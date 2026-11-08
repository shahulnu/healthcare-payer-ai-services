package com.ummarali.payerai.common;

import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps engine and SDK errors to the same responses and messages as src/lib/shared/api.ts. */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ModelDeclinedException.class)
  ResponseEntity<Map<String, String>> declined(ModelDeclinedException e) {
    return ApiError.of(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }

  @ExceptionHandler(ReplayMissingException.class)
  ResponseEntity<Map<String, String>> replayMissing(ReplayMissingException e) {
    return ApiError.of(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(RateLimitException.class)
  ResponseEntity<Map<String, String>> rateLimited(RateLimitException e) {
    return ApiError.of(HttpStatus.TOO_MANY_REQUESTS, "The model is rate limited. Try again in a minute or use replay.");
  }

  @ExceptionHandler(AnthropicServiceException.class)
  ResponseEntity<Map<String, String>> apiError(AnthropicServiceException e) {
    return ApiError.of(HttpStatus.BAD_GATEWAY, "Model API error " + e.statusCode() + ". Try replay mode.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> unexpected(Exception e) {
    log.error("Request failed", e);
    return ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "The request failed.");
  }
}
