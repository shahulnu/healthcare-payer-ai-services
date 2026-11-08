package com.ummarali.payerai.common;

/** Tokens used by one or more model calls, with the estimated cost at list price. */
public record TokenUsage(long inputTokens, long outputTokens, double estCostUsd) {

  public TokenUsage plus(TokenUsage other) {
    if (other == null) return this;
    return new TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens, estCostUsd + other.estCostUsd);
  }
}
