package org.kgrid.adapter.api;

import org.springframework.http.HttpStatus;

public class AdapterException extends RuntimeException {

  private HttpStatus status;

  public AdapterException(String message, Throwable cause, HttpStatus status) {
    super(message, cause);
    this.status = status;
  }

  public AdapterException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }
}
