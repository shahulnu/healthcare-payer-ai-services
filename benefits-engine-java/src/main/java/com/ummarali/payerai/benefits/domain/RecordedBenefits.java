package com.ummarali.payerai.benefits.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** data/benefits/recorded.json: the tool calls and final submission of each recorded sample question. */
public record RecordedBenefits(String source, String note, String model, Map<String, RecordedRun> cases) {

  public record RecordedRun(List<RecordedToolCall> toolCalls, Submission submission) {}

  public record RecordedToolCall(String name, JsonNode input) {}
}
