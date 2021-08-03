package org.kgrid.adapter.api;

import java.net.URI;
import java.util.Map;

public class ClientRequest {

  private  Object body;
  private  URI url;
  private  Map<String, String> headers;
  private  String httpMethod;

  public ClientRequest(Object body, URI url, Map<String,String> headers, String httpMethod ) {
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

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String,String> headers){
    this.headers = headers;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod){
    this.httpMethod = httpMethod;
  }
}
