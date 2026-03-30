package io.github.rakheendama.starter.portal;

public class TooManyRequestsException extends RuntimeException {

  public TooManyRequestsException(String message) {
    super(message);
  }
}
