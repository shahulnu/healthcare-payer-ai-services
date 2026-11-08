package com.ummarali.payerai.benefits;

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
  private static final BenefitsData DATA = new BenefitsData(MAPPER);
  private static final BenefitsEngine ENGINE =
      new BenefitsEngine(DATA, new BenefitsAgent(new ClaudeSupport("claude-opus-5-5", ""), DATA, MAPPER));

  static List<Path> goldenFiles() {
    return ContractCheck.files("benefits");
  }

  @ParameterizedTest
  @MethodSource("goldenFiles")
  void matchesContract(Path golden) {
    String id = ContractCheck.idOf(golden);
    var sample = DATA.findSampleQuestion(id).orElseThrow();
    var result = ENGINE.answer(sample.member(), sample.question().text(), id, "replay");
    assertThat(ContractCheck.differences(MAPPER, golden, result)).isEmpty();
  }
}
