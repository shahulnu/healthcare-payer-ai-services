package com.ummarali.payerai.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitationsTest {

  @Test
  void matchesIgnoringCaseAndWhitespace() {
    assertThat(Citations.quoteFound("POSITIVE  straight-leg\nraise", "Exam: Positive straight-leg raise on the left.")).isTrue();
  }

  @Test
  void rejectsParaphrasesEmptyQuotesAndMissingSources() {
    assertThat(Citations.quoteFound("SLR was positive", "Positive straight-leg raise.")).isFalse();
    assertThat(Citations.quoteFound("   ", "anything")).isFalse();
    assertThat(Citations.quoteFound("anything", null)).isFalse();
  }

  @Test
  void usageAddsUp() {
    var total = new TokenUsage(100, 50, 0.01).plus(new TokenUsage(20, 10, 0.002)).plus(null);
    assertThat(total.inputTokens()).isEqualTo(120);
    assertThat(total.outputTokens()).isEqualTo(60);
    assertThat(total.estCostUsd()).isEqualTo(0.012, org.assertj.core.data.Offset.offset(1e-9));
  }
}
