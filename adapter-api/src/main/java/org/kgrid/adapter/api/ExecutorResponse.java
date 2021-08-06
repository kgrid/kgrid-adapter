package org.kgrid.adapter.api;

import java.util.List;
import java.util.Map;

public class ExecutorResponse {

    private final Object body;
    private final Map<String, List<String>> headers;

    public Object getBody() {
        return body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public ExecutorResponse(Object body, Map<String, List<String>> headers) {
        this.body = body;
        this.headers = headers;
    }
}
