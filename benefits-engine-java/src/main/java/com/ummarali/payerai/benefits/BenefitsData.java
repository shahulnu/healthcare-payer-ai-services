package com.ummarali.payerai.benefits;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.benefits.domain.Member;
import com.ummarali.payerai.benefits.domain.Plan;
import com.ummarali.payerai.benefits.domain.RecordedBenefits;
import com.ummarali.payerai.benefits.domain.Service;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the shared demo data (data/benefits/*.json, copied onto the classpath by the Maven build).
 * A production service would read plan documents from the product library and accumulators from
 * the claims system instead.
 */
@Component
public class BenefitsData {

  public record SampleMatch(Member member, Member.SampleQuestion question) {}

  private final List<Plan> plans;
  private final List<Service> services;
  private final List<Member> members;
  private final RecordedBenefits recorded;

  public BenefitsData(ObjectMapper mapper) {
    this.plans = read(mapper, "benefits/plans.json", new TypeReference<>() {});
    this.services = read(mapper, "benefits/services.json", new TypeReference<>() {});
    this.members = read(mapper, "benefits/members.json", new TypeReference<>() {});
    this.recorded = read(mapper, "benefits/recorded.json", new TypeReference<>() {});
  }

  private static <T> T read(ObjectMapper mapper, String path, TypeReference<T> type) {
    try (InputStream in = BenefitsData.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing classpath resource " + path + ". Build with Maven so data/benefits is copied.");
      return mapper.readValue(in, type);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + path, e);
    }
  }

  public List<Member> members() {
    return members;
  }

  public List<Service> services() {
    return services;
  }

  public Optional<Member> findMember(String id) {
    return members.stream().filter(m -> m.id().equals(id)).findFirst();
  }

  public Optional<Plan> findPlan(String id) {
    return plans.stream().filter(p -> p.id().equals(id)).findFirst();
  }

  public Optional<Service> findService(String id) {
    return services.stream().filter(s -> s.id().equals(id)).findFirst();
  }

  public Optional<SampleMatch> findSampleQuestion(String questionId) {
    for (Member m : members) {
      for (Member.SampleQuestion q : m.sampleQuestions()) {
        if (q.id().equals(questionId)) return Optional.of(new SampleMatch(m, q));
      }
    }
    return Optional.empty();
  }

  public RecordedBenefits recorded() {
    return recorded;
  }
}
