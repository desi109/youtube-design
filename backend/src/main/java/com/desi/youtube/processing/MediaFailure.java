package com.desi.youtube.processing;

public class MediaFailure extends RuntimeException {

  public final String code;
  public final boolean retryable;

  public MediaFailure(String code, String message, boolean retryable) {
    super(message);
    this.code = code;
    this.retryable = retryable;
  }
}
