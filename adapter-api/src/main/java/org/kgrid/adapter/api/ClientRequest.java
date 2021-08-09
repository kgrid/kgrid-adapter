package org.kgrid.adapter.api;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

public class ClientRequest {

    private Object body;
    private URI url;
    private HttpHeaders headers;
    private String httpMethod;

    public ClientRequest(Object body, URI url, HttpHeaders headers, String httpMethod) {
        this.body = body;
        this.url = url;
        this.headers = headers;
        this.httpMethod = httpMethod;
    }

    public ClientRequest(Builder builder) {
        this.body = builder.body;
        this.url = builder.url;
        this.headers = builder.headers;
        this.httpMethod = builder.httpMethod;
    }

    public Object getBody() {
        return body;
    }

    public URI getUrl() {
        return url;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public static class Builder {

        private Object body;
        private URI url;
        private HttpHeaders headers;
        private String httpMethod;

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder headers(Map<String, List<String>> headers) {
            this.headers = HttpHeaders.of(headers, (k,v) -> true);
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public ClientRequest build() {
            return new ClientRequest(this);
        }
    }

}
