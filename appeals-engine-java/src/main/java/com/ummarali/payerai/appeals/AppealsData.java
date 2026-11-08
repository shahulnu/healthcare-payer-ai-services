package com.ummarali.payerai.appeals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.appeals.domain.CodeBook;
import com.ummarali.payerai.appeals.domain.DeniedClaim;
import com.ummarali.payerai.appeals.domain.PaymentPolicy;
import com.ummarali.payerai.appeals.domain.RecordedAppeals;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the shared demo data (data/appeals/*.json, copied onto the classpath by the Maven build).
 * A production service would read 835 remittances from the clearinghouse feed and records from
 * the EHR document store instead.
 */
@Component
public class AppealsData {

  private final List<DeniedClaim> claims;
  private final List<PaymentPolicy> policies;
  private final CodeBook codes;
  private final RecordedAppeals recorded;

  public AppealsData(ObjectMapper mapper) {
    this.claims = read(mapper, "appeals/claims.json", new TypeReference<>() {});
    this.policies = read(mapper, "appeals/policies.json", new TypeReference<>() {});
    this.codes = read(mapper, "appeals/codes.json", new TypeReference<>() {});
    this.recorded = read(mapper, "appeals/recorded.json", new TypeReference<>() {});
  }

  private static <T> T read(ObjectMapper mapper, String path, TypeReference<T> type) {
    try (InputStream in = AppealsData.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing classpath resource " + path + ". Build with Maven so data/appeals is copied.");
      return mapper.readValue(in, type);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + path, e);
    }
  }

  public List<DeniedClaim> claims() {
    return claims;
  }

  public Optional<DeniedClaim> findClaim(String id) {
    return claims.stream().filter(c -> c.id().equals(id)).findFirst();
  }

  public List<PaymentPolicy> findPolicies(List<String> ids) {
    return policies.stream().filter(p -> ids.contains(p.id())).toList();
  }

  public CodeBook codes() {
    return codes;
  }

  public RecordedAppeals recorded() {
    return recorded;
  }
}
