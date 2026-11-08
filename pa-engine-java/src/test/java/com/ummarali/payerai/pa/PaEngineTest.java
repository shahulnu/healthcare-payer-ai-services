package com.ummarali.payerai.pa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.pa.domain.CaseDocument;
import com.ummarali.payerai.pa.domain.CoveragePolicy;
import com.ummarali.payerai.pa.domain.CoveragePolicy.Criterion;
import com.ummarali.payerai.pa.domain.ModelOutput;
import com.ummarali.payerai.pa.domain.ModelOutput.CriterionAssessment;
import com.ummarali.payerai.pa.domain.ModelOutput.QuoteRef;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaEngineTest {

  private static final CoveragePolicy POLICY = new CoveragePolicy("P-1", "Test policy", List.of("72148"), "medical",
      List.of(new Criterion("C1", "First", "g"), new Criterion("C2", "Second", "g")), "test");

  private static final List<CaseDocument> DOCS = List.of(
      new CaseDocument("note-1", "clinical_note", "Note", "2026-10-02", "Positive straight-leg raise on the left at 40 degrees."));

  private static ModelOutput output(String s1, String q1, String s2, String q2) {
    return new ModelOutput(List.of(
        new CriterionAssessment("C1", s1, q1 == null ? List.of() : List.of(new QuoteRef("note-1", q1)), "r1"),
        new CriterionAssessment("C2", s2, q2 == null ? List.of() : List.of(new QuoteRef("note-1", q2)), "r2")),
        "summary");
  }

  @Test
  void allMetWithVerifiedQuotesRecommendsApproval() {
    var r = PaEngine.finalizeResult(POLICY, DOCS, output("met", "straight-leg raise", "met", "40 DEGREES"));
    assertThat(r.route()).isEqualTo("recommend_approve");
    assertThat(r.criteria()).allMatch(c -> !c.downgraded());
  }

  @Test
  void metWithAParaphrasedQuoteIsDowngraded() {
    var r = PaEngine.finalizeResult(POLICY, DOCS, output("met", "straight-leg raise", "met", "SLR was positive"));
    var c2 = r.criteria().get(1);
    assertThat(c2.modelStatus()).isEqualTo("met");
    assertThat(c2.status()).isEqualTo("insufficient");
    assertThat(c2.downgraded()).isTrue();
    assertThat(r.route()).isEqualTo("request_info");
  }

  @Test
  void anyNotMetGoesToNurseReviewNeverDenial() {
    var r = PaEngine.finalizeResult(POLICY, DOCS, output("met", "straight-leg raise", "not_met", "left at 40 degrees"));
    assertThat(r.route()).isEqualTo("nurse_review");
    assertThat(r.routeReason()).contains("C2 not met");
  }

  @Test
  void missingCriterionAndUnknownStatusAreInsufficient() {
    var out = new ModelOutput(List.of(new CriterionAssessment("C1", "probably", List.of(), "r")), "s");
    var r = PaEngine.finalizeResult(POLICY, DOCS, out);
    assertThat(r.criteria()).extracting("status").containsExactly("insufficient", "insufficient");
    assertThat(r.criteria().get(1).downgraded()).isTrue();
    assertThat(r.route()).isEqualTo("request_info");
  }

  /** Replays the recorded outputs and checks routes against evals/labels.json. */
  @ParameterizedTest
  @CsvSource({
      "PA-2026-01487, recommend_approve, 2026-11-03T14:05:00.000Z",
      "PA-2026-01502, nurse_review, 2026-11-04T09:40:00.000Z",
      "PA-2026-01519, request_info, 2026-10-31T16:20:00.000Z"})
  void replayMatchesLabels(String caseId, String expectedRoute, String expectedDue) {
    var data = new PaData(new ObjectMapper());
    var engine = new PaEngine(data, new ClaudeEvaluator(new ClaudeSupport("claude-opus-5-5", "")));
    var e = engine.evaluate(data.findCase(caseId).orElseThrow(), "replay");

    assertThat(e.engine()).isEqualTo("java");
    assertThat(e.route()).isEqualTo(expectedRoute);
    assertThat(e.decisionDueBy()).isEqualTo(expectedDue);
    assertThat(e.criteria()).allMatch(c -> c.evidence().stream().allMatch(ev -> ev.verified()));
    assertThat(e.redactions()).isNotEmpty();
    assertThat(e.trace()).extracting("name").containsExactly(
        "Policy lookup", "Identifier redaction", "Criteria evaluation (replay)", "Citation check + routing");
  }

  @Test
  void dataLoadsAllCasesAndPolicies() {
    var data = new PaData(new ObjectMapper());
    assertThat(data.cases()).hasSize(3);
    for (var c : data.cases()) assertThat(data.findPolicy(c.service().code())).isPresent();
    assertThat(data.recorded().cases()).containsKeys("PA-2026-01487", "PA-2026-01502", "PA-2026-01519");
  }
}
