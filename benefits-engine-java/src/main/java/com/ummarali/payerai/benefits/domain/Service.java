package com.ummarali.payerai.benefits.domain;

import java.util.Map;

/** A service the cost calculator knows, with illustrative allowed amounts per network. */
public record Service(String id, String name, String code, String category, Map<String, Double> allowed) {}
