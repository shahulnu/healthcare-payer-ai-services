package com.ummarali.payerai.pa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.common.ClaudeSupport;
import com.ummarali.payerai.common.ContractCheck;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** This engine must reproduce the golden files the TypeScript engine also has to match. */
class ContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final PaData DATA = new PaData(MAPPER);
  private static final PaEngine ENGINE = new PaEngine(DATA, new ClaudeEvaluator(new ClaudeSupport("claude-opus-5-5", "")));

  static List<Path> goldenFiles() {
    return ContractCheck.files("pa");
  }

  @ParameterizedTest
  @MethodSource("goldenFiles")
  void matchesContract(Path golden) {
    var result = ENGINE.evaluate(DATA.findCase(ContractCheck.idOf(golden)).orElseThrow(), "replay");
    assertThat(ContractCheck.differences(MAPPER, golden, result)).isEmpty();
  }
}
