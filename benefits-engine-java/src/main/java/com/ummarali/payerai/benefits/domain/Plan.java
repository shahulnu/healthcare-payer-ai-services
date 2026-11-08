package com.ummarali.payerai.benefits.domain;

import java.util.List;
import java.util.Map;

/** A fictional health plan from data/benefits/plans.json: document sections plus cost-share rules. */
public record Plan(
    String id,
    String name,
    String type,
    String planYear,
    String sourceNote,
    List<PlanSection> sections,
    /** category -> network ("in" / "out") -> rule */
    Map<String, Map<String, CostRule>> rules,
    Limits limits) {

  public record PlanSection(String id, String doc, int page, String title, String text) {}

  /**
   * One cost-share rule. kind is copay, coinsurance, no_charge or not_covered; amount applies to
   * copays and rate to coinsurance. The flags mirror the TypeScript CostRule union.
   */
  public record CostRule(String kind, Double amount, Double rate, Boolean priorAuth, Boolean referral, Integer visitLimit) {

    public static final CostRule NOT_COVERED = new CostRule("not_covered", null, null, null, null, null);
  }

  /** Per-network amounts; out is null when the plan has no out-of-network benefit. */
  public record ByNetwork(Double in, Double out) {

    public Double get(String network) {
      return "out".equals(network) ? out : in;
    }
  }

  public record Limits(ByNetwork deductible, ByNetwork oopMax) {}
}
