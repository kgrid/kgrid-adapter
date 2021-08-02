package org.kgrid.adapter.api;

@Deprecated
public interface Executor extends RequestHandlingExecutor{

  Object execute(Object input, String contentType);

  default Object execute(ClientRequest request) {
    return execute(request.getBody(), "application/json");
  }
}
