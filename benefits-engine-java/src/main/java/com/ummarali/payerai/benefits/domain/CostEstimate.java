package com.ummarali.payerai.benefits.domain;

import java.util.List;

/** The calculator's result. Field names match the TypeScript CostEstimate. */
public record CostEstimate(
    String serviceId,
    String serviceName,
    String network,
    boolean covered,
    double allowedAmount,
    double deductiblePortion,
    double coinsurancePortion,
    double copay,
    double memberPays,
    double planPays,
    List<String> notes) {}
