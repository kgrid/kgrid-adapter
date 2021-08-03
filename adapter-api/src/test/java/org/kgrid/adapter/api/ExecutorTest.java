package org.kgrid.adapter.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

class ExecutorTest {

    public static final String INPUT = "input";
    public static final String CONTENT_TYPE = "application/json";
    public static final URI URI = java.net.URI.create("uri");
    public static final String OUTPUT = "output";
    private Executor ex;
    private ClientRequest clientRequest;

    @BeforeEach
    void setUp() {
        clientRequest = new ClientRequest(INPUT, CONTENT_TYPE, URI);
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
        String result = (String)ex.execute(clientRequest);
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
