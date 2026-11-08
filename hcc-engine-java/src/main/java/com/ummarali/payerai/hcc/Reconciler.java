package com.ummarali.payerai.hcc;

import com.ummarali.payerai.common.Citations;
import com.ummarali.payerai.hcc.domain.ChartDocument;
import com.ummarali.payerai.hcc.domain.ChartMember;
import com.ummarali.payerai.hcc.domain.Finding;
import com.ummarali.payerai.hcc.domain.Finding.ConditionEvidence;
import com.ummarali.payerai.hcc.domain.HccMap;
import com.ummarali.payerai.hcc.domain.HccMap.CodeEntry;
import com.ummarali.payerai.hcc.domain.ModelReview;
import com.ummarali.payerai.hcc.domain.ModelReview.EvidenceRef;
import com.ummarali.payerai.hcc.domain.ModelReview.ModelCondition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The deterministic layer, a port of reconcile() in src/lib/hcc/engine.ts. Code owns the HCC map, the
 * hierarchy and every disposition: a code counts as supported only with a verified MEAT quote from an
 * acceptable encounter.
 */
final class Reconciler {

  private static final Pattern PAST_LANGUAGE =
      Pattern.compile("\\b(history of|no evidence of disease|status post|resolved|completed)\\b", Pattern.CASE_INSENSITIVE);
  private static final Set<String> ELEMENTS = Set.of("M", "E", "A", "T");
  private static final Set<String> ASSESSMENTS = Set.of("supported", "not_supported", "query_provider");
  /** Deletions first, so they can't get lost below the additions. */
  private static final List<String> ORDER = List.of("review_deletion", "add", "query_provider", "confirmed", "superseded", "no_hcc", "no_action");

  private Reconciler() {}

  private static String hccName(HccMap map, CodeEntry entry) {
    return entry != null && entry.hcc() != null ? map.hccNames().get(entry.hcc()) : null;
  }

  private static String description(CodeEntry entry) {
    return entry != null ? entry.description() : "Not in the demo's code list";
  }

  static List<Finding> reconcile(ChartMember member, List<ChartDocument> docs, ModelReview review, HccMap map) {
    Map<String, ChartDocument> byId = docs.stream().collect(Collectors.toMap(ChartDocument::id, Function.identity()));
    Set<String> submitted = new HashSet<>(member.submitted());
    Set<String> seen = new HashSet<>();
    List<Finding> findings = new ArrayList<>();

    for (ModelCondition c : review.conditions() == null ? List.<ModelCondition>of() : review.conditions()) {
      if (!seen.add(c.icd10())) continue;
      CodeEntry entry = map.codes().get(c.icd10());
      // An assessment outside the allowed values is treated as needing a person.
      String assessment = ASSESSMENTS.contains(c.assessment()) ? c.assessment() : "query_provider";

      List<ConditionEvidence> evidence = new ArrayList<>();
      for (EvidenceRef e : c.evidence() == null ? List.<EvidenceRef>of() : c.evidence()) {
        if (!ELEMENTS.contains(e.element())) continue;
        ChartDocument doc = byId.get(e.docId());
        evidence.add(new ConditionEvidence(e.element(), e.docId(), e.quote(),
            Citations.quoteFound(e.quote(), doc == null ? null : doc.text()), doc != null && doc.acceptable()));
      }
      List<ConditionEvidence> usable = evidence.stream().filter(e -> e.verified() && e.acceptableSource()).toList();
      List<String> meatMet = usable.stream().map(ConditionEvidence::element).distinct().toList();

      boolean supported = assessment.equals("supported");
      String codeNote = null;
      if (entry == null) {
        supported = false;
        codeNote = "This code isn't in the demo's code list, so a coder should check it.";
      } else if (supported && usable.isEmpty()) {
        supported = false;
        codeNote = "The model said supported, but no verified quote from an acceptable encounter backs it.";
      } else if (supported && Boolean.TRUE.equals(entry.activeOnly()) && usable.stream().anyMatch(e -> PAST_LANGUAGE.matcher(e.quote()).find())) {
        supported = false;
        codeNote = "This code means active disease, but the supporting quote describes a past condition.";
      }

      boolean isSubmitted = submitted.contains(c.icd10());
      String disposition;
      if (entry == null) disposition = "query_provider";
      else if (entry.hcc() == null) disposition = "no_hcc";
      else if (supported) disposition = isSubmitted ? "confirmed" : "add";
      else if (assessment.equals("query_provider")) disposition = "query_provider";
      else disposition = isSubmitted ? "review_deletion" : "no_action";

      findings.add(new Finding(c.icd10(), description(entry), entry == null ? null : entry.hcc(), hccName(map, entry), isSubmitted,
          assessment, disposition, codeNote, meatMet, evidence, c.rationale()));
    }

    // A submitted code the model didn't assess still needs a person to look at it.
    for (String code : member.submitted()) {
      if (seen.contains(code)) continue;
      CodeEntry entry = map.codes().get(code);
      findings.add(new Finding(code, description(entry), entry == null ? null : entry.hcc(), hccName(map, entry), true, null,
          entry != null && entry.hcc() == null ? "no_hcc" : "query_provider",
          "The model didn't assess this submitted code, so a coder should review it.", List.of(), List.of(), "No assessment returned."));
    }

    // Hierarchy: a higher HCC replaces the lower ones in its family.
    Set<String> supportedHccs = new LinkedHashSet<>();
    for (Finding f : findings) {
      if (isSupported(f) && f.hcc() != null) supportedHccs.add(f.hcc());
    }
    List<Finding> resolved = new ArrayList<>();
    for (Finding f : findings) {
      String higher = !isSupported(f) || f.hcc() == null ? null
          : supportedHccs.stream().filter(h -> map.hierarchies().getOrDefault(h, List.of()).contains(f.hcc())).findFirst().orElse(null);
      resolved.add(higher == null ? f : f.superseded(higher));
    }

    // List.sort is stable, so ties keep the model's order, as in the TypeScript engine.
    resolved.sort(Comparator.comparingInt(f -> ORDER.indexOf(f.disposition())));
    return resolved;
  }

  private static boolean isSupported(Finding f) {
    return f.disposition().equals("add") || f.disposition().equals("confirmed");
  }
}
