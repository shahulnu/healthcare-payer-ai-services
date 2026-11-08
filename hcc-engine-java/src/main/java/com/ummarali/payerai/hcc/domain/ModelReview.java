package com.ummarali.payerai.hcc.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * What the model returns. The SDK derives the JSON schema from these records. Field names match the
 * TypeScript engine's schema, so recorded outputs are shared.
 */
public record ModelReview(List<ModelCondition> conditions, String summary) {

  public record ModelCondition(
      String icd10,
      @JsonPropertyDescription("One of: supported, not_supported, query_provider") String assessment,
      List<EvidenceRef> evidence,
      String rationale) {}

  public record EvidenceRef(
      @JsonPropertyDescription("One of: M, E, A, T") String element,
      @JsonProperty("doc_id") String docId,
      String quote) {}
}
