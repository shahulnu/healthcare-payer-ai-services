package com.ummarali.payerai.pa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.pa.domain.CoveragePolicy;
import com.ummarali.payerai.pa.domain.PaCase;
import com.ummarali.payerai.pa.domain.RecordedOutputs;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the shared demo data (data/pa/*.json, copied onto the classpath by the Maven build).
 * A production service would read requests from the UM platform and policies from a versioned
 * policy library instead.
 */
@Component
public class PaData {

  private final List<PaCase> cases;
  private final List<CoveragePolicy> policies;
  private final RecordedOutputs recorded;

  public PaData(ObjectMapper mapper) {
    this.cases = read(mapper, "pa/cases.json", new TypeReference<>() {});
    this.policies = read(mapper, "pa/policies.json", new TypeReference<>() {});
    this.recorded = read(mapper, "pa/recorded.json", new TypeReference<>() {});
  }

  private static <T> T read(ObjectMapper mapper, String path, TypeReference<T> type) {
    try (InputStream in = PaData.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing classpath resource " + path + ". Build with Maven so data/pa is copied.");
      return mapper.readValue(in, type);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + path, e);
    }
  }

  public List<PaCase> cases() {
    return cases;
  }

  public Optional<PaCase> findCase(String id) {
    return cases.stream().filter(c -> c.id().equals(id)).findFirst();
  }

  public Optional<CoveragePolicy> findPolicy(String serviceCode) {
    return policies.stream().filter(p -> p.serviceCodes().contains(serviceCode)).findFirst();
  }

  public RecordedOutputs recorded() {
    return recorded;
  }
}
