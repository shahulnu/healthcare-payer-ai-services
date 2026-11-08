package com.ummarali.payerai.appeals.domain;

/** A provider record attached to a denied claim. */
public record ClaimDocument(String id, String kind, String title, String date, String text) {

  public ClaimDocument withText(String newText) {
    return new ClaimDocument(id, kind, title, date, newText);
  }
}
