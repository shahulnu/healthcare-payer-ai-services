package com.ummarali.payerai.pa.domain;

/** A clinical note, claims history or report attached to a request. */
public record CaseDocument(String id, String kind, String title, String date, String text) {

  public CaseDocument withText(String newText) {
    return new CaseDocument(id, kind, title, date, newText);
  }
}
