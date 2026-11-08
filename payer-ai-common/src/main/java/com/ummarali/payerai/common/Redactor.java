package com.ummarali.payerai.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based identifier redaction that runs before any text reaches the model. Same rules
 * as src/lib/shared/redact.ts. Demo-grade: production pairs it with an NLP de-identification
 * service and sends the model only the minimum necessary PHI.
 */
public final class Redactor {

  private record Rule(String label, Pattern pattern, String replacement) {}

  private static final List<Rule> RULES = List.of(
      new Rule("Name", Pattern.compile("(Patient:\\s*)([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)"), "$1[NAME]"),
      new Rule("Date of birth", Pattern.compile("(DOB:\\s*)\\d{1,2}/\\d{1,2}/\\d{4}"), "$1[DOB]"),
      new Rule("MRN", Pattern.compile("(MRN:\\s*)\\d{6,10}"), "$1[MRN]"),
      new Rule("Account number", Pattern.compile("(Account:\\s*)\\d{6,12}"), "$1[ACCOUNT]"),
      new Rule("Phone", Pattern.compile("\\(?\\b\\d{3}\\)?[\\s.-]\\d{3}[\\s.-]\\d{4}\\b"), "[PHONE]"),
      new Rule("SSN", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), "[SSN]"),
      new Rule("Email", Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.]+\\b"), "[EMAIL]"));

  /** Redacted text plus how many identifiers of each kind were removed. */
  public record Result(String text, Map<String, Integer> counts) {}

  private Redactor() {}

  public static Result redact(String text) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    String out = text;
    for (Rule rule : RULES) {
      Matcher m = rule.pattern().matcher(out);
      StringBuilder sb = new StringBuilder();
      int n = 0;
      while (m.find()) {
        m.appendReplacement(sb, rule.replacement());
        n++;
      }
      m.appendTail(sb);
      out = sb.toString();
      if (n > 0) counts.merge(rule.label(), n, Integer::sum);
    }
    return new Result(out, counts);
  }
}
