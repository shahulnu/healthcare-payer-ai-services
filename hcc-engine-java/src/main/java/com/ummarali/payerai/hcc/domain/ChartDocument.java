package com.ummarali.payerai.hcc.domain;

/** One encounter. acceptable: face-to-face with a provider type that can support risk adjustment. */
public record ChartDocument(String id, String title, String date, String encounterType, String provider, boolean acceptable, String text) {

  public ChartDocument withText(String newText) {
    return new ChartDocument(id, title, date, encounterType, provider, acceptable, newText);
  }
}
