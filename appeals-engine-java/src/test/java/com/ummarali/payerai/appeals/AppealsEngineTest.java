package com.ummarali.payerai.appeals;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.appeals.domain.AnalysisOutput;
import com.ummarali.payerai.appeals.domain.AnalysisOutput.LineAssessment;
import com.ummarali.payerai.appeals.domain.AnalysisOutput.QuoteRef;
import com.ummarali.payerai.appeals.domain.ClaimDocument;
import com.ummarali.payerai.appeals.domain.DeniedClaim;
import com.ummarali.payerai.appeals.domain.DeniedClaim.Adjustment;
import com.ummarali.payerai.appeals.domain.DeniedClaim.RemitLine;
import com.ummarali.payerai.appeals.domain.PaymentPolicy;
import com.ummarali.payerai.common.ClaudeSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppealsEngineTest {

  private static final List<ClaimDocument> DOCS = List.of(new ClaimDocument("note-1", "progress_note", "Note", "2026-09-18",
      "Oswestry Disability Index 26% (42% at evaluation). Therapeutic exercise 40 minutes."));
  private static final List<PaymentPolicy> POLICIES = List.of(new PaymentPolicy("POL-1", "Policy", "Time-based codes require the treatment minutes to be documented."));

  private static DeniedClaim claim() {
    var paid = new RemitLine(1, "97164", List.of(), 1, "Re-eval", 118, 96.4, List.of(new Adjustment("CO", "45", 21.6)), List.of());
    var denied = new RemitLine(2, "97110", List.of(), 3, "Exercise", 141, 0, List.of(new Adjustment("CO", "50", 141)), List.of());
    return new DeniedClaim("CLM-1", "P-1", "2026-09-18", "2026-10-09", new DeniedClaim.Member("M-1", 58, "F", "PPO"),
        new DeniedClaim.Provider("Clinic", "PT"), List.of("M54.50"), List.of(paid, denied), DOCS, List.of("POL-1"), "t");
  }

  private static AnalysisOutput out(String action, boolean addresses, List<QuoteRef> quotes) {
    return new AnalysisOutput(List.of(new LineAssessment(2, "medical_necessity", "why", action, addresses, quotes, "next")), "s");
  }

  @Test
  void onlyDeniedLinesAreAnalyzedAndWriteOffIsNotADenialCode() {
    var f = AppealsEngine.finalizeFindings(claim(), DOCS, POLICIES, out("appeal", true, List.of(new QuoteRef("note-1", "Therapeutic exercise 40 minutes"))));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).line()).isEqualTo(2);
    assertThat(f.get(0).denialCodes()).containsExactly("CO-50");
  }

  @Test
  void appealStrengthComesFromVerifiedRecordQuotes() {
    var two = List.of(new QuoteRef("note-1", "Oswestry Disability Index 26%"), new QuoteRef("note-1", "Therapeutic exercise 40 minutes"));
    assertThat(AppealsEngine.finalizeFindings(claim(), DOCS, POLICIES, out("appeal", true, two)).get(0).strength()).isEqualTo("strong");

    var one = List.of(new QuoteRef("note-1", "Therapeutic exercise 40 minutes"), new QuoteRef("POL-1", "treatment minutes to be documented"));
    var f = AppealsEngine.finalizeFindings(claim(), DOCS, POLICIES, out("appeal", true, one)).get(0);
    assertThat(f.strength()).isEqualTo("moderate");
    assertThat(f.evidence()).extracting("fromRecord").containsExactly(true, false);
    assertThat(f.evidence()).allMatch(e -> e.verified());
  }

  @Test
  void appealWithOnlyPolicyOrParaphrasedQuotesIsDowngraded() {
    var quotes = List.of(new QuoteRef("POL-1", "treatment minutes to be documented"), new QuoteRef("note-1", "40 mins of exercise"));
    var f = AppealsEngine.finalizeFindings(claim(), DOCS, POLICIES, out("appeal", true, quotes)).get(0);
    assertThat(f.modelAction()).isEqualTo("appeal");
    assertThat(f.action()).isEqualTo("no_action");
    assertThat(f.strength()).isEqualTo("weak");
    assertThat(f.downgraded()).isTrue();
  }

  @Test
  void correctedClaimNeedsTheModelToSayTheEvidenceAnswersTheDenial() {
    var q = List.of(new QuoteRef("note-1", "Therapeutic exercise 40 minutes"));
    assertThat(AppealsEngine.finalizeFindings(claim(), DOCS, POLICIES, out("corrected_claim", true, q)).get(0).action()).isEqualTo("corrected_claim");
    var f = AppealsEngine.finalizeFindings(claim(), DOCS, POLICIES, out("corrected_claim", false, q)).get(0);
    assertThat(f.action()).isEqualTo("no_action");
    assertThat(f.downgraded()).isTrue();
  }

  @Test
  void unknownActionOrMissingLineIsNotPursued() {
    var f = AppealsEngine.finalizeFindings(claim(), DOCS, POLICIES, out("escalate", true, List.of())).get(0);
    assertThat(f.action()).isEqualTo("no_action");
    var missing = AppealsEngine.finalizeFindings(claim(), DOCS, POLICIES, new AnalysisOutput(List.of(), "s")).get(0);
    assertThat(missing.downgraded()).isTrue();
    assertThat(missing.nextSteps()).isEqualTo("Review this line manually.");
  }

  @Test
  void letterQuotesAreCheckedAgainstRecordsAndPolicy() {
    var letter = AppealsEngine.checkLetter("""
        The note documents "Therapeutic exercise 40 minutes." and policy says “Time-based codes require the treatment minutes to be documented.”
        It also claims "the patient fully recovered by week two" which is not in the record.""", DOCS, POLICIES);
    assertThat(letter.quotes()).isEqualTo(3);
    assertThat(letter.quotesVerified()).isEqualTo(2);
  }

  /** Replays the recorded outputs and checks every line against evals/appeals-labels.json. */
  @ParameterizedTest
  @CsvSource({
      "CLM-88310427, 2:appeal:medical_necessity 3:no_action:documentation, 141.0, true, false",
      "CLM-88412290, 2:corrected_claim:coding, 168.0, false, true",
      "CLM-87990314, 1:appeal:timely_filing 2:appeal:timely_filing, 263.0, true, false"})
  void replayMatchesLabels(String claimId, String expected, double recoverable, boolean hasLetter, boolean hasCorrected) {
    var data = new AppealsData(new ObjectMapper());
    var engine = new AppealsEngine(data, new ClaudeSupport("claude-opus-5-5", ""));
    var a = engine.analyze(data.findClaim(claimId).orElseThrow(), "replay");

    var actual = String.join(" ", a.findings().stream().map(f -> f.line() + ":" + f.action() + ":" + f.category()).toList());
    assertThat(actual).isEqualTo(expected);
    assertThat(a.engine()).isEqualTo("java");
    assertThat(a.replaySource()).isEqualTo("live");
    assertThat(a.recoverable()).isEqualTo(recoverable);
    assertThat(a.letter() != null).isEqualTo(hasLetter);
    assertThat(a.correctedClaim() != null).isEqualTo(hasCorrected);
    assertThat(a.findings()).allMatch(f -> f.evidence().stream().allMatch(e -> e.verified()));
    if (a.letter() != null) assertThat(a.letter().quotesVerified()).isEqualTo(a.letter().quotes());
    assertThat(a.redactions()).isNotEmpty();
  }
}
