package com.ummarali.payerai.common;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Claude access shared by every service: API key lookup, one lazily built client, and a
 * structured-output call with a cached system prompt and server-side fallback.
 */
@Component
public class ClaudeSupport {

  /** A parsed structured response plus the model that served it and what it cost. */
  public record Structured<T>(T output, String model, TokenUsage usage) {}

  // Claude Opus 5.5 list price, USD per million tokens.
  private static final double PRICE_IN = 4.0;
  private static final double PRICE_OUT = 20.0;

  private final String model;
  private final Optional<String> apiKey;
  private volatile AnthropicClient client;

  public ClaudeSupport(@Value("${payerai.model:claude-opus-5-5}") String model, @Value("${payerai.env-file:}") String envFile) {
    this.model = model;
    this.apiKey = resolveApiKey(envFile);
  }

  /** ANTHROPIC_API_KEY from the environment, else from the file in payerai.env-file (this project's .env). */
  private static Optional<String> resolveApiKey(String envFile) {
    String fromEnv = System.getenv("ANTHROPIC_API_KEY");
    if (fromEnv != null && !fromEnv.isBlank()) return Optional.of(fromEnv.trim());
    if (envFile == null || envFile.isBlank()) return Optional.empty();
    try {
      return Files.readAllLines(Path.of(envFile)).stream()
          .map(String::trim)
          .filter(l -> l.startsWith("ANTHROPIC_API_KEY="))
          .map(l -> l.substring("ANTHROPIC_API_KEY=".length()).trim())
          .filter(v -> !v.isEmpty())
          .findFirst();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  public boolean liveAvailable() {
    return apiKey.isPresent();
  }

  public String model() {
    return model;
  }

  public AnthropicClient client() {
    if (client == null) {
      synchronized (this) {
        if (client == null) client = AnthropicOkHttpClient.builder().apiKey(apiKey.orElseThrow()).build();
      }
    }
    return client;
  }

  public static TokenUsage usageOf(Usage u) {
    long input = u.inputTokens() + u.cacheCreationInputTokens().orElse(0L) + u.cacheReadInputTokens().orElse(0L);
    long out = u.outputTokens();
    return new TokenUsage(input, out, (input * PRICE_IN + out * PRICE_OUT) / 1_000_000);
  }

  /**
   * One structured-output call. The JSON schema is derived from {@code type}. Effort is left at
   * Claude Opus 5.5's default (medium), matching the TypeScript engines.
   */
  /**
   * Request settings every call shares: model, output cap, a cached system prompt, and server-side
   * fallback, so a safety-classifier decline is retried on Anthropic's recommended fallback model.
   */
  public MessageCreateParams.Builder baseParams(String systemPrompt, long maxTokens) {
    return MessageCreateParams.builder()
        .model(model)
        .maxTokens(maxTokens)
        .systemOfTextBlockParams(List.of(TextBlockParam.builder()
            .text(systemPrompt)
            .cacheControl(CacheControlEphemeral.builder().build())
            .build()))
        .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
  }

  public <T> Structured<T> structured(String systemPrompt, String userMessage, Class<T> type, long maxTokens, String declinedMessage) {
    StructuredMessageCreateParams<T> params = baseParams(systemPrompt, maxTokens)
        .addUserMessage(userMessage)
        .outputConfig(type)
        .build();

    StructuredMessage<T> response = client().messages().create(params);

    if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
      throw new ModelDeclinedException(declinedMessage);
    }
    T output = response.content().stream()
        .flatMap(block -> block.text().stream())
        .map(typed -> typed.text())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Model output did not match the schema (stop reason: "
            + response.stopReason().map(Object::toString).orElse("none") + ")."));
    return new Structured<>(output, response.model().asString(), usageOf(response.usage()));
  }
}
