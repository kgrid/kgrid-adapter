package org.kgrid.adapter.api;

import java.util.HashMap;

public interface Executor {

  @Deprecated
  default Object execute(Object input, String contentType) {
    throw new UnsupportedOperationException("This Executor type is no longer supported, please use the ClientRequest Class as input");
  }

  default ExecutorResponse execute(ClientRequest request) {
    Object result = execute(request.getBody(), request.getHeaders().firstValue("content-type").get());
    return new ExecutorResponse(result,new HashMap<>());
  }
}
