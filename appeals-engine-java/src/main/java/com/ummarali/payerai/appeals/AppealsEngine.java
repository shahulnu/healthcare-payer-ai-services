package com.ummarali.payerai.appeals;

import com.ummarali.payerai.appeals.domain.Analysis;
import com.ummarali.payerai.appeals.domain.Analysis.Letter;
import com.ummarali.payerai.appeals.domain.Analysis.LineEvidence;
import com.ummarali.payerai.appeals.domain.Analysis.LineFinding;
import com.ummarali.payerai.appeals.domain.AnalysisOutput;
import com.ummarali.payerai.appeals.domain.AnalysisOutput.LineAssessment;
import com.ummarali.payerai.appeals.domain.ClaimDocument;
import com.ummarali.payerai.appeals.domain.DeniedClaim;
import com.ummarali.payerai.appeals.domain.LetterOutput;
import com.ummarali.payerai.appeals.domain.PaymentPolicy;
import com.ummarali.payerai.appeals.domain.RecordedAppeals.RecordedCase;
import com.ummarali.payerai.common.Citations;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.common.RedactionCount;
import com.ummarali.payerai.common.Redactor;
import com.ummarali.payerai.common.ReplayMissingException;
import com.ummarali.payerai.common.TokenUsage;
import com.ummarali.payerai.common.Trace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Java port of src/lib/appeals/engine.ts. Code parses the remittance, redacts the records and
 * decides what to pursue; Claude explains each denial (stage 1) and drafts the appeal letter
 * (stage 2) only from evidence that code has verified.
 */
@Service
public class AppealsEngine {

  public static final String ENGINE = "java";

  private static final Set<String> CATEGORIES = Set.of(
      "medical_necessity", "authorization", "coding", "timely_filing", "duplicate", "eligibility", "documentation", "other");
  private static final Set<String> ACTIONS = Set.of("appeal", "corrected_claim", "no_action");
  private static final Pattern LETTER_QUOTE = Pattern.compile("[\"“]([^\"”]{12,})[\"”]");

  private final AppealsData data;
  private final ClaudeSupport claude;

  public AppealsEngine(AppealsData data, ClaudeSupport claude) {
    this.data = data;
    this.claude = claude;
  }

  /** Deterministic checks: verify quotes, then decide whether each denied line is worth pursuing. */
  static List<LineFinding> finalizeFindings(DeniedClaim c, List<ClaimDocument> docs, List<PaymentPolicy> policies, AnalysisOutput output) {
    Map<String, String> recordText = docs.stream().collect(Collectors.toMap(ClaimDocument::id, ClaimDocument::text));
    Map<String, String> policyText = policies.stream().collect(Collectors.toMap(PaymentPolicy::id, PaymentPolicy::text));
    List<LineAssessment> assessed = output.lines() == null ? List.of() : output.lines();

    return c.lines().stream().filter(DeniedClaim.RemitLine::denied).map(l -> {
      LineAssessment m = assessed.stream().filter(x -> x.line() == l.line()).findFirst().orElse(null);
      if (m == null) {
        return new LineFinding(l.line(), l.code(), l.billed(), l.denialCodes(), "The model did not analyze this line.", "other",
            "no_action", "no_action", null, List.of(), "Review this line manually.", true);
      }
      List<LineEvidence> evidence = (m.evidence() == null ? List.<AnalysisOutput.QuoteRef>of() : m.evidence()).stream().map(e -> {
        boolean fromRecord = recordText.containsKey(e.docId());
        String source = fromRecord ? recordText.get(e.docId()) : policyText.get(e.docId());
        return new LineEvidence(e.docId(), e.quote(), Citations.quoteFound(e.quote(), source), fromRecord);
      }).toList();
      long recordQuotes = evidence.stream().filter(e -> e.verified() && e.fromRecord()).count();

      // Unknown values from the model are treated conservatively.
      String modelAction = ACTIONS.contains(m.action()) ? m.action() : "no_action";
      String category = CATEGORIES.contains(m.category()) ? m.category() : "other";

      // Pursue a line only when verified quotes from the provider's own records answer the denial.
      boolean supported = m.addressesDenialReason() && recordQuotes > 0;
      String action = modelAction;
      String strength = null;
      if (modelAction.equals("appeal")) {
        strength = !supported ? "weak" : recordQuotes >= 2 ? "strong" : "moderate";
        if (!supported) action = "no_action";
      } else if (modelAction.equals("corrected_claim")) {
        strength = supported ? "strong" : "weak";
        if (!supported) action = "no_action";
      }
      return new LineFinding(l.line(), l.code(), l.billed(), l.denialCodes(), m.explanation(), category, modelAction, action,
          strength, evidence, m.nextSteps(), !action.equals(modelAction));
    }).toList();
  }

  /** Counts quoted passages in the letter and checks each against the records and policies. */
  static Letter checkLetter(String letter, List<ClaimDocument> docs, List<PaymentPolicy> policies) {
    List<String> sources = new ArrayList<>();
    docs.forEach(d -> sources.add(d.text()));
    policies.forEach(p -> sources.add(p.text()));
    int quotes = 0;
    int verified = 0;
    Matcher m = LETTER_QUOTE.matcher(letter);
    while (m.find()) {
      String q = m.group(1).replaceAll("[.,;:]$", "");
      quotes++;
      if (sources.stream().anyMatch(s -> Citations.quoteFound(q, s))) verified++;
    }
    return new Letter(letter, quotes, verified);
  }

