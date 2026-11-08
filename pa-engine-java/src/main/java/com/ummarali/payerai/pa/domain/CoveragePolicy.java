package com.ummarali.payerai.pa.domain;

import java.util.List;

/** An illustrative coverage policy from data/pa/policies.json. All criteria are required. */
public record CoveragePolicy(
    String id, String title, List<String> serviceCodes, String benefit, List<Criterion> criteria, String sourceNote) {

  public record Criterion(String id, String text, String guidance) {}
}
