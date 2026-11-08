package com.ummarali.payerai.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedactorTest {

  @Test
  void removesIdentifiersAndCountsThem() {
    var r = Redactor.redact("""
        Patient: Dana Whitfield   DOB: 03/14/1972   MRN: 00482231
        Callback: (555) 201-4478
        Started physical therapy on 08/10/2026.""");

    assertThat(r.text()).contains("Patient: [NAME]", "DOB: [DOB]", "MRN: [MRN]", "Callback: [PHONE]");
    assertThat(r.text()).doesNotContain("Whitfield", "03/14/1972", "00482231", "201-4478");
    assertThat(r.counts()).containsEntry("Name", 1).containsEntry("Date of birth", 1).containsEntry("MRN", 1).containsEntry("Phone", 1);
  }

  @Test
  void leavesClinicalDatesAndCodesAlone() {
    var r = Redactor.redact("2026-08-10 to 2026-09-30  97110 x14  Therapeutic exercise  M54.16");
    assertThat(r.text()).isEqualTo("2026-08-10 to 2026-09-30  97110 x14  Therapeutic exercise  M54.16");
    assertThat(r.counts()).isEmpty();
  }
}
