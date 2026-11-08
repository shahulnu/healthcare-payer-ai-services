package com.ummarali.payerai.common;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-client daily cap on live model runs. In-memory and per-instance; in production this
 * belongs in the API gateway or a shared store such as Redis.
 */
@Component
public class LiveLimiter {

  private record Count(LocalDate day, int count) {}

  private final int dailyLimit;
  private final Map<String, Count> usage = new ConcurrentHashMap<>();

  public LiveLimiter(@Value("${payerai.daily-limit:10}") int dailyLimit) {
    this.dailyLimit = dailyLimit;
  }

  public int dailyLimit() {
    return dailyLimit;
  }

  /** Counts one live run for this client and returns false once the day's cap is reached. */
  public boolean allow(String client) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    boolean[] allowed = {true};
    usage.compute(client, (k, c) -> {
      if (c == null || !c.day().equals(today)) return new Count(today, 1);
      if (c.count() >= dailyLimit) {
        allowed[0] = false;
        return c;
      }
      return new Count(today, c.count() + 1);
    });
    return allowed[0];
  }
}
