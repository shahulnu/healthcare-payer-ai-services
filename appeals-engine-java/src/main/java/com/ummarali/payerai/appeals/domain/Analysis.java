package com.ummarali.payerai.appeals.domain;

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
public record Analysis(
    String engine,
    String claimId,
    String mode,
    String replaySource,
    String model,
    String summary,
    List<LineFinding> findings,
    double recoverable,
    String correctedClaim,
    Letter letter,
    List<RedactionCount> redactions,
    List<TraceStep> trace,
    TokenUsage usage,
    String generatedAt,
    RecordedAppeals.RecordedCase raw,
    String notice) {

  public Analysis withNotice(String n) {
    return new Analysis(engine, claimId, mode, replaySource, model, summary, findings, recoverable, correctedClaim,
        letter, redactions, trace, usage, generatedAt, raw, n);
  }

  public record LineFinding(
      int line,
      String code,
      double billed,
      List<String> denialCodes,
      String explanation,
      String category,
      String modelAction,
      String action,
      String strength,
      List<LineEvidence> evidence,
      String nextSteps,
      boolean downgraded) {}

  /** fromRecord: the quote is from the provider's own records rather than the payer policy. */
  public record LineEvidence(String docId, String quote, boolean verified, boolean fromRecord) {}

  public record Letter(String text, int quotes, int quotesVerified) {}
}
