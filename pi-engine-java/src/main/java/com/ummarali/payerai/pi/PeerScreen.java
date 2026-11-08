package com.ummarali.payerai.pi;

import com.ummarali.payerai.pi.domain.FeatureResult;
import com.ummarali.payerai.pi.domain.Lead;
import com.ummarali.payerai.pi.domain.PeerPoint;
import com.ummarali.payerai.pi.domain.Provider;
import com.ummarali.payerai.pi.domain.Screen;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The detection step, a port of src/lib/pi/detect.ts. Plain statistics, with no model involved:
 * robust peer-group z-scores (median and MAD, so outliers don't hide themselves), risk adjustment
 * for the 99215 share, and one hard rule for implausible billed hours.
 */
public final class PeerScreen {

  private PeerScreen() {}

  private static final double FLAG_Z = 3.5;
  private static final double HIGH_Z = 8;
  private static final double Z_CAP = 12;
  private static final double IMPLAUSIBLE_HOURS = 16;

  // Illustrative unit values, used only to size a lead.
  private static final double LEVEL5_PREMIUM = 55; // 99215 allowed minus 99214 allowed
  private static final double MODIFIER25_EM = 110; // E/M paid alongside a same-day procedure
  private static final double THERAPY_UNIT = 28;
  private static final double THERAPY_VISIT = 95;

  enum Format { PCT, NUM1, NUM2, HOURS, USD }

  /** exposure estimates billing above the peer pattern from the excess over the reference value. */
  record FeatureDef(String id, String label, Function<Provider, Double> value, Format format, BiFunction<Provider, Double, Double> exposure, boolean hasHardRule) {}

  private static final FeatureDef PEAK_DAY =
      new FeatureDef("peak_day_hours", "Busiest day, billed time implied by codes", Provider::peakDayHours, Format.HOURS, null, true);

  private static final Map<String, List<FeatureDef>> FEATURES = Map.of(
      "Family Medicine", List.of(
          new FeatureDef("level5_share", "Established visits billed at 99215", Provider::level5Share, Format.PCT,
              (p, x) -> x * p.encounters() * LEVEL5_PREMIUM, false),
          new FeatureDef("modifier25_rate", "Visits billed with modifier 25", Provider::modifier25Rate, Format.PCT,
              (p, x) -> x * p.encounters() * MODIFIER25_EM, false),
          PEAK_DAY,
          new FeatureDef("allowed_per_patient", "Allowed amount per patient", Provider::allowedPerPatient, Format.USD, null, false)),
      "Physical Therapy", List.of(
          new FeatureDef("units_per_visit", "Timed units per visit", Provider::unitsPerVisit, Format.NUM2,
              (p, x) -> x * p.encounters() * THERAPY_UNIT, false),
          new FeatureDef("four_plus_unit_share", "Visits with 4 or more timed units", Provider::fourPlusUnitShare, Format.PCT, null, false),
          new FeatureDef("visits_per_patient", "Visits per patient", Provider::visitsPerPatient, Format.NUM1,
              (p, x) -> x * (p.patients() == null ? 0 : p.patients()) * THERAPY_VISIT, false),
          new FeatureDef("modifier59_rate", "Visits billed with modifier 59", Provider::modifier59Rate, Format.PCT, null, false),
          PEAK_DAY));

  /** Same specialty order as the TypeScript engine. */
  private static final List<String> SPECIALTIES = List.of("Family Medicine", "Physical Therapy");

  /**
   * Fixed-point text with the same rounding as JavaScript's toFixed: exact binary value, ties away
   * from zero. String.format can differ on values such as 1.005, so it isn't used here.
   */
  static String fixed(double x, int digits) {
    return new BigDecimal(x).setScale(digits, RoundingMode.HALF_UP).toPlainString();
  }

  static double round(double x, int digits) {
    return Double.parseDouble(fixed(x, digits));
  }

  static String format(double v, Format f) {
    return switch (f) {
      case PCT -> fixed(v * 100, 1) + "%";
      case NUM1 -> fixed(v, 1);
      case NUM2 -> fixed(v, 2);
      case HOURS -> fixed(v, 1) + " hours";
      case USD -> "$" + String.format(Locale.ROOT, "%,d", Math.round(v));
    };
  }

  static double median(List<Double> xs) {
    List<Double> s = new ArrayList<>(xs);
    Collections.sort(s);
    int m = s.size() >> 1;
    return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2;
  }

  /** Robust z-score: 0.6745 x (x - median) / MAD. */
  static double robustZ(double x, double med, double mad) {
    return mad == 0 ? 0 : (0.6745 * (x - med)) / mad;
  }

  /** Theil-Sen line: the median of all pairwise slopes, so outliers can't tilt it. */
  static Screen.Line theilSen(List<Double> xs, List<Double> ys) {
    List<Double> slopes = new ArrayList<>();
    for (int i = 0; i < xs.size(); i++) {
      for (int j = i + 1; j < xs.size(); j++) {
        if (!xs.get(j).equals(xs.get(i))) slopes.add((ys.get(j) - ys.get(i)) / (xs.get(j) - xs.get(i)));
      }
    }
    double slope = median(slopes);
    List<Double> offsets = new ArrayList<>();
    for (int i = 0; i < xs.size(); i++) offsets.add(ys.get(i) - slope * xs.get(i));
    return new Screen.Line(slope, median(offsets));
  }

  private record Stat(FeatureDef def, double med, double mad, Screen.Line line, double residMed, double residMad) {}

  public static Screen run(String period, List<Provider> providers) {
    Map<String, Map<String, List<PeerPoint>>> peers = new LinkedHashMap<>();
    List<Lead> all = new ArrayList<>();
    Screen.Line level5Line = new Screen.Line(0, 0);

    for (String specialty : SPECIALTIES) {
      List<Provider> group = providers.stream().filter(p -> p.specialty().equals(specialty)).toList();
      Map<String, List<PeerPoint>> groupPeers = new LinkedHashMap<>();
      peers.put(specialty, groupPeers);

      List<Stat> stats = new ArrayList<>();
      for (FeatureDef def : FEATURES.get(specialty)) {
        List<Double> values = group.stream().map(def.value()).toList();
        double med = median(values);
        double mad = median(values.stream().map(v -> Math.abs(v - med)).toList());
        groupPeers.put(def.id(), group.stream().map(p -> new PeerPoint(p.id(), def.value().apply(p), p.patientRiskScore())).toList());

        if (!def.id().equals("level5_share")) {
          stats.add(new Stat(def, med, mad, null, 0, 0));
          continue;
        }
        // Sicker panels justify more high-level visits, so judge this feature on its residual.
        Screen.Line line = theilSen(group.stream().map(Provider::patientRiskScore).toList(), values);
        level5Line = line;
        List<Double> resid = group.stream().map(p -> def.value().apply(p) - (line.intercept() + line.slope() * p.patientRiskScore())).toList();
        double residMed = median(resid);
        double residMad = median(resid.stream().map(r -> Math.abs(r - residMed)).toList());
        stats.add(new Stat(def, med, mad, line, residMed, residMad));
      }

      for (Provider p : group) all.add(screenProvider(p, specialty, stats));
    }

    List<Lead> leads = all.stream()
        .filter(l -> !l.tier().equals("none"))
        .sorted(Comparator.comparingLong(Lead::score).reversed().thenComparing(Comparator.comparingLong(Lead::exposure).reversed()))
        .toList();
    List<Lead> cleared = all.stream().filter(l -> l.tier().equals("none") && l.clearedByAdjustment()).toList();
    return new Screen(period, providers.size(), leads, cleared, peers, level5Line);
  }

  private static Lead screenProvider(Provider p, String specialty, List<Stat> stats) {
    double exposure = 0;
    List<FeatureResult> features = new ArrayList<>();
    for (Stat s : stats) {
      FeatureDef def = s.def();
      double value = def.value().apply(p);
      double rawZ = robustZ(value, s.med(), s.mad());
      Double expected = s.line() == null ? null : s.line().intercept() + s.line().slope() * p.patientRiskScore();
      double z = expected == null ? rawZ : robustZ(value - expected, s.residMed(), s.residMad());
      boolean hardRule = def.hasHardRule() && value > IMPLAUSIBLE_HOURS;
      boolean flagged = z >= FLAG_Z || hardRule;
      if (flagged && def.exposure() != null) {
        exposure += Math.max(0, def.exposure().apply(p, value - (expected == null ? s.med() : expected)));
      }
      features.add(new FeatureResult(def.id(), def.label(), value, format(value, def.format()), s.med(), format(s.med(), def.format()), expected,
          expected == null ? null : format(expected, def.format()), round(z, 1), round(rawZ, 1), flagged, hardRule));
    }

    List<FeatureResult> flags = features.stream().filter(FeatureResult::flagged).toList();
    double sum = flags.stream().mapToDouble(f -> Math.min(f.z(), Z_CAP)).sum();
    String tier = flags.isEmpty() ? "none"
        : flags.stream().anyMatch(f -> f.hardRule() || f.z() >= HIGH_Z) || flags.size() >= 2 ? "high" : "medium";
    long score = flags.isEmpty() ? 0 : Math.round(100 * (1 - Math.exp(-sum / 15)));
    boolean cleared = features.stream().anyMatch(f -> f.expected() != null && f.rawZ() >= FLAG_Z && f.z() < FLAG_Z);
    return new Lead(p.id(), specialty, p.encounters(), tier, score, features, flags.stream().map(FeatureResult::label).toList(),
        Math.round(exposure / 100) * 100, cleared);
  }
}
