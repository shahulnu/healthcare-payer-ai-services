package com.ummarali.payerai.appeals.domain;

import java.util.Map;

/** Plain-language meanings of the X12 group, CARC and RARC codes in the demo, from data/appeals/codes.json. */
public record CodeBook(Map<String, String> groupCodes, Map<String, String> carc, Map<String, String> rarc) {

  /** Meaning of a denial code such as "CO-50" (looked up as CARC 50) or "M15" (RARC). */
  public String describe(String code) {
    String meaning = code.contains("-") ? carc.get(code.substring(code.indexOf('-') + 1)) : rarc.get(code);
    return meaning != null ? meaning : "Code not in the demo dictionary.";
  }
}
