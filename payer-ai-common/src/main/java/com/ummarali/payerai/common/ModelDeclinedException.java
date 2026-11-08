package com.ummarali.payerai.common;

/** The model (and its server-side fallback) declined the request. */
public class ModelDeclinedException extends RuntimeException {
  public ModelDeclinedException(String message) {
    super(message);
  }
}
