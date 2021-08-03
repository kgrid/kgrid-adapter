package org.kgrid.adapter.api;

public interface Executor {

  @Deprecated
  default Object execute(Object input, String contentType) {
    throw new UnsupportedOperationException("This Executor type is no longer supported, please use the ClientRequest Class as input");
  }

  default Object execute(ClientRequest request) {
    return execute(request.getBody(), "application/json");
  }
}
