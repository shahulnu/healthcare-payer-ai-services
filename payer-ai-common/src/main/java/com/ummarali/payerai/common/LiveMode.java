package com.ummarali.payerai.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Picks live or replay for a demo request and explains any downgrade, like resolveMode() in src/lib/shared/api.ts. */
@Component
public class LiveMode {

  public record Decision(String mode, String notice) {}

  private final ClaudeSupport claude;
  private final LiveLimiter limiter;

  public LiveMode(ClaudeSupport claude, LiveLimiter limiter) {
    this.claude = claude;
    this.limiter = limiter;
  }

  public Decision resolve(String requested, HttpServletRequest req) {
    if (!"live".equals(requested)) return new Decision("replay", null);
    if (!claude.liveAvailable()) return new Decision("replay", "Live mode is off on this deployment, so this is a replay.");
    if (!limiter.allow(clientIp(req))) {
      return new Decision("replay", "Daily limit of " + limiter.dailyLimit() + " live runs reached, so this is a replay.");
    }
    return new Decision("live", null);
  }

  static String clientIp(HttpServletRequest req) {
    String fwd = req.getHeader("X-Forwarded-For");
    return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr();
  }
}
