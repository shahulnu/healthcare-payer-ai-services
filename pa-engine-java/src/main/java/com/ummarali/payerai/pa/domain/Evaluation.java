package com.ummarali.payerai.pa.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ummarali.payerai.common.RedactionCount;
import com.ummarali.payerai.common.TokenUsage;
import com.ummarali.payerai.common.TraceStep;
import java.util.List;

/**
 * The engine's result. Field names and values match the TypeScript engine's JSON so the
 * Next.js demo can use either engine unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Evaluation(
    String engine,
    String caseId,
    String policyId,
    String policyTitle,
    String mode,
    String model,
    String route,
    String routeReason,
    String summary,
    List<CriterionResult> criteria,
    List<RedactionCount> redactions,
    List<TraceStep> trace,
    TokenUsage usage,
    String decisionDueBy,
    String generatedAt,
    ModelOutput raw,
    String notice) {

  public Evaluation withNotice(String n) {
    return new Evaluation(engine, caseId, policyId, policyTitle, mode, model, route, routeReason, summary,
        criteria, redactions, trace, usage, decisionDueBy, generatedAt, raw, n);
  }

  public record CriterionResult(
      String id,
      String text,
      String modelStatus,
      String status,
      List<Evidence> evidence,
      String rationale,
      boolean downgraded) {}

  public record Evidence(String docId, String quote, boolean verified) {}

}
