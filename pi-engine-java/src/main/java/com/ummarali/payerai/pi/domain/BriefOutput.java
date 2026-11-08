package com.ummarali.payerai.pi.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * What the model writes. The SDK derives the JSON schema from this record. Field names match the
 * TypeScript engine's schema, so recorded outputs are shared.
 */
public record BriefOutput(
    String headline,
    String pattern,
    @JsonProperty("alternative_explanations") List<String> alternativeExplanations,
    @JsonProperty("next_steps") List<String> nextSteps,
    @JsonProperty("cited_fact_ids") List<String> citedFactIds) {}
