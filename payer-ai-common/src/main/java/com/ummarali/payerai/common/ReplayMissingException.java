package com.ummarali.payerai.common;

/** Replay was requested for an item that has no recorded model output. */
public class ReplayMissingException extends RuntimeException {
  public ReplayMissingException(String message) {
    super(message);
  }
}
