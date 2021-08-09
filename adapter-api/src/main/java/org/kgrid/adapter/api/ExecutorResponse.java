package org.kgrid.adapter.api;

import java.util.List;
import java.util.Map;

public class ExecutorResponse {

    private final Object body;
    private final Map<String, List<String>> headers;
    private final ClientRequest clientRequest;

    public Object getBody() {
        return body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public ClientRequest getClientRequest() {
        return clientRequest;
    }

    public ExecutorResponse(Object body, Map<String, List<String>> headers, ClientRequest clientRequest) {
        this.body = body;
        this.headers = headers;
        this.clientRequest = clientRequest;
    }
}
