package com.ummarali.payerai.benefits.domain;

import java.util.List;
import java.util.Map;

/** A synthetic member with deductible and out-of-pocket totals, plus the demo's sample questions. */
public record Member(
    String id, String label, String planId, String accumulatorsAsOf, Accumulators accumulators, List<SampleQuestion> sampleQuestions) {

  public record Accumulators(Map<String, Double> deductibleMet, Map<String, Double> oopMet) {}

  public record SampleQuestion(String id, String text) {}
}
