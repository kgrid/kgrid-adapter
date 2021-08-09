package org.kgrid.adapter.api;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

class ExecutorTest {

    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String REQUEST_CONTENT_TYPE = "application/json";
    private static final List<String> REQUEST_ACCEPT = List.of("application/json","text/csv");
    private static final String RESPONSE_CONTENT_TYPE = "text/csv";
    private static final URI URI = java.net.URI.create("uri");
    private static final String HTTP_METHOD = "POST";
    private ClientRequest clientRequest;
    private final ClientRequestBuilder clientRequestBuilder = new ClientRequestBuilder();
    private Map<String, List<String>> responseHeaders;

    @BeforeEach
    void setUp() {
        Map<String, List<String>> requestHeaders = Map.of(
                "content-type", List.of(REQUEST_CONTENT_TYPE),
                "accept", REQUEST_ACCEPT
        );
        responseHeaders = Map.of(
                "content-type", List.of(RESPONSE_CONTENT_TYPE)
        );
        clientRequest = clientRequestBuilder
                .body(INPUT)
                .headers(requestHeaders)
                .url(URI)
                .httpMethod(HTTP_METHOD)
                .build();

    }

    @Test
    @DisplayName("Execute using client request returns appropriately with a String as the result")
    void executeUsingRequest_StringResult() {
        Executor executor = new Executor() {
            @Override
            public ExecutorResponse execute(ClientRequest clientRequest) {
                return new ExecutorResponse(OUTPUT, responseHeaders);
            }
        };
        ExecutorResponse executorResponse = executor.execute(clientRequest);
        assertEquals(OUTPUT, executorResponse.getBody());
        assertEquals(RESPONSE_CONTENT_TYPE, (executorResponse.getHeaders().get("content-type")).get(0));
    }

    @Test
    @DisplayName("Execute using client request returns appropriately with a primitive as the result")
    void executeUsingRequest_PrimitiveResult() {
        int resultBody = 123;
        Executor executor = new Executor() {
            @Override
            public ExecutorResponse execute(ClientRequest clientRequest) {
                return new ExecutorResponse(resultBody, responseHeaders);
            }
        };
        ExecutorResponse executorResponse = executor.execute(clientRequest);
        assertEquals(resultBody, executorResponse.getBody());
        assertEquals(RESPONSE_CONTENT_TYPE, (executorResponse.getHeaders().get("content-type")).get(0));
    }

    @Test
    @DisplayName("Execute using client request returns appropriately with an object as the result")
    void executeUsingRequest_ObjectResult() {
        final List<String> resultBody = List.of(OUTPUT, INPUT);
        Executor executor = new Executor() {
            @Override
            public ExecutorResponse execute(ClientRequest clientRequest) {
                return new ExecutorResponse(resultBody, responseHeaders);
            }
        };
        ExecutorResponse executorResponse = executor.execute(clientRequest);
        assertEquals(resultBody, executorResponse.getBody());
        assertEquals(RESPONSE_CONTENT_TYPE, (executorResponse.getHeaders().get("content-type")).get(0));
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
        ExecutorResponse result = executor.execute(clientRequest);
        assertEquals(OUTPUT, result.getBody());
    }

    @Test
    @DisplayName("Legacy Execute throws unsupported operation exception")
    void legacyExecute() {
        Executor executor =
                new Executor() {
                    @Override
                    public ExecutorResponse execute(ClientRequest clientRequest) {
                        return new ExecutorResponse(OUTPUT, responseHeaders);
                    }
                };
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> executor.execute(INPUT, REQUEST_CONTENT_TYPE));
        assertEquals("This Executor type is no longer supported, please use the ClientRequest Class as input", exception.getMessage());
    }
}
