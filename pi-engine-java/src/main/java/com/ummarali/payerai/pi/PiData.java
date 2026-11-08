package com.ummarali.payerai.pi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ummarali.payerai.pi.domain.Cohort;
import com.ummarali.payerai.pi.domain.Lead;
import com.ummarali.payerai.pi.domain.RecordedPi;
import com.ummarali.payerai.pi.domain.Screen;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the shared cohort (data/pi/*.json, copied onto the classpath by the Maven build) and screens
 * it once at startup. A production service would run the screen as a batch job on the claims warehouse.
 */
@Component
public class PiData {

  private final Screen screen;
  private final RecordedPi recorded;

  public PiData(ObjectMapper mapper) {
    Cohort cohort = read(mapper, "pi/providers.json", new TypeReference<>() {});
    this.screen = PeerScreen.run(cohort.period(), cohort.providers());
    this.recorded = read(mapper, "pi/recorded.json", new TypeReference<>() {});
  }

  private static <T> T read(ObjectMapper mapper, String path, TypeReference<T> type) {
    try (InputStream in = PiData.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing classpath resource " + path + ". Build with Maven so data/pi is copied.");
      return mapper.readValue(in, type);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + path, e);
    }
  }

  public Screen screen() {
    return screen;
  }

  public Optional<Lead> findLead(String id) {
    return screen.leads().stream().filter(l -> l.providerId().equals(id)).findFirst();
  }

  public RecordedPi recorded() {
    return recorded;
  }
}
