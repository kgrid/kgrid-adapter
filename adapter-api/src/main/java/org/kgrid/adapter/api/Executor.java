package org.kgrid.adapter.api;

public interface Executor {

  @Deprecated
  default ExecutorResponse execute(Object input, String contentType) {
    throw new UnsupportedOperationException("This Executor type is no longer supported, please use the ClientRequest Class as input");
  }

  default ExecutorResponse execute(ClientRequest request) {
    return execute(request.getBody(), request.getHeaders().firstValue("content-type").get());
  }
}
