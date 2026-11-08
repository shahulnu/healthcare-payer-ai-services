package com.ummarali.payerai.benefits;

import com.ummarali.payerai.benefits.domain.CostEstimate;
import com.ummarali.payerai.benefits.domain.Member;
import com.ummarali.payerai.benefits.domain.Plan;
import com.ummarali.payerai.benefits.domain.Plan.CostRule;
import com.ummarali.payerai.benefits.domain.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The plan's cost calculator, a port of src/lib/benefits/calculator.ts. Deterministic: it applies
 * the member's remaining deductible, coinsurance or copay, and remaining out-of-pocket limit to the
 * service's allowed amount. The model asks for estimates through the estimate_cost tool but never
 * does this math itself.
 */
public final class CostCalculator {

  private CostCalculator() {}

  static double round2(double n) {
    return Math.round(n * 100) / 100.0;
  }

  private static boolean on(Boolean flag) {
    return Boolean.TRUE.equals(flag);
  }

  public static CostEstimate estimate(Plan plan, Member member, Service service, String network) {
    Map<String, CostRule> byNetwork = plan.rules().get(service.category());
    CostRule rule = byNetwork != null && byNetwork.get(network) != null ? byNetwork.get(network) : CostRule.NOT_COVERED;
    double allowed = service.allowed().get(network);
    List<String> notes = new ArrayList<>();

    if ("not_covered".equals(rule.kind())) {
      notes.add("out".equals(network) && "HMO".equals(plan.type())
          ? "Not covered out of network except emergencies. The member pays the full cost, and it doesn't count toward the deductible or out-of-pocket limit."
          : "Not a covered service. The member pays the full cost.");
      return new CostEstimate(service.id(), service.name(), network, false, allowed, 0, 0, 0, allowed, 0, notes);
    }

    // Emergency care out of network is paid at the in-network level (No Surprises Act protections).
    String acc = "emergency".equals(service.category()) ? "in" : network;
    Double deductibleLimit = plan.limits().deductible().get(acc);
    Double oopLimit = plan.limits().oopMax().get(acc);
    double deductible = deductibleLimit == null ? 0 : deductibleLimit;
    double oopMax = oopLimit == null ? Double.POSITIVE_INFINITY : oopLimit;
    double dedRemaining = Math.max(0, deductible - member.accumulators().deductibleMet().getOrDefault(acc, 0.0));
    double oopRemaining = Math.max(0, oopMax - member.accumulators().oopMet().getOrDefault(acc, 0.0));

    double deductiblePortion = 0;
    double coinsurancePortion = 0;
    double copay = 0;
    if ("copay".equals(rule.kind())) {
      copay = Math.min(rule.amount(), allowed);
    } else if ("coinsurance".equals(rule.kind())) {
      deductiblePortion = Math.min(allowed, dedRemaining);
      coinsurancePortion = (allowed - deductiblePortion) * rule.rate();
    }
    double memberPays = deductiblePortion + coinsurancePortion + copay;
    if (memberPays > oopRemaining) {
      notes.add(String.format(Locale.ROOT, "Capped at the remaining out-of-pocket limit of $%.2f.", oopRemaining));
      memberPays = oopRemaining;
    }

    if (!"no_charge".equals(rule.kind())) {
      if (on(rule.priorAuth())) notes.add("Prior authorization is required.");
      if (on(rule.referral())) notes.add("A referral from the primary care provider is required.");
      if (rule.visitLimit() != null) notes.add("Limited to " + rule.visitLimit() + " visits per calendar year.");
    }
    if ("out".equals(network) && "PPO".equals(plan.type()) && !"emergency".equals(service.category())) {
      notes.add("The provider may also balance bill the difference between their charge and the allowed amount.");
    }

    return new CostEstimate(service.id(), service.name(), network, true, allowed, round2(deductiblePortion),
        round2(coinsurancePortion), round2(copay), round2(memberPays), round2(allowed - memberPays), notes);
  }
}
