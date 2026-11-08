package com.ummarali.payerai.pa.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * What the model returns. The SDK derives the structured-output JSON schema from these
 * records. Field names match the TypeScript engine's schema so recorded outputs are shared.
 */
public record ModelOutput(List<CriterionAssessment> criteria, String summary) {

  public record CriterionAssessment(
      String id,
      @JsonPropertyDescription("One of: met, not_met, insufficient") String status,
      List<QuoteRef> evidence,
      String rationale) {}

  public record QuoteRef(@JsonProperty("doc_id") String docId, String quote) {}
}
