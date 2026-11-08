package com.ummarali.payerai.appeals.domain;

import java.util.Map;

/** data/appeals/recorded.json: the stage 1 and stage 2 outputs that replay mode plays back. */
public record RecordedAppeals(String source, String note, String model, Map<String, RecordedCase> cases) {

  public record RecordedCase(AnalysisOutput analysis, String letter) {}
}
