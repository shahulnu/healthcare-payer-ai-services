package com.ummarali.payerai.pi;

import com.ummarali.payerai.pi.domain.BriefOutput;
import com.ummarali.payerai.pi.domain.Fact;
import com.ummarali.payerai.pi.domain.PiBrief.CitationCheck;
import com.ummarali.payerai.pi.domain.PiBrief.LanguageCheck;
import com.ummarali.payerai.pi.domain.PiBrief.NumberCheck;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Code checks on the brief, a port of checkBrief() in src/lib/pi/engine.ts: figures must come from the
 * facts, language stays neutral, and citations resolve. A failed check holds the brief for an analyst to
 * edit. The lead itself stands on the statistics.
 */
final class BriefChecker {

  record Checked(String status, List<String> holdReasons, NumberCheck numberCheck, LanguageCheck languageCheck, CitationCheck citationCheck) {}

  /** Words that state or imply wrongdoing. */
  private static final Pattern ACCUSATORY = Pattern.compile(
      "\\b(fraud\\w*|abus\\w*|upcod\\w*|kickback\\w*|scheme\\w*|illegal\\w*|criminal\\w*|intentional\\w*|deliberate\\w*)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern PROVIDER_ID = Pattern.compile("PRV-SYN-\\d+");
  private static final Pattern NUMBERS = Pattern.compile("\\d+(?:,\\d{3})*(?:\\.\\d+)?");

  private BriefChecker() {}

  static List<String> numbersIn(String text) {
    List<String> out = new ArrayList<>();
    Matcher m = NUMBERS.matcher(PROVIDER_ID.matcher(text).replaceAll(" "));
    while (m.find()) out.add(m.group());
    return out;
  }

  /** Numeric value with scale removed, so "48" and "48.0" compare equal. */
  private static BigDecimal numberOf(String s) {
    return new BigDecimal(s.replace(",", "")).stripTrailingZeros();
  }

  static Checked check(List<Fact> facts, BriefOutput brief) {
    List<String> parts = new ArrayList<>(List.of(brief.headline(), brief.pattern()));
    parts.addAll(brief.alternativeExplanations());
    parts.addAll(brief.nextSteps());
    String text = String.join("\n", parts);

    Set<BigDecimal> allowed = facts.stream().flatMap(f -> numbersIn(f.text()).stream()).map(BriefChecker::numberOf).collect(Collectors.toSet());
    List<String> found = numbersIn(text);
    List<String> unsupported = found.stream().filter(n -> !allowed.contains(numberOf(n))).distinct().toList();

    Set<String> flaggedWords = new LinkedHashSet<>();
    Matcher words = ACCUSATORY.matcher(text);
    while (words.find()) flaggedWords.add(words.group().toLowerCase(Locale.ROOT));

    Set<String> ids = facts.stream().map(Fact::id).collect(Collectors.toSet());
    Set<String> flaggedIds = facts.stream().filter(Fact::flagged).map(Fact::id).collect(Collectors.toSet());
    List<String> unknown = brief.citedFactIds().stream().filter(id -> !ids.contains(id)).toList();
    boolean citesFlagged = brief.citedFactIds().stream().anyMatch(flaggedIds::contains);

    List<String> reasons = new ArrayList<>();
    if (!unsupported.isEmpty()) reasons.add("Figures not in the screen results: " + String.join(", ", unsupported) + ".");
    if (!flaggedWords.isEmpty()) reasons.add("Language that implies wrongdoing: " + String.join(", ", flaggedWords) + ".");
    if (!unknown.isEmpty()) reasons.add("Citations to facts that don't exist: " + String.join(", ", unknown) + ".");
    if (!citesFlagged) reasons.add("The brief doesn't cite any flagged pattern.");
    if (brief.alternativeExplanations().isEmpty()) reasons.add("The brief offers no alternative explanation.");

    return new Checked(reasons.isEmpty() ? "ready" : "held", reasons, new NumberCheck(found, unsupported),
        new LanguageCheck(List.copyOf(flaggedWords)), new CitationCheck(brief.citedFactIds(), unknown, citesFlagged));
  }
}
