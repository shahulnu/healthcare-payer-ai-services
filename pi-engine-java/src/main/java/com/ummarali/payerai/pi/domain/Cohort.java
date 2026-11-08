package com.ummarali.payerai.pi.domain;

import java.util.List;

/** The contents of data/pi/providers.json. */
public record Cohort(String period, String note, List<Provider> providers) {}
