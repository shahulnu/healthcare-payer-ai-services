package com.ummarali.payerai.pa.domain;

import java.util.List;

/** A synthetic prior authorization request, as stored in data/pa/cases.json. */
public record PaCase(
    String id,
    String receivedAt,
    String urgency,
    Member member,
    Provider provider,
    Service service,
    List<CaseDocument> documents,
    String teaser) {

  public record Member(String id, int age, String sex, String plan) {}

  public record Provider(String name, String specialty, String network) {}

  public record Service(String code, String codeSystem, String description, List<String> diagnosis) {}
}
