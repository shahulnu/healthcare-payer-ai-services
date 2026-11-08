package com.ummarali.payerai.hcc.domain;

import java.util.List;

/**
 * The code's decision on one ICD-10 code. disposition is add, confirmed, superseded, review_deletion,
 * query_provider, no_hcc or no_action. Names match the TypeScript Finding.
 */
public record Finding(
    String code,
    String description,
    String hcc,
    String hccName,
    boolean submitted,
    String modelAssessment,
    String disposition,
    String codeNote,
    List<String> meatMet,
    List<ConditionEvidence> evidence,
    String rationale) {

  public Finding superseded(String higherHcc) {
    return new Finding(code, description, hcc, hccName, submitted, modelAssessment, "superseded",
        "HCC " + higherHcc + " is also supported and replaces HCC " + hcc + " in the hierarchy.", meatMet, evidence, rationale);
  }

  /** acceptableSource: the quote comes from an encounter that can support risk adjustment. */
  public record ConditionEvidence(String element, String docId, String quote, boolean verified, boolean acceptableSource) {}
}
