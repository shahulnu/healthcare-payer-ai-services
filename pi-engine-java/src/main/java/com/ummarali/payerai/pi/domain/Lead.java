package com.ummarali.payerai.pi.domain;

import java.util.List;

/** A screened provider. exposure sizes the lead and is not an overpayment finding. */
public record Lead(
    String providerId,
    String specialty,
    int encounters,
    String tier,
    long score,
    List<FeatureResult> features,
    List<String> flaggedLabels,
    long exposure,
    boolean clearedByAdjustment) {}
