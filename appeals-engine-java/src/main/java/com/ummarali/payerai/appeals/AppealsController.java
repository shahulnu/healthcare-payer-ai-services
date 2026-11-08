package com.ummarali.payerai.appeals;

import com.ummarali.payerai.appeals.domain.Analysis;
import com.ummarali.payerai.appeals.domain.DeniedClaim;
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
 * Same endpoints and JSON as the Next.js route (src/app/api/appeals/analyze/route.ts), so the demo
 * UI can switch engines by pointing APPEALS_ENGINE_URL at this service. Errors are mapped by the
 * shared ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/appeals")
public class AppealsController {

  public record AnalyzeRequest(String claimId, String mode) {}

  private final AppealsData data;
  private final AppealsEngine engine;
  private final ClaudeSupport claude;
  private final LiveMode liveMode;

  public AppealsController(AppealsData data, AppealsEngine engine, ClaudeSupport claude, LiveMode liveMode) {
    this.data = data;
    this.engine = engine;
    this.claude = claude;
    this.liveMode = liveMode;
  }

  @GetMapping("/analyze")
  public Map<String, Object> status() {
    return Map.of("liveAvailable", claude.liveAvailable(), "engine", AppealsEngine.ENGINE);
  }

  @GetMapping("/claims")
  public List<DeniedClaim> claims() {
    return data.claims();
  }

  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(@RequestBody(required = false) AnalyzeRequest body, HttpServletRequest req) {
    if (body == null || body.claimId() == null) return ApiError.of(HttpStatus.BAD_REQUEST, "Send a JSON body with a claimId.");
    DeniedClaim c = data.findClaim(body.claimId()).orElse(null);
    if (c == null) return ApiError.of(HttpStatus.NOT_FOUND, "Unknown claim \"" + body.claimId() + "\".");

    LiveMode.Decision d = liveMode.resolve(body.mode(), req);
    Analysis result = engine.analyze(c, d.mode());
    return ResponseEntity.ok(d.notice() == null ? result : result.withNotice(d.notice()));
  }
}
