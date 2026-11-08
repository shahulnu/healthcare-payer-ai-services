package com.ummarali.payerai.benefits.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.ummarali.payerai.common.TokenUsage;
import com.ummarali.payerai.common.TraceStep;
import java.util.List;

/**
 * The engine's result. Field names and values match the TypeScript BenefitsAnswer so the Next.js
 * demo can use either engine unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenefitsAnswer(
    String engine,
    String memberId,
    String planName,
    String questionId,
    String question,
    String mode,
    String replaySource,
    String model,
    String status,
    String answer,
    String escalationReason,
    List<Citation> citations,
    List<CostEstimate> estimates,
    NumberCheck numberCheck,
    List<ToolCall> toolCalls,
    List<TraceStep> trace,
    TokenUsage usage,
    String generatedAt,
    RecordedBenefits.RecordedRun raw,
    String notice) {

  public BenefitsAnswer withNotice(String n) {
    return new BenefitsAnswer(engine, memberId, planName, questionId, question, mode, replaySource, model, status, answer,
        escalationReason, citations, estimates, numberCheck, toolCalls, trace, usage, generatedAt, raw, n);
  }

  public record Citation(String sectionId, String doc, Integer page, String title, String quote, boolean verified) {}

  /** Dollar amounts in the answer, and any that don't come from a section read or a cost estimate. */
  public record NumberCheck(List<String> found, List<String> unsupported) {}

  public record ToolCall(String name, JsonNode input, Object output) {}
}
