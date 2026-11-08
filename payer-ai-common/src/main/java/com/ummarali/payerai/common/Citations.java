package com.ummarali.payerai.common;

import java.util.Locale;

/** Verbatim quote checks, matching quoteFound() in src/lib/shared/llm.ts. */
public final class Citations {

  private Citations() {}

  public static String normalize(String s) {
    return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
  }

  /** True when the quote appears word for word (ignoring case and whitespace) in the source. */
  public static boolean quoteFound(String quote, String source) {
    if (quote == null || source == null) return false;
    String q = normalize(quote);
    return !q.isEmpty() && normalize(source).contains(q);
  }
}
