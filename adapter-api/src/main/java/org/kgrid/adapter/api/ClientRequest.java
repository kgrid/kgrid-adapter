package org.kgrid.adapter.api;

public class ClientRequest {

  public Object getBody() {
    return body;
  }

  private final Object body;
  private final String contentType;

  public ClientRequest(Object body) {
    this(body, "application/json");
  }

  public ClientRequest(Object body, String contentType) {
    this.body = body;
    this.contentType = contentType;
  }
}
