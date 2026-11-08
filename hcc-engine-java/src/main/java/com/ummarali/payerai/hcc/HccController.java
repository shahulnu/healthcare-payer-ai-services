package com.ummarali.payerai.hcc;

import com.ummarali.payerai.common.ApiError;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.common.LiveMode;
import com.ummarali.payerai.hcc.domain.ChartMember;
import com.ummarali.payerai.hcc.domain.HccReview;
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
 * Same endpoints and JSON as the Next.js route (src/app/api/hcc/review/route.ts), so the demo can switch
 * engines by pointing HCC_ENGINE_URL at this service. Errors are mapped by the shared ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/hcc")
public class HccController {

  public record ReviewRequest(String memberId, String mode) {}

  private final HccData data;
  private final HccEngine engine;
  private final ClaudeSupport claude;
  private final LiveMode liveMode;

  public HccController(HccData data, HccEngine engine, ClaudeSupport claude, LiveMode liveMode) {
    this.data = data;
    this.engine = engine;
    this.claude = claude;
    this.liveMode = liveMode;
  }

  @GetMapping("/review")
  public Map<String, Object> status() {
    return Map.of("liveAvailable", claude.liveAvailable(), "engine", HccEngine.ENGINE);
  }

  @GetMapping("/members")
  public List<ChartMember> members() {
    return data.members();
  }

  @PostMapping("/review")
  public ResponseEntity<?> review(@RequestBody(required = false) ReviewRequest body, HttpServletRequest req) {
    if (body == null || body.memberId() == null) return ApiError.of(HttpStatus.BAD_REQUEST, "Send a JSON body with a memberId.");
    ChartMember member = data.findMember(body.memberId()).orElse(null);
    if (member == null) return ApiError.of(HttpStatus.NOT_FOUND, "Unknown member \"" + body.memberId() + "\".");

    LiveMode.Decision d = liveMode.resolve(body.mode(), req);
    HccReview review = engine.review(member, d.mode());
    return ResponseEntity.ok(d.notice() == null ? review : review.withNotice(d.notice()));
  }
}
