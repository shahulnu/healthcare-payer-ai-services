package com.ummarali.payerai.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Records each workflow step's duration and a one-line detail for the run trace. */
public final class Trace {

  private final List<TraceStep> steps = new ArrayList<>();

  public <T> T time(String name, Supplier<T> fn, Function<T, String> detail) {
    long t0 = System.nanoTime();
    T r = fn.get();
    steps.add(new TraceStep(name, Math.round((System.nanoTime() - t0) / 1_000_000.0), detail.apply(r)));
    return r;
  }

  public List<TraceStep> steps() {
    return List.copyOf(steps);
  }
}
