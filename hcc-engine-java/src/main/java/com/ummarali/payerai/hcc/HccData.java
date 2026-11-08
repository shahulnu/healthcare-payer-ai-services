package com.ummarali.payerai.hcc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.hcc.domain.ChartMember;
import com.ummarali.payerai.hcc.domain.HccMap;
import com.ummarali.payerai.hcc.domain.RecordedHcc;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the shared demo data (data/hcc/*.json, copied onto the classpath by the Maven build). A
 * production service would load the full CMS tables by payment year and read charts from retrieval feeds.
 */
@Component
public class HccData {

  private final List<ChartMember> members;
  private final HccMap map;
  private final RecordedHcc recorded;

  public HccData(ObjectMapper mapper) {
    this.members = read(mapper, "hcc/members.json", new TypeReference<>() {});
    this.map = read(mapper, "hcc/hcc-map.json", new TypeReference<>() {});
    this.recorded = read(mapper, "hcc/recorded.json", new TypeReference<>() {});
  }

  private static <T> T read(ObjectMapper mapper, String path, TypeReference<T> type) {
    try (InputStream in = HccData.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing classpath resource " + path + ". Build with Maven so data/hcc is copied.");
      return mapper.readValue(in, type);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + path, e);
    }
  }

  public List<ChartMember> members() {
    return members;
  }

  public Optional<ChartMember> findMember(String id) {
    return members.stream().filter(m -> m.id().equals(id)).findFirst();
  }

  public HccMap map() {
    return map;
  }

  public RecordedHcc recorded() {
    return recorded;
  }
}
