package com.ummarali.payerai.pi.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One peer's value on one feature, for the demo's charts. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PeerPoint(String id, double value, Double risk) {}
