package com.ummarali.payerai.benefits;

import com.ummarali.payerai.benefits.domain.BenefitsAnswer;
import com.ummarali.payerai.benefits.domain.Member;
import com.ummarali.payerai.common.ApiError;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.common.LiveMode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same endpoints and JSON as the Next.js route (src/app/api/benefits/ask/route.ts), so the demo
 * UI can switch engines by pointing BENEFITS_ENGINE_URL at this service. Errors are mapped by the
 * shared ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/benefits")
public class BenefitsController {

  static final int MAX_QUESTION = 500;

  public record AskRequest(String memberId, String questionId, String question, String mode) {}

  private final BenefitsData data;
  private final BenefitsEngine engine;
  private final ClaudeSupport claude;
  private final LiveMode liveMode;

  public BenefitsController(BenefitsData data, BenefitsEngine engine, ClaudeSupport claude, LiveMode liveMode) {
    this.data = data;
    this.engine = engine;
    this.claude = claude;
    this.liveMode = liveMode;
  }

  @GetMapping("/ask")
  public Map<String, Object> status() {
    return Map.of("liveAvailable", claude.liveAvailable(), "engine", BenefitsEngine.ENGINE);
  }

  @GetMapping("/members")
  public List<Member> members() {
    return data.members();
  }

  @PostMapping("/ask")
  public ResponseEntity<?> ask(@RequestBody(required = false) AskRequest body, HttpServletRequest req) {
    if (body == null) return ApiError.of(HttpStatus.BAD_REQUEST, "Send a JSON body with a memberId and a questionId or question.");

    // A sample question carries its member; a typed question needs one.
    BenefitsData.SampleMatch sample = null;
    if (body.questionId() != null) {
      sample = data.findSampleQuestion(body.questionId()).orElse(null);
      if (sample == null) return ApiError.of(HttpStatus.NOT_FOUND, "Unknown sample question \"" + body.questionId() + "\".");
    }
    Member member = sample != null ? sample.member() : body.memberId() == null ? null : data.findMember(body.memberId()).orElse(null);
    if (member == null) return ApiError.of(HttpStatus.NOT_FOUND, "Unknown member \"" + body.memberId() + "\".");

    String question = (sample != null ? sample.question().text() : body.question() == null ? "" : body.question()).trim();
    if (question.isEmpty()) return ApiError.of(HttpStatus.BAD_REQUEST, "Ask a question first.");
    if (question.length() > MAX_QUESTION) return ApiError.of(HttpStatus.BAD_REQUEST, "Keep questions under " + MAX_QUESTION + " characters.");

    LiveMode.Decision d = liveMode.resolve(body.mode(), req);
    BenefitsAnswer answer = engine.answer(member, question, sample != null ? sample.question().id() : null, d.mode());
    return ResponseEntity.ok(d.notice() == null ? answer : answer.withNotice(d.notice()));
  }
}
