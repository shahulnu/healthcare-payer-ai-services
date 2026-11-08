package com.ummarali.payerai.pi;

import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.common.ReplayMissingException;
import com.ummarali.payerai.common.TokenUsage;
import com.ummarali.payerai.common.Trace;
import com.ummarali.payerai.pi.domain.BriefOutput;
import com.ummarali.payerai.pi.domain.Fact;
import com.ummarali.payerai.pi.domain.FeatureResult;
import com.ummarali.payerai.pi.domain.Lead;
import com.ummarali.payerai.pi.domain.PeerPoint;
import com.ummarali.payerai.pi.domain.PiBrief;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Java port of src/lib/pi/engine.ts. The screen has already scored and tiered the lead. This class
 * turns the results into facts, gets a brief from Claude (or a recording), and checks it in code.
 */
@Service
public class PiEngine {

  public static final String ENGINE = "java";

  // Same prompt as src/lib/pi/engine.ts. Kept byte-identical across requests so it can be cached.
  static final String SYSTEM_PROMPT = """
      You write lead briefs for analysts on a health plan's special investigations unit. A statistical screen has already flagged this provider and set the tier. You explain the billing pattern so an analyst can decide what to do. You do not decide anything.

      Rules:
      - Use only the facts provided. Copy every number exactly as it appears in a fact. Do not calculate, round or introduce any other number, and do not number your lists.
      - This includes illustrations: no example durations, thresholds or dates, and no procedure codes the facts don't mention. Write "99215" rather than "level 5". Describe alternatives in words instead.
      - Describe billing patterns, not conduct. A statistical outlier is not evidence of wrongdoing. Never use the words fraud, fraudulent, abuse, abusive, upcoding, kickback, scheme, illegal, criminal, intentional or deliberate, and never state or imply intent.
      - "headline": one sentence naming the pattern.
      - "pattern": two or three plain sentences for the analyst, on what stands out against peers and why it matters for review.
      - "alternative_explanations": at least two legitimate reasons that could explain the pattern, each with what would confirm or rule it out.
      - "next_steps": concrete steps for the analyst, using the record sample size in the facts. Never recommend recoupment, payment holds, termination or a referral to law enforcement. Those decisions belong to people after a records review.
      - "cited_fact_ids": the ids of the facts you relied on.""";

  private final PiData data;
  private final ClaudeSupport claude;

  public PiEngine(PiData data, ClaudeSupport claude) {
    this.data = data;
    this.claude = claude;
  }

  /** What the code decides: the action follows the tier, and the model can't change either. */
  static String actionFor(Lead lead) {
    return lead.tier().equals("high") ? "Open a records review" : "Analyst review; provider education if the pattern is confirmed";
  }

  static int sampleFor(Lead lead) {
    return lead.tier().equals("high") ? 30 : 15;
  }

  /** The only facts the model sees. All are aggregates, with no member-level data. */
  List<Fact> buildFacts(Lead lead) {
    var screen = data.screen();
    Map<String, List<PeerPoint>> group = screen.peers().get(lead.specialty());
    int peerCount = group.values().iterator().next().size();
    List<Fact> facts = new ArrayList<>();
    facts.add(new Fact("F-COHORT", "Review period %s. Peer group: %d %s providers.".formatted(screen.period(), peerCount, lead.specialty()), false));
    facts.add(new Fact("F-VOLUME", "Visits in the period: %s.".formatted(String.format(Locale.ROOT, "%,d", lead.encounters())), false));

    List<PeerPoint> level5 = group.get("level5_share");
    if (level5 != null) {
      level5.stream().filter(p -> p.id().equals(lead.providerId()) && p.risk() != null).findFirst()
          .ifPresent(p -> facts.add(new Fact("F-RISK", "Average patient risk score: %s.".formatted(PeerScreen.fixed(p.risk(), 2)), false)));
    }
    for (FeatureResult f : lead.features()) {
      String expected = f.expectedDisplay() != null ? ", expected for this panel's risk " + f.expectedDisplay() : "";
      String rule = f.hardRule() ? ", above the plausibility limit of 16 hours" : "";
      facts.add(new Fact("F-" + f.id(),
          "%s: %s (peer median %s%s, robust z-score %s%s). %s".formatted(f.label(), f.display(), f.peerDisplay(), expected, PeerScreen.fixed(f.z(), 1), rule,
              f.flagged() ? "Flagged by the screen." : "Not flagged."),
          f.flagged()));
    }
    if (lead.exposure() > 0) {
      facts.add(new Fact("F-EXPOSURE",
          "Estimated billing above the peer pattern: $%s. This sizes the lead and is not an overpayment finding.".formatted(String.format(Locale.ROOT, "%,d", lead.exposure())),
          false));
    }
    facts.add(new Fact("F-TIER", "Screen tier: %s. Screen score: %d. Action: %s.".formatted(lead.tier(), lead.score(), actionFor(lead)), false));
    facts.add(new Fact("F-SAMPLE", "Record sample to request: %d claims.".formatted(sampleFor(lead)), false));
    return facts;
  }

  static String userMessage(Lead lead, List<Fact> facts) {
    String lines = facts.stream().map(f -> f.id() + ": " + f.text()).collect(Collectors.joining("\n"));
    return "<lead provider=\"%s\" specialty=\"%s\">\n%s\n</lead>\n\nWrite the brief.".formatted(lead.providerId(), lead.specialty(), lines);
  }

  public PiBrief brief(Lead lead, String mode) {
    Trace trace = new Trace();
    var recorded = data.recorded();
    boolean liveRecording = "live".equals(recorded.source());

    List<Fact> facts = trace.time("Screen results to facts", () -> buildFacts(lead),
        f -> "%d facts, %d flagged; aggregates only, no member data".formatted(f.size(), f.stream().filter(Fact::flagged).count()));

    BriefOutput brief;
    String model;
    TokenUsage usage = null;
    if ("live".equals(mode)) {
      ClaudeSupport.Structured<BriefOutput> r = trace.time("Lead brief (LLM)",
          () -> claude.structured(SYSTEM_PROMPT, userMessage(lead, facts), BriefOutput.class, 16000L,
              "The model declined this lead. An analyst can review the screen results directly."),
          x -> "%s, %d in / %d out tokens".formatted(x.model(), x.usage().inputTokens(), x.usage().outputTokens()));
      brief = r.output();
      model = r.model();
      usage = r.usage();
    } else {
      BriefOutput rec = recorded.cases() == null ? null : recorded.cases().get(lead.providerId());
      if (rec == null) throw new ReplayMissingException("No recorded brief for this lead yet. Switch to Live, or run npm run record:pi.");
      brief = trace.time("Lead brief (replay)", () -> rec,
          x -> "Loaded " + (liveRecording ? "recorded live output" : "sample output written for the demo"));
      model = recorded.model();
    }

    BriefChecker.Checked c = trace.time("Number + language + citation check", () -> BriefChecker.check(facts, brief),
        x -> "%d figures, %d unsupported; %d flagged words; %d citations".formatted(x.numberCheck().found().size(), x.numberCheck().unsupported().size(),
            x.languageCheck().flagged().size(), x.citationCheck().cited().size()));

    return new PiBrief(ENGINE, lead.providerId(), mode, "live".equals(mode) ? null : (liveRecording ? "live" : "sample"), model, c.status(), c.holdReasons(),
        lead.tier(), actionFor(lead), brief, facts, c.numberCheck(), c.languageCheck(), c.citationCheck(), trace.steps(), usage, Instant.now().toString(), brief, null);
  }
}
