package org.kgrid.adapter.api;

import java.net.URI;
import java.net.http.HttpHeaders;

public class ClientRequestBuilder {
    private Object body;
    private URI url;
    private HttpHeaders headers;
    private String httpMethod;

    public ClientRequestBuilder body(Object body) {
        this.body = body;
        return this;
    }

    public ClientRequestBuilder url(URI url) {
        this.url = url;
        return this;
    }

    public ClientRequestBuilder headers(HttpHeaders headers) {
        this.headers = headers;
        return this;
    }

    public ClientRequestBuilder httpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
        return this;
    }

    public ClientRequest build() {
        return new ClientRequest(body, url, headers, httpMethod);
    }
}
