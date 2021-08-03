package org.kgrid.adapter.api;

import java.net.URI;
import java.util.Map;

public class ClientRequestBuilder {
    private Object body;
    private URI url;
    private Map<String, String> headers;
    private String httpMethod;

    public ClientRequestBuilder body(Object body) {
        this.body = body;
        return this;
    }

    public ClientRequestBuilder url(URI url) {
        this.url = url;
        return this;
    }

    public ClientRequestBuilder headers(Map<String, String> headers) {
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
