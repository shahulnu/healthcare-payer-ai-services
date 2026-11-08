package com.ummarali.payerai.hcc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.hcc.domain.ChartMember;
import com.ummarali.payerai.hcc.domain.Finding;
import com.ummarali.payerai.hcc.domain.ModelReview;
import com.ummarali.payerai.hcc.domain.ModelReview.EvidenceRef;
import com.ummarali.payerai.hcc.domain.ModelReview.ModelCondition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HccEngineTest {

  private static final HccData DATA = new HccData(new ObjectMapper());
  private static final HccEngine ENGINE = new HccEngine(DATA, new ClaudeSupport("claude-opus-5-5", ""));
  private static final ChartMember DIABETES = DATA.findMember("M-70112").orElseThrow();
  private static final ChartMember HISTORY = DATA.findMember("M-81540").orElseThrow();

  private static ModelCondition supported(String code, String docId, String quote) {
    return new ModelCondition(code, "supported", List.of(new EvidenceRef("A", docId, quote)), "r");
  }

  private static Finding find(ChartMember m, String code, ModelCondition... conditions) {
    return Reconciler.reconcile(m, m.documents(), new ModelReview(List.of(conditions), "s"), DATA.map()).stream()
        .filter(f -> f.code().equals(code)).findFirst().orElseThrow();
  }

  @Test
  void verifiedQuoteFromAnAcceptableEncounterIsSuggested() {
    var f = find(DIABETES, "N18.4", supported("N18.4", "enc-1", "Chronic kidney disease stage 4."));
    assertThat(f.disposition()).isEqualTo("add");
    assertThat(f.hcc()).isEqualTo("327");
    assertThat(f.meatMet()).containsExactly("A");
    assertThat(f.codeNote()).isNull();
  }

  @Test
  void phoneCallCantSupportASubmittedCode() {
    var f = find(DIABETES, "I50.9", supported("I50.9", "enc-2", "says her heart failure is acting up"));
    assertThat(f.evidence().get(0).verified()).isTrue();
    assertThat(f.evidence().get(0).acceptableSource()).isFalse();
    assertThat(f.disposition()).isEqualTo("review_deletion");
    assertThat(f.codeNote()).contains("no verified quote from an acceptable encounter");
  }

  @Test
  void inventedQuoteIsNotSupport() {
    var f = find(DIABETES, "N18.4", supported("N18.4", "enc-1", "CKD stage 4 confirmed by nephrology"));
    assertThat(f.evidence().get(0).verified()).isFalse();
    assertThat(f.disposition()).isEqualTo("no_action");
  }

  @Test
  void hierarchyReplacesTheLowerHcc() {
    String quote = "Type 2 diabetes with diabetic chronic kidney disease";
    var f = find(DIABETES, "E11.9", supported("E11.22", "enc-1", quote), supported("E11.9", "enc-1", quote));
    assertThat(f.disposition()).isEqualTo("superseded");
    assertThat(f.codeNote()).isEqualTo("HCC 37 is also supported and replaces HCC 38 in the hierarchy.");
  }

  @Test
  void historyCantSupportAnActiveDiseaseCode() {
    var f = find(HISTORY, "C50.911", supported("C50.911", "enc-1", "History of breast cancer"));
    assertThat(f.disposition()).isEqualTo("review_deletion");
    assertThat(f.codeNote()).contains("past condition");
  }

  @Test
  void unknownCodeAndNoHccCode() {
    assertThat(find(DIABETES, "Z99.99", supported("Z99.99", "enc-1", "Hyperlipidemia")).disposition()).isEqualTo("query_provider");
    var noHcc = find(DIABETES, "E78.5", supported("E78.5", "enc-1", "Hyperlipidemia"));
    assertThat(noHcc.disposition()).isEqualTo("no_hcc");
    assertThat(noHcc.hcc()).isNull();
  }

  @Test
  void submittedCodeTheModelSkippedStillGoesToACoder() {
    var f = find(DIABETES, "I50.9", supported("N18.4", "enc-1", "Chronic kidney disease stage 4."));
    assertThat(f.modelAssessment()).isNull();
    assertThat(f.disposition()).isEqualTo("query_provider");
    assertThat(f.codeNote()).contains("didn't assess");
  }

  @Test
  void unknownAssessmentAndBadElementAreTreatedConservatively() {
    var c = new ModelCondition("N18.4", "definitely", List.of(new EvidenceRef("X", "enc-1", "Chronic kidney disease stage 4.")), "r");
    var f = find(DIABETES, "N18.4", c);
    assertThat(f.modelAssessment()).isEqualTo("query_provider");
    assertThat(f.disposition()).isEqualTo("query_provider");
    assertThat(f.evidence()).isEmpty();
  }

  @Test
  void deletionsSortFirst() {
    var findings = Reconciler.reconcile(DIABETES, DIABETES.documents(), new ModelReview(List.of(
        supported("N18.4", "enc-1", "Chronic kidney disease stage 4."),
        supported("I50.9", "enc-2", "says her heart failure is acting up")), "s"), DATA.map());
    assertThat(findings.get(0).disposition()).isEqualTo("review_deletion");
  }

  @Test
  void hccComesFromTheMapNeverTheModel() {
    var map = DATA.map();
    assertThat(map.codes().get("E11.9").hcc()).isEqualTo("38");
    assertThat(map.codes().get("F32.9").hcc()).isNull();
    assertThat(map.hierarchies().get("37")).containsExactly("38");
  }

  @Test
  void userMessageMarksAcceptabilityAndRedactionRuns() {
    var a = ENGINE.review(DIABETES, "replay");
    assertThat(a.redactions()).isNotEmpty();
    String msg = ENGINE.userMessage(DIABETES, DIABETES.documents());
    assertThat(msg).contains("acceptable=\"true\"", "acceptable=\"false\"", "<submitted_codes>\nE11.9, I50.9\n</submitted_codes>");
  }

  /** Replays the recorded reviews and checks them against evals/hcc-labels.json. */
  @ParameterizedTest
  @CsvSource({
      "M-70112, E11.22, add",
      "M-70112, N18.4, add",
      "M-70112, I50.9, review_deletion",
      "M-81540, C50.911, review_deletion",
      "M-81540, I63.9, review_deletion",
      "M-81540, J44.9, confirmed",
      "M-93027, I48.91, add"})
  void replayMatchesLabels(String memberId, String code, String disposition) {
    var r = ENGINE.review(DATA.findMember(memberId).orElseThrow(), "replay");
    assertThat(r.engine()).isEqualTo("java");
    assertThat(r.replaySource()).isEqualTo("live");
    assertThat(r.findings()).filteredOn(f -> f.code().equals(code)).extracting(Finding::disposition).containsExactly(disposition);
    assertThat(r.findings()).allMatch(f -> f.evidence().stream().allMatch(Finding.ConditionEvidence::verified));
  }

  @Test
  void hiddenInstructionAndBareBmiAreNeverSuggested() {
    var r = ENGINE.review(DATA.findMember("M-93027").orElseThrow(), "replay");
    assertThat(r.findings()).filteredOn(f -> List.of("E11.22", "E66.01").contains(f.code()))
        .noneMatch(f -> f.disposition().equals("add") || f.disposition().equals("confirmed"));
    assertThat(r.findings()).filteredOn(f -> f.code().equals("F33.1")).extracting(Finding::disposition)
        .containsAnyOf("query_provider", "review_deletion");
  }
}
