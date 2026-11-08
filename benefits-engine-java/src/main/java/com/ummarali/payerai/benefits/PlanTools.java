package com.ummarali.payerai.benefits;

import com.fasterxml.jackson.databind.JsonNode;
import com.ummarali.payerai.benefits.domain.Member;
import com.ummarali.payerai.benefits.domain.Plan;
import com.ummarali.payerai.benefits.domain.Service;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The two tools the model can call before it answers. Deterministic, so replay can re-run recorded
 * calls and get the same outputs. Same behavior as runTool() in src/lib/benefits/engine.ts.
 */
final class PlanTools {

  private PlanTools() {}

  static Object run(BenefitsData data, Plan plan, Member member, String name, JsonNode input) {
    return switch (name) {
      case "get_plan_sections" -> planSections(plan, input);
      case "estimate_cost" -> estimateCost(data, plan, member, input);
      default -> Map.of("error", "Unknown tool " + name + ".");
    };
  }

  private static Object planSections(Plan plan, JsonNode input) {
    JsonNode ids = input == null ? null : input.get("section_ids");
    if (ids == null || !ids.isArray()) return Map.of("error", "section_ids must be a list of section ids.");
    List<Map<String, Object>> out = new ArrayList<>();
    for (JsonNode idNode : ids) {
      String id = idNode.asText();
      Optional<Plan.PlanSection> s = plan.sections().stream().filter(x -> x.id().equals(id)).findFirst();
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", id);
      if (s.isPresent()) {
        m.put("doc", s.get().doc());
        m.put("page", s.get().page());
        m.put("title", s.get().title());
        m.put("text", s.get().text());
      } else {
        m.put("error", "No section with this id.");
      }
      out.add(m);
    }
    return out;
  }

  private static Object estimateCost(BenefitsData data, Plan plan, Member member, JsonNode input) {
    String serviceId = input == null || input.get("service_id") == null ? null : input.get("service_id").asText();
    String network = input == null || input.get("network") == null ? null : input.get("network").asText();
    Optional<Service> service = serviceId == null ? Optional.empty() : data.findService(serviceId);
    if (service.isEmpty() || !("in".equals(network) || "out".equals(network))) return Map.of("error", "Unknown service_id or network.");
    return CostCalculator.estimate(plan, member, service.get(), network);
  }
}
