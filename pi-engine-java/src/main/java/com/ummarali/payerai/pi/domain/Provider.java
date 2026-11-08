package com.ummarali.payerai.pi.domain;

/** A synthetic provider's aggregates from data/pi/providers.json. Fields a specialty doesn't use are null. */
public record Provider(
    String id,
    String specialty,
    int encounters,
    Integer patients,
    Double patientRiskScore,
    Double level5Share,
    Double level4Share,
    Double modifier25Rate,
    Double allowedPerPatient,
    Double unitsPerVisit,
    Double fourPlusUnitShare,
    Double visitsPerPatient,
    Double modifier59Rate,
    double peakDayHours) {}
