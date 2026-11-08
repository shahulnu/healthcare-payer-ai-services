package com.ummarali.payerai.pi.domain;

import java.util.List;
import java.util.Map;

/** The result of screening the whole cohort. Same JSON as the TypeScript Screen. */
public record Screen(
    String period,
    int cohortSize,
    List<Lead> leads,
    List<Lead> cleared,
    /* specialty -> feature id -> peer values */
    Map<String, Map<String, List<PeerPoint>>> peers,
    Line level5Line) {

  /** Risk adjustment line for the 99215 share: expected = intercept + slope x risk. */
  public record Line(double slope, double intercept) {}
}
