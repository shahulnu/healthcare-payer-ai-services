package com.ummarali.payerai.hcc.domain;

import java.util.Map;

/** data/hcc/recorded.json: the chart reviews replay mode plays back. */
public record RecordedHcc(String source, String note, String model, Map<String, ModelReview> cases) {}
