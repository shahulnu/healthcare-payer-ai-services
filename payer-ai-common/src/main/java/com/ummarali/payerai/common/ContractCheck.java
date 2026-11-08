package com.ummarali.payerai.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Compares an engine's output with a golden file in contract-tests/, the saved JSON the TypeScript
 * site's engines also have to reproduce. Same rules as scripts/contracts.mjs in the site project:
 * numbers compare by value, a missing field equals null, and fields that vary between runs or engines
 * are left out.
 */
public final class ContractCheck {

  private static final List<String> VARIABLE_FIELDS = List.of("engine", "generatedAt", "notice", "usage");
  private static final double TOLERANCE = 1e-9;

  /** Works from a module's folder, which is where Maven runs its tests. */
  private static final Path ROOT = Path.of("..", "contract-tests");

  private ContractCheck() {}

  /** The golden files for one app, such as "pa", or an error if there are none. */
  public static List<Path> files(String app) {
    Path dir = ROOT.resolve(app);
    try (Stream<Path> s = Files.list(dir)) {
      List<Path> files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
      if (files.isEmpty()) throw new IllegalStateException("No contract files in " + dir + ".");
      return files;
    } catch (IOException e) {
      throw new UncheckedIOException("No contract files at " + dir + ". Run npm run contracts:sync in the site project.", e);
    }
  }

  public static String idOf(Path file) {
    return file.getFileName().toString().replaceAll("\\.json$", "");
  }

  /** The differences between a golden file and an engine's output. Empty means the contract holds. */
  public static List<String> differences(ObjectMapper mapper, Path golden, Object actual) {
    try {
      List<String> out = new ArrayList<>();
      diff(normalize(mapper.readTree(golden.toFile())), normalize(mapper.valueToTree(actual)), golden.getFileName().toString(), out);
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + golden, e);
    }
  }

  private static JsonNode normalize(JsonNode node) {
    if (!(node instanceof ObjectNode obj)) return node;
    VARIABLE_FIELDS.forEach(obj::remove);
    if (obj.get("trace") instanceof ArrayNode trace) {
      trace.forEach(step -> {
        if (step instanceof ObjectNode s) s.remove("ms");
      });
    }
    return obj;
  }

  private static boolean isNull(JsonNode n) {
    return n == null || n.isNull() || n.isMissingNode();
  }

  private static void diff(JsonNode a, JsonNode b, String path, List<String> out) {
    if (isNull(a) || isNull(b)) {
      if (isNull(a) != isNull(b)) out.add(path + ": expected " + a + ", got " + b);
    } else if (a.isNumber() && b.isNumber()) {
      if (Math.abs(a.doubleValue() - b.doubleValue()) > TOLERANCE) out.add(path + ": expected " + a + ", got " + b);
    } else if (a.isArray() && b.isArray()) {
      if (a.size() != b.size()) {
        out.add(path + ": expected " + a.size() + " items, got " + b.size());
      } else {
        for (int i = 0; i < a.size(); i++) diff(a.get(i), b.get(i), path + "[" + i + "]", out);
      }
    } else if (a.isObject() && b.isObject()) {
      Set<String> keys = new LinkedHashSet<>();
      a.fieldNames().forEachRemaining(keys::add);
      b.fieldNames().forEachRemaining(keys::add);
      for (String k : keys) diff(a.get(k), b.get(k), path + "." + k, out);
    } else if (!a.equals(b)) {
      out.add(path + ": expected " + a + ", got " + b);
    }
  }
}
