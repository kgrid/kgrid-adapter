package org.kgrid.adapter.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpHeaders;

public class AdapterResponse<T> {

    private final T body;
    private final HttpHeaders headers;
    private final JsonNode metadata;

    public T getBody() {
        return body;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public AdapterResponse(T body, HttpHeaders headers, JsonNode metadata) {
        this.body = body;
        this.headers = headers;
        this.metadata = metadata;
    }
}
