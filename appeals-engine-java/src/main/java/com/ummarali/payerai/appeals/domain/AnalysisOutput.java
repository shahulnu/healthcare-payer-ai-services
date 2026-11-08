package com.ummarali.payerai.appeals.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Stage 1: what the model returns for each denied line. The SDK derives the JSON schema from
 * these records; field names match the TypeScript engine so recorded outputs are shared.
 */
public record AnalysisOutput(List<LineAssessment> lines, String summary) {

  public record LineAssessment(
      int line,
      @JsonPropertyDescription("One of: medical_necessity, authorization, coding, timely_filing, duplicate, eligibility, documentation, other")
      String category,
      String explanation,
      @JsonPropertyDescription("One of: appeal, corrected_claim, no_action") String action,
      @JsonProperty("addresses_denial_reason") boolean addressesDenialReason,
      List<QuoteRef> evidence,
      @JsonProperty("next_steps") String nextSteps) {}

  public record QuoteRef(@JsonProperty("doc_id") String docId, String quote) {}
}
