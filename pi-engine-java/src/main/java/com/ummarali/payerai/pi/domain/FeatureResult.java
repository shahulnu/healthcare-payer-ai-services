package com.ummarali.payerai.pi.domain;

/**
 * One measure for one provider. z is used for flagging, risk-adjusted where the feature is
 * adjusted; rawZ is against peers before any adjustment. Names match the TypeScript FeatureResult.
 */
public record FeatureResult(
    String id,
    String label,
    double value,
    String display,
    double peerMedian,
    String peerDisplay,
    Double expected,
    String expectedDisplay,
    double z,
    double rawZ,
    boolean flagged,
    boolean hardRule) {}
