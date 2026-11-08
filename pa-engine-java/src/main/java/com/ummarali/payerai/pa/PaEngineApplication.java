package com.ummarali.payerai.pa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan com.ummarali.payerai so the shared beans (ClaudeSupport, LiveMode, ApiExceptionHandler) load too.
@SpringBootApplication(scanBasePackages = "com.ummarali.payerai")
public class PaEngineApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaEngineApplication.class, args);
  }
}
