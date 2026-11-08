package com.ummarali.payerai.pa;

import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.common.Citations;
import com.ummarali.payerai.common.RedactionCount;
import com.ummarali.payerai.common.Redactor;
import com.ummarali.payerai.common.ReplayMissingException;
import com.ummarali.payerai.common.TokenUsage;
import com.ummarali.payerai.common.Trace;
import com.ummarali.payerai.pa.domain.CaseDocument;
import com.ummarali.payerai.pa.domain.CoveragePolicy;
import com.ummarali.payerai.pa.domain.Evaluation;
import com.ummarali.payerai.pa.domain.Evaluation.CriterionResult;
import com.ummarali.payerai.pa.domain.Evaluation.Evidence;
import com.ummarali.payerai.pa.domain.ModelOutput;
import com.ummarali.payerai.pa.domain.ModelOutput.CriterionAssessment;
import com.ummarali.payerai.pa.domain.PaCase;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Java port of src/lib/pa/engine.ts. Same fixed workflow: policy lookup, redaction, criteria
 * review (live Claude call or recorded replay), citation check, routing. Only step 3 involves
 * the model; every decision about what happens to the case is made here in code.
 */
@Service
public class PaEngine {

  public static final String ENGINE = "java";

  private static final Set<String> STATUSES = Set.of("met", "not_met", "insufficient");

  /** Always three fractional digits, the same text as JavaScript's toISOString. */
  private static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private final PaData data;
  private final ClaudeEvaluator claude;

  public PaEngine(PaData data, ClaudeEvaluator claude) {
    this.data = data;
    this.claude = claude;
  }

  record Routing(List<CriterionResult> criteria, String route, String routeReason) {}

  /** Deterministic checks the model cannot skip: verbatim citations, then routing. */
  static Routing finalizeResult(CoveragePolicy policy, List<CaseDocument> docs, ModelOutput output) {
    Map<String, String> docText = docs.stream().collect(Collectors.toMap(CaseDocument::id, CaseDocument::text));

    List<CriterionResult> criteria = policy.criteria().stream().map(cr -> {
      CriterionAssessment m = output.criteria() == null ? null
          : output.criteria().stream().filter(x -> cr.id().equals(x.id())).findFirst().orElse(null);
      if (m == null) {
        return new CriterionResult(cr.id(), cr.text(), "insufficient", "insufficient", List.of(),
            "The model did not return an assessment for this criterion.", true);
      }
      // Anything outside the three allowed values is treated as "insufficient".
      String modelStatus = STATUSES.contains(m.status()) ? m.status() : "insufficient";
      List<Evidence> evidence = (m.evidence() == null ? List.<ModelOutput.QuoteRef>of() : m.evidence()).stream()
          .map(e -> new Evidence(e.docId(), e.quote(), Citations.quoteFound(e.quote(), docText.get(e.docId()))))
          .toList();
      // A decision backed only by unverifiable quotes is not trusted.
      boolean decided = modelStatus.equals("met") || modelStatus.equals("not_met");
      boolean downgraded = decided && evidence.stream().noneMatch(Evidence::verified);
      String status = downgraded ? "insufficient" : modelStatus;
      return new CriterionResult(cr.id(), cr.text(), modelStatus, status, evidence, m.rationale(), downgraded);
    }).toList();

    // The copilot never recommends a denial: anything short of "all met" goes to a person.
    List<String> notMet = criteria.stream().filter(x -> x.status().equals("not_met")).map(CriterionResult::id).toList();
    List<String> missing = criteria.stream().filter(x -> x.status().equals("insufficient")).map(CriterionResult::id).toList();
    if (!notMet.isEmpty()) {
      return new Routing(criteria, "nurse_review", String.join(", ", notMet)
          + " not met. A nurse reviewer decides; only a physician can issue an adverse determination.");
    }
    if (!missing.isEmpty()) {
      return new Routing(criteria, "request_info", String.join(", ", missing)
          + " lack supporting documentation. Ask the provider for records before review.");
    }
    return new Routing(criteria, "recommend_approve",
        "Every required criterion is met with a verified citation. Eligible for approval after reviewer sign-off.");
  }

  /** Due time under the CMS-0057-F timeframes: 72 hours expedited, 7 calendar days standard. */
  static String decisionDueBy(PaCase c) {
    Duration window = "expedited".equals(c.urgency()) ? Duration.ofHours(72) : Duration.ofDays(7);
    return ISO_MILLIS.format(Instant.parse(c.receivedAt()).plus(window));
  }

  public Evaluation evaluate(PaCase c, String mode) {
    Trace trace = new Trace();

    CoveragePolicy policy = trace.time("Policy lookup",
        () -> data.findPolicy(c.service().code()).orElse(null),
        p -> p != null ? "%s matched on %s %s".formatted(p.id(), c.service().codeSystem(), c.service().code()) : "No policy found");
    if (policy == null) throw new IllegalStateException("No coverage policy for " + c.service().code() + ".");

    Map<String, Integer> counts = new LinkedHashMap<>();
    List<CaseDocument> docs = trace.time("Identifier redaction", () -> c.documents().stream().map(d -> {
      Redactor.Result r = Redactor.redact(d.text());
      r.counts().forEach((k, n) -> counts.merge(k, n, Integer::sum));
      return d.withText(r.text());
    }).toList(), r -> "%d identifiers removed from %d documents".formatted(
        counts.values().stream().mapToInt(Integer::intValue).sum(), c.documents().size()));

    ModelOutput output;
    String model;
    TokenUsage usage = null;
    if ("live".equals(mode)) {
      ClaudeSupport.Structured<ModelOutput> r = trace.time("Criteria evaluation (LLM)", () -> claude.evaluate(c, policy, docs),
          res -> "%s, %d in / %d out tokens".formatted(res.model(), res.usage().inputTokens(), res.usage().outputTokens()));
      output = r.output();
      model = r.model();
      usage = r.usage();
    } else {
      var recorded = data.recorded();
      ModelOutput rec = recorded.cases() == null ? null : recorded.cases().get(c.id());
      if (rec == null) throw new ReplayMissingException("No recorded output for " + c.id() + ". Use live mode.");
      output = trace.time("Criteria evaluation (replay)", () -> rec,
          r -> "Loaded " + ("live".equals(recorded.source()) ? "recorded live output" : "sample output written for the demo"));
      model = recorded.model();
    }

    Routing routing = trace.time("Citation check + routing", () -> finalizeResult(policy, docs, output), r -> {
      long total = r.criteria().stream().mapToLong(x -> x.evidence().size()).sum();
      long verified = r.criteria().stream().flatMap(x -> x.evidence().stream()).filter(Evidence::verified).count();
      long downgraded = r.criteria().stream().filter(CriterionResult::downgraded).count();
      return "%d of %d quotes found verbatim; %d criteria downgraded".formatted(verified, total, downgraded);
    });

    List<RedactionCount> redactions = counts.entrySet().stream().map(e -> new RedactionCount(e.getKey(), e.getValue())).toList();
    return new Evaluation(ENGINE, c.id(), policy.id(), policy.title(), mode, model, routing.route(), routing.routeReason(),
        output.summary(), routing.criteria(), redactions, trace.steps(), usage, decisionDueBy(c), Instant.now().toString(),
        output, null);
  }
}
