package org.kgrid.adapter.api;

public interface Executor {

  @Deprecated
  default Object execute(Object input, String contentType) {
    throw new UnsupportedOperationException("Don't use me.");
  }

  default Object execute(ClientRequest request) {
    return execute(request.getBody(), "application/json");
  }
}
