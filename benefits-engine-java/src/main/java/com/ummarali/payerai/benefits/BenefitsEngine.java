package com.ummarali.payerai.benefits;

import com.ummarali.payerai.benefits.domain.BenefitsAnswer;
import com.ummarali.payerai.benefits.domain.BenefitsAnswer.ToolCall;
import com.ummarali.payerai.benefits.domain.Member;
import com.ummarali.payerai.benefits.domain.Plan;
import com.ummarali.payerai.benefits.domain.RecordedBenefits.RecordedRun;
import com.ummarali.payerai.benefits.domain.RecordedBenefits.RecordedToolCall;
import com.ummarali.payerai.common.ReplayMissingException;
import com.ummarali.payerai.common.Trace;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Java port of answerQuestion() in src/lib/benefits/engine.ts: look up the plan, run the agent
 * (live) or re-run a recorded run's tool calls (replay), then check the answer in code.
 */
@Service
public class BenefitsEngine {

  public static final String ENGINE = "java";

  private final BenefitsData data;
  private final BenefitsAgent agent;

  public BenefitsEngine(BenefitsData data, BenefitsAgent agent) {
    this.data = data;
    this.agent = agent;
  }

  public BenefitsAnswer answer(Member member, String question, String questionId, String mode) {
    Trace trace = new Trace();
    var recorded = data.recorded();
    boolean liveRecording = "live".equals(recorded.source());

    Plan plan = trace.time("Member + plan lookup", () -> data.findPlan(member.planId()).orElse(null),
        p -> p != null ? "%s, %d sections".formatted(p.name(), p.sections().size()) : "No plan");
    if (plan == null) throw new IllegalStateException("No plan " + member.planId() + ".");

    BenefitsAgent.Run run;
    if ("live".equals(mode)) {
      run = trace.time("Agent loop (LLM + tools)", () -> agent.run(plan, member, question),
          r -> "%d model turns, %d tool calls".formatted(r.turns(), r.toolCalls().size()));
    } else {
      RecordedRun rec = questionId == null || recorded.cases() == null ? null : recorded.cases().get(questionId);
      if (rec == null) throw new ReplayMissingException("Replay works for the sample questions only. Pick one, or switch to Live to ask your own.");
      // Tools are deterministic, so replay re-runs the recorded calls against today's data.
      run = trace.time("Agent loop (replay)", () -> new BenefitsAgent.Run(
              rec.toolCalls().stream().map(t -> new ToolCall(t.name(), t.input(), PlanTools.run(data, plan, member, t.name(), t.input()))).toList(),
              rec.submission(), null, recorded.model(), 0),
          r -> "Re-ran %d recorded tool calls; loaded %s answer".formatted(r.toolCalls().size(), liveRecording ? "recorded live" : "sample"));
    }

    AnswerChecker.Checked checked = trace.time("Citation + number check", () -> AnswerChecker.check(plan, run.submission(), run.toolCalls()),
        c -> "%d of %d quotes verified; %d dollar amounts, %d unsupported".formatted(
            c.citations().stream().filter(x -> x.verified()).count(), c.citations().size(),
            c.numberCheck().found().size(), c.numberCheck().unsupported().size()));

    List<RecordedToolCall> rawCalls = run.toolCalls().stream().map(t -> new RecordedToolCall(t.name(), t.input())).toList();
    return new BenefitsAnswer(ENGINE, member.id(), plan.name(), questionId, question, mode,
        "live".equals(mode) ? null : (liveRecording ? "live" : "sample"), run.model(), checked.status(), checked.answer(),
        checked.escalationReason(), checked.citations(), checked.estimates(), checked.numberCheck(), run.toolCalls(), trace.steps(),
        run.usage(), Instant.now().toString(), new RecordedRun(rawCalls, run.submission()), null);
  }
}
