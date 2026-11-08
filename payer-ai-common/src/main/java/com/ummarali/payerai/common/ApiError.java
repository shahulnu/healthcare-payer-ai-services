package com.ummarali.payerai.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/** The { "error": "..." } body every endpoint returns on failure, matching the Next.js API. */
public final class ApiError {

  private ApiError() {}

  public static ResponseEntity<Map<String, String>> of(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("error", message));
  }
}
