package com.ummarali.payerai.benefits;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ummarali.payerai.benefits.domain.BenefitsAnswer.ToolCall;
import com.ummarali.payerai.benefits.domain.Submission;
import com.ummarali.payerai.benefits.domain.Submission.CitationRef;
import com.ummarali.payerai.common.ClaudeSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BenefitsEngineTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final BenefitsData DATA = new BenefitsData(MAPPER);

  /** Same scenarios checked against the TypeScript calculator. */
  @ParameterizedTest
  @CsvSource({
      "M-20431, mri_knee, in, 586.0, 400.0, 186.0, 0.0, true",
      "M-20431, pcp_visit, in, 30.0, 0.0, 0.0, 30.0, true",
      "M-20431, pt_visit, out, 170.0, 170.0, 0.0, 0.0, true",
      "M-20431, acupuncture, in, 95.0, 0.0, 0.0, 0.0, false",
      "M-20431, er_visit, out, 400.0, 0.0, 0.0, 400.0, true",
      "M-58817, specialist_visit, in, 84.0, 0.0, 84.0, 0.0, true",
      "M-58817, mri_knee, in, 408.0, 0.0, 408.0, 0.0, true",
      "M-58817, er_visit, in, 580.0, 0.0, 580.0, 0.0, true",
      "M-58817, specialist_visit, out, 230.0, 0.0, 0.0, 0.0, false"})
  void calculatorMatchesTypeScript(String memberId, String serviceId, String network, double pays, double ded, double coins,
      double copay, boolean covered) {
    var member = DATA.findMember(memberId).orElseThrow();
    var plan = DATA.findPlan(member.planId()).orElseThrow();
    var e = CostCalculator.estimate(plan, member, DATA.findService(serviceId).orElseThrow(), network);
    assertThat(e.memberPays()).isEqualTo(pays);
    assertThat(e.deductiblePortion()).isEqualTo(ded);
    assertThat(e.coinsurancePortion()).isEqualTo(coins);
    assertThat(e.copay()).isEqualTo(copay);
    assertThat(e.covered()).isEqualTo(covered);
  }

  @Test
  void calculatorNotesRequirementsAndBalanceBilling() {
    var ppo = DATA.findMember("M-20431").orElseThrow();
    var plan = DATA.findPlan(ppo.planId()).orElseThrow();
    assertThat(CostCalculator.estimate(plan, ppo, DATA.findService("mri_knee").orElseThrow(), "in").notes())
        .containsExactly("Prior authorization is required.");
    assertThat(CostCalculator.estimate(plan, ppo, DATA.findService("pt_visit").orElseThrow(), "out").notes())
        .anyMatch(n -> n.contains("balance bill"));
  }

  private static List<ToolCall> mriCalls(String memberId) {
    var member = DATA.findMember(memberId).orElseThrow();
    var plan = DATA.findPlan(member.planId()).orElseThrow();
    var sections = JsonNodeFactory.instance.objectNode();
    sections.putArray("section_ids").add("SBC-3");
    var cost = JsonNodeFactory.instance.objectNode().put("service_id", "mri_knee").put("network", "in");
    return List.of(
        new ToolCall("get_plan_sections", sections, PlanTools.run(DATA, plan, member, "get_plan_sections", sections)),
        new ToolCall("estimate_cost", cost, PlanTools.run(DATA, plan, member, "estimate_cost", cost)));
  }

  private static AnswerChecker.Checked check(String answer, String quote, boolean needsHuman) {
    var plan = DATA.findPlan("PLAN-SILVER-PPO").orElseThrow();
    var sub = new Submission(answer, List.of(new CitationRef("SBC-3", quote)), needsHuman, needsHuman ? "Claim status" : "");
    return AnswerChecker.check(plan, sub, mriCalls("M-20431"));
  }

  @Test
  void answerWithCalculatorFiguresAndVerifiedQuoteIsShown() {
    var c = check("You'd pay about $586 of the $1,020 allowed amount.", "Prior authorization is required for CT, PET and MRI scans.", false);
    assertThat(c.status()).isEqualTo("answered");
    assertThat(c.numberCheck().found()).containsExactly("$586", "$1,020");
    assertThat(c.numberCheck().unsupported()).isEmpty();
  }

  @Test
  void dollarAmountsIgnoreTrailingPunctuation() {
    assertThat(AnswerChecker.dollarAmounts("about $1,100, then $586. Or $1,020.50 and $30"))
        .containsExactly("$1,100", "$586", "$1,020.50", "$30");
  }

  @Test
  void figureWithNoSourceIsHeldForAnAgent() {
    var c = check("Good news, the MRI costs $0.50 today.", "Prior authorization is required for CT, PET and MRI scans.", false);
    assertThat(c.status()).isEqualTo("escalated");
    assertThat(c.numberCheck().unsupported()).containsExactly("$0.50");
    assertThat(c.escalationReason()).contains("$0.50");
  }

  @Test
  void unverifiedCitationsAndModelRequestsEscalate() {
    assertThat(check("It is covered.", "MRIs are always covered", false).status()).isEqualTo("escalated");
    assertThat(check("An agent can check your claim.", "anything", true).escalationReason()).isEqualTo("Claim status");
    assertThat(AnswerChecker.check(DATA.findPlan("PLAN-SILVER-PPO").orElseThrow(), null, List.of()).status()).isEqualTo("escalated");
  }

  @Test
  void toolSchemasAreStrictAndOffersKnownServicesOnly() {
    var agent = new BenefitsAgent(new ClaudeSupport("claude-opus-5-5", ""), DATA, MAPPER);
    var tools = agent.tools();
    assertThat(tools).extracting(t -> t.name()).containsExactly("get_plan_sections", "estimate_cost", "submit_answer");
    assertThat(tools).allMatch(t -> t.strict().orElse(false));
  }

  /** Replays the recorded runs and checks routing and cost against evals/benefits-labels.json. */
  @ParameterizedTest
  @CsvSource({
      "Q-PPO-MRI, answered, mri_knee, in, 586",
      "Q-PPO-PCP, answered, pcp_visit, in, 30",
      "Q-PPO-ACU, answered, , , ",
      "Q-PPO-OON-PT, answered, pt_visit, out, 170",
      "Q-PPO-CLAIM, escalated, , , ",
      "Q-PPO-INJECT, answered, mri_knee, in, 586",
      "Q-HMO-DERM, answered, specialist_visit, in, 84",
      "Q-HMO-MRI, answered, mri_knee, in, 408",
      "Q-HMO-OON, answered, , , "})
  void replayMatchesLabels(String questionId, String status, String service, String network, Integer amount) {
    var engine = new BenefitsEngine(DATA, new BenefitsAgent(new ClaudeSupport("claude-opus-5-5", ""), DATA, MAPPER));
    var sample = DATA.findSampleQuestion(questionId).orElseThrow();
    var a = engine.answer(sample.member(), sample.question().text(), questionId, "replay");

    assertThat(a.engine()).isEqualTo("java");
    assertThat(a.status()).isEqualTo(status);
    assertThat(a.citations()).allMatch(c -> c.verified());
    assertThat(a.numberCheck().unsupported()).isEmpty();
    if (amount != null) {
      var est = a.estimates().stream().filter(e -> e.serviceId().equals(service) && e.network().equals(network)).findFirst();
      assertThat(est).isPresent();
      assertThat(est.get().memberPays()).isEqualTo(amount.doubleValue());
      assertThat(a.answer()).contains("$" + String.format("%,d", amount));
    }
  }
}
