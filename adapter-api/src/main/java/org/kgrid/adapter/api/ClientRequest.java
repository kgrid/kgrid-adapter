package org.kgrid.adapter.api;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Map;

public class ClientRequest {

  private  Object body;
  private  URI url;
  private HttpHeaders headers;
  private  String httpMethod;

  public ClientRequest(Object body, URI url, HttpHeaders headers, String httpMethod ) {
    this.body = body;
    this.url = url;
    this.headers = headers;
    this.httpMethod = httpMethod;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(Object body) {
    this.body = body;
  }

  public URI getUrl() {
    return url;
  }

  public void setUrl(URI url) {
    this.url = url;
  }

  public HttpHeaders getHeaders() {
    return headers;
  }

  public void setHeaders(HttpHeaders headers){
    this.headers = headers;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod){
    this.httpMethod = httpMethod;
  }
}
