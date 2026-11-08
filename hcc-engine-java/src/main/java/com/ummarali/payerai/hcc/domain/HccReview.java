package com.ummarali.payerai.hcc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ummarali.payerai.common.RedactionCount;
import com.ummarali.payerai.common.TokenUsage;
import com.ummarali.payerai.common.TraceStep;
import java.util.List;
import java.util.Map;

/** The engine's result. Same JSON as the TypeScript HccReview, so the demo can use either engine. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HccReview(
    String engine,
    String memberId,
    String mode,
    String replaySource,
    String model,
    String hccModel,
    String summary,
    List<Finding> findings,
    Map<String, Integer> counts,
    List<RedactionCount> redactions,
    List<TraceStep> trace,
    TokenUsage usage,
    String generatedAt,
    ModelReview raw,
    String notice) {

  public HccReview withNotice(String n) {
    return new HccReview(engine, memberId, mode, replaySource, model, hccModel, summary, findings, counts, redactions, trace, usage, generatedAt, raw, n);
  }
}
