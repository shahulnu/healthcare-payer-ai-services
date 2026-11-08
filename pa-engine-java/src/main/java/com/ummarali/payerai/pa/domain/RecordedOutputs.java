package com.ummarali.payerai.pa.domain;

import java.util.Map;

/** data/pa/recorded.json: model outputs that replay mode plays back. */
public record RecordedOutputs(String source, String note, String model, Map<String, ModelOutput> cases) {}
