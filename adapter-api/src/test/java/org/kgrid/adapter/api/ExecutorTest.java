package org.kgrid.adapter.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ExecutorTest {

    private static final String INPUT = "input";
    private static final String CONTENT_TYPE = "application/json";
    private static final String ACCEPT = "application/json";
    private static final URI URI = java.net.URI.create("uri");
    private static final String OUTPUT = "output";
    private static final String HTTP_METHOD = "POST";
    private Executor ex;
    private ClientRequest clientRequest;
    private final ClientRequestBuilder clientRequestBuilder = new ClientRequestBuilder();

    @BeforeEach
    void setUp() {
        Map<String, List<String>> headerMap = Map.of(
                "content-type", List.of(CONTENT_TYPE),
                "accept", List.of(ACCEPT)
        );
        HttpHeaders httpHeaders = HttpHeaders.of(headerMap, (k, v) -> true);
        clientRequest = clientRequestBuilder
                .body(INPUT)
                .headers(httpHeaders)
                .url(URI)
                .httpMethod(HTTP_METHOD)
                .build();
        ex = new Executor() {
            @Override
            public Object execute(ClientRequest clientRequest) {
                return OUTPUT;
            }
        };
    }

    @Test
    @DisplayName("Execute using client request returns appropriately")
    void executeUsingRequest() {
        String result = (String) ex.execute(clientRequest);
        assertEquals(OUTPUT, result);
    }

    @Test
    @DisplayName("Executing Legacy Executor with request uses default request handling executor")
    void executeLegacyExecutorUsingRequestHandlingExecutor_UsesDefaultRequestHandling() {
        Executor executor =
                new Executor() {

                    @Override
                    public Object execute(Object input, String contentType) {
                        return OUTPUT;
                    }
                };
        assertEquals(OUTPUT, executor.execute(clientRequest));
    }

    @Test
    @DisplayName("Legacy Execute throws unsupported operation exception")
    void legacyExecute() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            ex.execute(INPUT, CONTENT_TYPE);
        });
        assertEquals("This Executor type is no longer supported, please use the ClientRequest Class as input", exception.getMessage());
    }
}
