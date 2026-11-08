package com.ummarali.payerai.pi.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ummarali.payerai.common.TokenUsage;
import com.ummarali.payerai.common.TraceStep;
import java.util.List;

/** The brief plus its checks. Same JSON as the TypeScript PiBrief, so the demo can use either engine. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PiBrief(
    String engine,
    String providerId,
    String mode,
    String replaySource,
    String model,
    String status,
    List<String> holdReasons,
    String tier,
    String action,
    BriefOutput brief,
    List<Fact> facts,
    NumberCheck numberCheck,
    LanguageCheck languageCheck,
    CitationCheck citationCheck,
    List<TraceStep> trace,
    TokenUsage usage,
    String generatedAt,
    BriefOutput raw,
    String notice) {

  public PiBrief withNotice(String n) {
    return new PiBrief(engine, providerId, mode, replaySource, model, status, holdReasons, tier, action, brief, facts,
        numberCheck, languageCheck, citationCheck, trace, usage, generatedAt, raw, n);
  }

  public record NumberCheck(List<String> found, List<String> unsupported) {}

  public record LanguageCheck(List<String> flagged) {}

  public record CitationCheck(List<String> cited, List<String> unknown, boolean citesFlagged) {}
}
