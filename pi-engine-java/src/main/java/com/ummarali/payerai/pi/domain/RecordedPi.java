package com.ummarali.payerai.pi.domain;

import java.util.Map;

/** data/pi/recorded.json: the briefs replay mode plays back. */
public record RecordedPi(String source, String note, String model, Map<String, BriefOutput> cases) {}
