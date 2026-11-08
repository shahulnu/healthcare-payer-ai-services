package com.ummarali.payerai.pa;

import com.ummarali.payerai.common.ApiError;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.common.LiveMode;
import com.ummarali.payerai.pa.domain.Evaluation;
import com.ummarali.payerai.pa.domain.PaCase;
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
 * Same endpoints and JSON as the Next.js route (src/app/api/pa/evaluate/route.ts), so the demo
 * UI can switch engines by pointing PA_ENGINE_URL at this service. Errors are mapped by the
 * shared ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/pa")
public class PaController {

  public record EvaluateRequest(String caseId, String mode) {}

  private final PaData data;
  private final PaEngine engine;
  private final ClaudeSupport claude;
  private final LiveMode liveMode;

  public PaController(PaData data, PaEngine engine, ClaudeSupport claude, LiveMode liveMode) {
    this.data = data;
    this.engine = engine;
    this.claude = claude;
    this.liveMode = liveMode;
  }

  @GetMapping("/evaluate")
  public Map<String, Object> status() {
    return Map.of("liveAvailable", claude.liveAvailable(), "engine", PaEngine.ENGINE);
  }

  @GetMapping("/cases")
  public List<PaCase> cases() {
    return data.cases();
  }

  @PostMapping("/evaluate")
  public ResponseEntity<?> evaluate(@RequestBody(required = false) EvaluateRequest body, HttpServletRequest req) {
    if (body == null || body.caseId() == null) return ApiError.of(HttpStatus.BAD_REQUEST, "Send a JSON body with a caseId.");
    PaCase c = data.findCase(body.caseId()).orElse(null);
    if (c == null) return ApiError.of(HttpStatus.NOT_FOUND, "Unknown case \"" + body.caseId() + "\".");

    LiveMode.Decision d = liveMode.resolve(body.mode(), req);
    Evaluation result = engine.evaluate(c, d.mode());
    return ResponseEntity.ok(d.notice() == null ? result : result.withNotice(d.notice()));
  }
}
