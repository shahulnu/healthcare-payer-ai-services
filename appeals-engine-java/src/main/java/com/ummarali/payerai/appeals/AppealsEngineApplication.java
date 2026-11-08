package com.ummarali.payerai.appeals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan com.ummarali.payerai so the shared beans (ClaudeSupport, LiveMode, ApiExceptionHandler) load too.
@SpringBootApplication(scanBasePackages = "com.ummarali.payerai")
public class AppealsEngineApplication {

  public static void main(String[] args) {
    SpringApplication.run(AppealsEngineApplication.class, args);
  }
}
