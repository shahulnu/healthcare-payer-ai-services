package com.ummarali.payerai.benefits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan com.ummarali.payerai so the shared beans (ClaudeSupport, LiveMode, ApiExceptionHandler) load too.
@SpringBootApplication(scanBasePackages = "com.ummarali.payerai")
public class BenefitsEngineApplication {

  public static void main(String[] args) {
    SpringApplication.run(BenefitsEngineApplication.class, args);
  }
}
