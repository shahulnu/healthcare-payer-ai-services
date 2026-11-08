package com.ummarali.payerai.benefits;

import com.ummarali.payerai.benefits.domain.BenefitsAnswer.Citation;
import com.ummarali.payerai.benefits.domain.BenefitsAnswer.NumberCheck;
import com.ummarali.payerai.benefits.domain.BenefitsAnswer.ToolCall;
import com.ummarali.payerai.benefits.domain.CostEstimate;
import com.ummarali.payerai.benefits.domain.Plan;
import com.ummarali.payerai.benefits.domain.Submission;
import com.ummarali.payerai.common.Citations;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code checks on the model's answer, a port of checkAnswer() in src/lib/benefits/engine.ts.
 * Citations must be verbatim from the plan, and every dollar amount must come from a section the
 * model read or a calculator result. Anything else goes to a person.
 */
final class AnswerChecker {

  record Checked(String status, String answer, String escalationReason, List<Citation> citations, List<CostEstimate> estimates,
      NumberCheck numberCheck) {}

  // "$1,020" or "$586.00", without swallowing a trailing comma or period.
  private static final Pattern DOLLARS = Pattern.compile("\\$\\d+(?:,\\d{3})*(?:\\.\\d{2})?");

  private AnswerChecker() {}

  static long toCents(String dollars) {
    return Math.round(Double.parseDouble(dollars.replaceAll("[$,]", "")) * 100);
  }

  static List<String> dollarAmounts(String text) {
    List<String> out = new ArrayList<>();
    Matcher m = DOLLARS.matcher(text);
    while (m.find()) out.add(m.group());
    return out;
  }

  static Checked check(Plan plan, Submission submission, List<ToolCall> toolCalls) {
    List<CostEstimate> estimates = toolCalls.stream()
        .filter(t -> t.name().equals("estimate_cost") && t.output() instanceof CostEstimate)
        .map(t -> (CostEstimate) t.output())
        .toList();

    if (submission == null) {
      return new Checked("escalated", "Let me connect you with a member services agent who can help with this.",
          "The assistant didn't produce a checkable answer.", List.of(), estimates, new NumberCheck(List.of(), List.of()));
    }

    List<Citation> citations = (submission.citations() == null ? List.<Submission.CitationRef>of() : submission.citations()).stream().map(c -> {
      Optional<Plan.PlanSection> s = plan.sections().stream().filter(x -> x.id().equals(c.sectionId())).findFirst();
      return new Citation(c.sectionId(), s.map(Plan.PlanSection::doc).orElse("?"), s.map(Plan.PlanSection::page).orElse(null),
          s.map(Plan.PlanSection::title).orElse("Unknown section"), c.quote(), Citations.quoteFound(c.quote(), s.map(Plan.PlanSection::text).orElse(null)));
    }).toList();

    // Amounts the answer may use: anything in a section the model read, or any figure from an estimate.
    Set<String> readSections = new HashSet<>();
    for (ToolCall t : toolCalls) {
      if (!t.name().equals("get_plan_sections") || !(t.output() instanceof List<?> list)) continue;
      for (Object o : list) {
        if (o instanceof Map<?, ?> m && m.get("text") != null) readSections.add(String.valueOf(m.get("id")));
      }
    }
    Set<Long> allowedCents = new HashSet<>();
    plan.sections().stream().filter(s -> readSections.contains(s.id()))
        .forEach(s -> dollarAmounts(s.text()).forEach(d -> allowedCents.add(toCents(d))));
    for (CostEstimate e : estimates) {
      for (double n : new double[] {e.allowedAmount(), e.deductiblePortion(), e.coinsurancePortion(), e.copay(), e.memberPays(), e.planPays()}) {
        allowedCents.add(Math.round(n * 100));
      }
      e.notes().forEach(n -> dollarAmounts(n).forEach(d -> allowedCents.add(toCents(d))));
    }
    String answer = submission.answer() == null ? "" : submission.answer();
    List<String> found = dollarAmounts(answer);
    List<String> unsupported = found.stream().filter(d -> !allowedCents.contains(toCents(d))).toList();

    String status = "answered";
    String reason = null;
    if (submission.needsHuman()) {
      status = "escalated";
      reason = submission.escalationReason() == null || submission.escalationReason().isBlank()
          ? "The assistant asked for a person to handle this." : submission.escalationReason();
    } else if (!unsupported.isEmpty()) {
      status = "escalated";
      reason = "The answer included " + String.join(", ", unsupported) + ", which doesn't come from the plan documents or the cost calculator.";
    } else if (citations.stream().noneMatch(Citation::verified)) {
      status = "escalated";
      reason = "None of the answer's citations could be matched to the plan documents.";
    }
    return new Checked(status, answer, reason, citations, estimates, new NumberCheck(found, unsupported));
  }
}
