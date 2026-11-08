package com.ummarali.payerai.pi;

import com.ummarali.payerai.common.ApiError;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.common.LiveMode;
import com.ummarali.payerai.pi.domain.Lead;
import com.ummarali.payerai.pi.domain.PiBrief;
import com.ummarali.payerai.pi.domain.Screen;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same endpoints and JSON as the Next.js routes (src/app/api/pi/), so the demo can switch engines by
 * pointing PI_ENGINE_URL at this service. Errors are mapped by the shared ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/pi")
public class PiController {

  public record BriefRequest(String providerId, String mode) {}

  private final PiData data;
  private final PiEngine engine;
  private final ClaudeSupport claude;
  private final LiveMode liveMode;

  public PiController(PiData data, PiEngine engine, ClaudeSupport claude, LiveMode liveMode) {
    this.data = data;
    this.engine = engine;
    this.claude = claude;
    this.liveMode = liveMode;
  }

  @GetMapping("/leads")
  public Screen leads() {
    return data.screen();
  }

  @GetMapping("/brief")
  public Map<String, Object> status() {
    return Map.of("liveAvailable", claude.liveAvailable(), "engine", PiEngine.ENGINE);
  }

  @PostMapping("/brief")
  public ResponseEntity<?> brief(@RequestBody(required = false) BriefRequest body, HttpServletRequest req) {
    if (body == null || body.providerId() == null) return ApiError.of(HttpStatus.BAD_REQUEST, "Send a JSON body with a providerId.");
    Lead lead = data.findLead(body.providerId()).orElse(null);
    if (lead == null) return ApiError.of(HttpStatus.NOT_FOUND, "\"" + body.providerId() + "\" isn't a lead in this screen.");

    LiveMode.Decision d = liveMode.resolve(body.mode(), req);
    PiBrief brief = engine.brief(lead, d.mode());
    return ResponseEntity.ok(d.notice() == null ? brief : brief.withNotice(d.notice()));
  }
}
