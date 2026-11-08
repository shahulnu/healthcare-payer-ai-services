package com.ummarali.payerai.common;

/** One row of a run trace: what ran, how long it took, and a one-line result. */
public record TraceStep(String name, long ms, String detail) {}
