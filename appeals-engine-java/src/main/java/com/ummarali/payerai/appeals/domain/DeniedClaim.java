package com.ummarali.payerai.appeals.domain;

import java.util.List;

/** A synthetic denied claim with its remittance lines and records, from data/appeals/claims.json. */
public record DeniedClaim(
    String id,
    String payerClaimId,
    String dateOfService,
    String remitDate,
    Member member,
    Provider provider,
    List<String> diagnosis,
    List<RemitLine> lines,
    List<ClaimDocument> documents,
    List<String> policyIds,
    String teaser) {

  public record Member(String id, int age, String sex, String plan) {}

  public record Provider(String name, String specialty) {}

  /** One service line as reported on the remittance advice (X12 835). */
  public record RemitLine(
      int line,
      String code,
      List<String> modifiers,
      int units,
      String description,
      double billed,
      double paid,
      List<Adjustment> adjustments,
      List<String> remarks) {

    public boolean denied() {
      return paid == 0;
    }

    /** Denial codes as shown to people, e.g. CO-50 and M15. CARC 45 (fee-schedule write-off) is not a denial. */
    public List<String> denialCodes() {
      var codes = new java.util.ArrayList<String>();
      for (Adjustment a : adjustments) if (!"45".equals(a.carc())) codes.add(a.group() + "-" + a.carc());
      codes.addAll(remarks);
      return codes;
    }
  }

  /** X12 claim adjustment: group (CO, PR, OA), reason code and amount. */
  public record Adjustment(String group, String carc, double amount) {}
}
