package com.ummarali.payerai.pi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.pi.domain.BriefOutput;
import com.ummarali.payerai.pi.domain.Fact;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PiEngineTest {

  private static final PiData DATA = new PiData(new ObjectMapper());
  private static final PiEngine ENGINE = new PiEngine(DATA, new ClaudeSupport("claude-opus-5-5", ""));

  @Test
  void medianAndRobustZ() {
    assertThat(PeerScreen.median(List.of(3.0, 1.0, 2.0))).isEqualTo(2.0);
    assertThat(PeerScreen.median(List.of(4.0, 1.0, 2.0, 3.0))).isEqualTo(2.5);
    assertThat(PeerScreen.robustZ(5, 3, 1)).isCloseTo(1.349, Offset.offset(1e-9));
    assertThat(PeerScreen.robustZ(5, 3, 0)).isEqualTo(0);
  }

  @Test
  void theilSenIgnoresAnOutlier() {
    var line = PeerScreen.theilSen(List.of(1.0, 2.0, 3.0, 4.0, 5.0), List.of(2.0, 4.0, 6.0, 8.0, 100.0));
    assertThat(line.slope()).isEqualTo(2.0);
    assertThat(line.intercept()).isEqualTo(0.0);
  }

  @Test
  void roundingMatchesJavaScriptToFixed() {
    // (1.005).toFixed(2) is "1.00" in JavaScript because the binary value is just under 1.005.
    assertThat(PeerScreen.fixed(1.005, 2)).isEqualTo("1.00");
    assertThat(PeerScreen.fixed(0.125 * 100, 1)).isEqualTo("12.5");
    assertThat(PeerScreen.format(1234.4, PeerScreen.Format.USD)).isEqualTo("$1,234");
  }

  /** Scores, tiers and exposure as computed by the TypeScript engine on the same cohort. */
  @ParameterizedTest
  @CsvSource({
      "PRV-SYN-2009, high, 79, 243100",
      "PRV-SYN-1031, high, 55, 40800",
      "PRV-SYN-1017, high, 55, 36300",
      "PRV-SYN-1058, high, 55, 0",
      "PRV-SYN-2027, high, 52, 169200",
      "PRV-SYN-1042, medium, 38, 3800"})
  void screenMatchesTypeScript(String id, String tier, long score, long exposure) {
    var lead = DATA.findLead(id).orElseThrow();
    assertThat(lead.tier()).isEqualTo(tier);
    assertThat(lead.score()).isEqualTo(score);
    assertThat(lead.exposure()).isEqualTo(exposure);
  }

  @Test
  void screenFindsOnlyInjectedPatternsAndClearsTheDecoy() {
    var s = DATA.screen();
    assertThat(s.cohortSize()).isEqualTo(120);
    assertThat(s.leads()).extracting(l -> l.providerId())
        .containsExactly("PRV-SYN-2009", "PRV-SYN-1031", "PRV-SYN-1017", "PRV-SYN-1058", "PRV-SYN-2027", "PRV-SYN-1042");
    assertThat(s.cleared()).extracting(l -> l.providerId()).containsExactly("PRV-SYN-1066");
    var decoy = s.cleared().get(0).features().get(0);
    assertThat(decoy.rawZ()).isGreaterThanOrEqualTo(3.5);
    assertThat(decoy.z()).isLessThan(3.5);
  }

  @Test
  void impossibleDayIsAHardRule() {
    var f = DATA.findLead("PRV-SYN-1058").orElseThrow().features().stream().filter(x -> x.id().equals("peak_day_hours")).findFirst().orElseThrow();
    assertThat(f.hardRule()).isTrue();
    assertThat(f.flagged()).isTrue();
    assertThat(f.display()).isEqualTo("19.5 hours");
  }

  private static BriefOutput brief(String pattern, List<String> cited) {
    return new BriefOutput("Headline", pattern, List.of("A busier clinic could explain it."), List.of("Request the sample."), cited);
  }

  private static final List<Fact> FACTS = List.of(
      new Fact("F-A", "Visits billed at 99215: 48.0% (peer median 5.5%). Flagged by the screen.", true),
      new Fact("F-B", "Visits in the period: 1,558.", false));

  @Test
  void cleanBriefIsReadyAndNumbersCompareByValue() {
    var c = BriefChecker.check(FACTS, brief("99215 is 48% across 1,558 visits for PRV-SYN-1017.", List.of("F-A", "F-B")));
    assertThat(c.status()).isEqualTo("ready");
    assertThat(c.numberCheck().unsupported()).isEmpty();
  }

  @Test
  void inventedFigureHoldsTheBrief() {
    var c = BriefChecker.check(FACTS, brief("Sessions may run 60 to 75 minutes.", List.of("F-A")));
    assertThat(c.status()).isEqualTo("held");
    assertThat(c.numberCheck().unsupported()).containsExactly("60", "75");
  }

  @Test
  void accusatoryLanguageHoldsTheBrief() {
    var c = BriefChecker.check(FACTS, brief("This looks like Upcoding and possibly fraud.", List.of("F-A")));
    assertThat(c.status()).isEqualTo("held");
    assertThat(c.languageCheck().flagged()).containsExactly("upcoding", "fraud");
  }

  @Test
  void citationsMustResolveAndIncludeAFlaggedPattern() {
    var c = BriefChecker.check(FACTS, brief("A plain pattern.", List.of("F-B", "F-NOPE")));
    assertThat(c.status()).isEqualTo("held");
    assertThat(c.citationCheck().unknown()).containsExactly("F-NOPE");
    assertThat(c.citationCheck().citesFlagged()).isFalse();
  }

  @Test
  void factsMatchTheTypeScriptWording() {
    var facts = ENGINE.buildFacts(DATA.findLead("PRV-SYN-1017").orElseThrow());
    assertThat(facts).extracting(Fact::id).startsWith("F-COHORT", "F-VOLUME", "F-RISK", "F-level5_share");
    assertThat(facts.get(0).text()).isEqualTo("Review period 2026 Q3. Peer group: 80 Family Medicine providers.");
    assertThat(facts.get(3).text()).isEqualTo(
        "Established visits billed at 99215: 48.0% (peer median 5.5%, expected for this panel's risk 5.6%, robust z-score 42.8). Flagged by the screen.");
    assertThat(facts).extracting(Fact::text).contains("Estimated billing above the peer pattern: $36,300. This sizes the lead and is not an overpayment finding.");
  }

  /** Replays each recorded brief and checks it against evals/pi-labels.json. */
  @ParameterizedTest
  @CsvSource({
      "PRV-SYN-2009, units_per_visit",
      "PRV-SYN-1031, modifier25_rate",
      "PRV-SYN-1017, level5_share",
      "PRV-SYN-1058, peak_day_hours",
      "PRV-SYN-2027, visits_per_patient",
      "PRV-SYN-1042, level5_share"})
  void replayPassesChecksAndCitesTheInjectedPattern(String id, String pattern) {
    var b = ENGINE.brief(DATA.findLead(id).orElseThrow(), "replay");
    assertThat(b.engine()).isEqualTo("java");
    assertThat(b.status()).isEqualTo("ready");
    assertThat(b.holdReasons()).isEmpty();
    assertThat(b.citationCheck().cited()).contains("F-" + pattern);
    assertThat(b.brief().alternativeExplanations()).isNotEmpty();
  }
}
