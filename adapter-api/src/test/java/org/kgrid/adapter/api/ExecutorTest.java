package org.kgrid.adapter.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutorTest {

  private Executor ex;

  @BeforeEach
  void setUp() {
    ex = new Executor() {
      public Object execute(Object input, String contentType) {
        return input;
      }
    };
  }

  @Test
  void legacyExecute() {
    final String result = (String) ex.execute("hello, bob", "Content-Type: application/json");
    assertEquals("hello, bob", result);
  }

  @Test
  void executeUsingRequest() {
    ClientRequest request = new ClientRequest("hello, bob", null, null);
    final String result = (String) ex.execute(request);
    assertEquals("hello, bob", result);
  }

  @Test
  void executeUsingRequestHandlingExecutor() {
    RequestHandlingExecutor rhex = new RequestHandlingExecutor() {
      @Override
      public Object execute(ClientRequest request) {
        return request.getBody();
      }
    };

    ClientRequest request = new ClientRequest("Hello, Bob", null, null);
    assertEquals("Hello, Bob", rhex.execute(request));
  }

  @Test
  void executeLegacyExecutorUsingRequestHandlingExecutor() {
    Executor rhex =
        new Executor() {

          @Override
          public Object execute(Object input, String contentType) {
            return input;
          }
        };


    ClientRequest request = new ClientRequest("Hello, Bob", null, null);
    assertEquals("Hello, Bob", rhex.execute(request));
  }

}
