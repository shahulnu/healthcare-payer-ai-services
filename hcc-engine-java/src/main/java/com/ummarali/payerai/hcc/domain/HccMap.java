package com.ummarali.payerai.hcc.domain;

import java.util.List;
import java.util.Map;

/** The ICD-10 to HCC lookup and hierarchy from data/hcc/hcc-map.json. The model never assigns an HCC. */
public record HccMap(String model, String note, Map<String, String> hccNames, Map<String, CodeEntry> codes, Map<String, List<String>> hierarchies) {

  /** activeOnly: the code means the condition is active now, so "history of" language can't support it. */
  public record CodeEntry(String description, String hcc, Boolean activeOnly) {}
}