  public Analysis analyze(DeniedClaim c, String mode) {
    Trace trace = new Trace();
    var recorded = data.recorded();
    String replayLabel = "live".equals(recorded.source()) ? "recorded live output" : "sample output written for the demo";

    List<PaymentPolicy> policies = trace.time("Remittance + policy lookup", () -> data.findPolicies(c.policyIds()),
        p -> "%d of %d lines denied; %s".formatted(c.lines().stream().filter(DeniedClaim.RemitLine::denied).count(), c.lines().size(),
            p.stream().map(PaymentPolicy::id).collect(Collectors.joining(", "))));

    Map<String, Integer> counts = new LinkedHashMap<>();
    List<ClaimDocument> docs = trace.time("Identifier redaction", () -> c.documents().stream().map(d -> {
      Redactor.Result r = Redactor.redact(d.text());
      r.counts().forEach((k, n) -> counts.merge(k, n, Integer::sum));
      return d.withText(r.text());
    }).toList(), r -> "%d identifiers removed from %d documents".formatted(
        counts.values().stream().mapToInt(Integer::intValue).sum(), c.documents().size()));

    RecordedCase rec = recorded.cases() == null ? null : recorded.cases().get(c.id());
    if (!"live".equals(mode) && rec == null) {
      throw new ReplayMissingException("No recorded run for this claim yet. Switch to Live, or run npm run record:appeals.");
    }
    String block = AppealsPrompts.claimBlock(c, docs, policies, data.codes());

    AnalysisOutput output;
    String model;
    TokenUsage usage = null;
    if ("live".equals(mode)) {
      ClaudeSupport.Structured<AnalysisOutput> r = trace.time("Denial analysis (LLM)",
          () -> claude.structured(AppealsPrompts.ANALYSIS_PROMPT, AppealsPrompts.analysisMessage(block), AnalysisOutput.class, 16000L,
              "The model declined this claim. Work it manually."),
          res -> "%s, %d in / %d out tokens".formatted(res.model(), res.usage().inputTokens(), res.usage().outputTokens()));
      output = r.output();
      model = r.model();
      usage = r.usage();
    } else {
      output = trace.time("Denial analysis (replay)", rec::analysis, x -> "Loaded " + replayLabel);
      model = recorded.model();
    }

    List<LineFinding> findings = trace.time("Evidence check + recommendation", () -> finalizeFindings(c, docs, policies, output), f -> {
      long total = f.stream().mapToLong(x -> x.evidence().size()).sum();
      long verified = f.stream().flatMap(x -> x.evidence().stream()).filter(LineEvidence::verified).count();
      long downgraded = f.stream().filter(LineFinding::downgraded).count();
      return "%d of %d quotes found verbatim; %d lines downgraded".formatted(verified, total, downgraded);
    });

    List<LineFinding> appealed = findings.stream().filter(f -> f.action().equals("appeal")).toList();
    String letterText = null;
    if (!appealed.isEmpty()) {
      if ("live".equals(mode)) {
        ClaudeSupport.Structured<LetterOutput> r = trace.time("Appeal letter (LLM)",
            () -> claude.structured(AppealsPrompts.LETTER_PROMPT, AppealsPrompts.letterMessage(block, appealed), LetterOutput.class, 8000L,
                "The model declined to draft this letter. Write it manually."),
            res -> "%d in / %d out tokens".formatted(res.usage().inputTokens(), res.usage().outputTokens()));
        letterText = r.output().letter();
        usage = usage == null ? r.usage() : usage.plus(r.usage());
      } else {
        letterText = trace.time("Appeal letter (replay)", rec::letter, x -> "Loaded " + replayLabel);
      }
    }
    final String finalLetterText = letterText;
    Letter letter = finalLetterText == null ? null : trace.time("Letter quote check", () -> checkLetter(finalLetterText, docs, policies),
        r -> "%d of %d quoted passages found in the records or policy".formatted(r.quotesVerified(), r.quotes()));

    List<LineFinding> corrected = findings.stream().filter(f -> f.action().equals("corrected_claim")).toList();
    String correctedClaim = corrected.isEmpty() ? null
        : "Submit a replacement claim (claim frequency code 7) that references payer claim " + c.payerClaimId() + ".\n"
            + corrected.stream().map(f -> "Line %d (%s): %s".formatted(f.line(), f.code(), f.nextSteps())).collect(Collectors.joining("\n"));

    double recoverable = findings.stream().filter(f -> !f.action().equals("no_action")).mapToDouble(LineFinding::billed).sum();
    List<RedactionCount> redactions = counts.entrySet().stream().map(e -> new RedactionCount(e.getKey(), e.getValue())).toList();
    String replaySource = "live".equals(mode) ? null : ("live".equals(recorded.source()) ? "live" : "sample");

    return new Analysis(ENGINE, c.id(), mode, replaySource, model, output.summary(), findings, recoverable, correctedClaim, letter,
        redactions, trace.steps(), usage, Instant.now().toString(), new RecordedCase(output, letterText), null);
  }
}
