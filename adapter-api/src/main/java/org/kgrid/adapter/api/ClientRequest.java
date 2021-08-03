package org.kgrid.adapter.api;

import java.net.URI;

public class ClientRequest {

  private final Object body;
  private final String contentType;
  private final URI url;

  public ClientRequest(Object body, String contentType, URI url) {
    this.body = body;
    this.contentType = contentType;
    this.url = url;
  }

  public Object getBody() {
    return body;
  }

  public String getContentType() {
    return contentType;
  }

  public URI getUrl() {
    return url;
  }
}
