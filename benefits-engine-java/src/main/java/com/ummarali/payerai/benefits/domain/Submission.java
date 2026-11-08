package com.ummarali.payerai.benefits.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** What the model passed to the submit_answer tool. Field names match the tool schema. */
public record Submission(
    String answer,
    List<CitationRef> citations,
    @JsonProperty("needs_human") boolean needsHuman,
    @JsonProperty("escalation_reason") String escalationReason) {

  public record CitationRef(@JsonProperty("section_id") String sectionId, String quote) {}
}
