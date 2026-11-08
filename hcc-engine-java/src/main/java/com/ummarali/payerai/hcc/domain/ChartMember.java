package com.ummarali.payerai.hcc.domain;

import java.util.List;

/** A synthetic member with submitted codes and chart documents, from data/hcc/members.json. */
public record ChartMember(String id, String label, String plan, String teaser, List<String> submitted, List<ChartDocument> documents) {}
