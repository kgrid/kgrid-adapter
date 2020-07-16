package org.kgrid.adapter.v8;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.Executor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JsV8AdapterTest {

  Adapter adapter;
  ObjectNode deploymentSpec;

  @Mock ActivationContext activationContext;

  @Before
  public void setUp() {
    adapter = new JsV8Adapter();
    adapter.initialize(activationContext);
    deploymentSpec = new ObjectMapper().createObjectNode();
    deploymentSpec.put("function", "hello");
    deploymentSpec.put("artifact", "src/welcome.js");
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn("function hello(name){ return 'Hello, ' +name;}".getBytes());
  }

  @Test
  public void statusIsDownNoEngine() {
    // Starting with uninitialized adapter
    adapter = new JsV8Adapter();
    assertEquals("DOWN", adapter.status());
  }

  @Test
  public void initializeSucceeds() {
    adapter.initialize(
        new ActivationContext() {
          @Override
          public Executor getExecutor(String key) {
            return null;
          }

          @Override
          public byte[] getBinary(String pathToBinary) {
            return new byte[0];
          }

          @Override
          public String getProperty(String key) {
            return null;
          }
        });
    assertEquals("UP", adapter.status());
  }

  @Test
  public void returnsValidHelloWorldExecutor() {

    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    assertEquals("Hello, Steve", ex.execute("\"Steve\"").toString());
  }

  @Test
  public void returnsValidHelloWorldExecutorForBob() {

    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    assertEquals("Hello, Bob", ex.execute("\"Bob\"").toString());
  }

  @Test
  public void executorCallsFunctionFromDeploymentSpec() throws Exception {
    deploymentSpec.put("function", "goodbye");
    when(activationContext.getBinary(anyString()))
        .thenReturn("function goodbye(name){ return 'Goodbye, ' + name;}".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Object helloResult = ex.execute("\"Bob\"");
    assertEquals("Goodbye, Bob", helloResult);
  }

  @Test
  public void activateUsesBinarySpecifiedInDeploymentSpec() {
    deploymentSpec.put("function", "fools");
    deploymentSpec.put("artifact", "src/tolkien.js");

    when(activationContext.getBinary(Paths.get("hello-world/src/tolkien.js").toString()))
        .thenReturn(
            "function fools(name){ return 'Fly you fools, especially you ' +name;}".getBytes());

    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Object result = ex.execute("\"Bob\"");
    assertEquals("Fly you fools, especially you Bob", result.toString());
  }

  @Test
  public void badArtifactThrowsGoodError() {
    RuntimeException runtimeException =
        new RuntimeException("Binary resource not found src/tolkien.js");
    when(activationContext.getBinary(Paths.get("hello-world/src/tolkien.js").toString()))
        .thenThrow(runtimeException);
    deploymentSpec.put("artifact", "src/tolkien.js");

    try {
      adapter.activate("hello-world", "", "", deploymentSpec);
    } catch (Exception ex) {
      assertEquals("Error loading source", ex.getMessage());
      assertEquals(runtimeException, ex.getCause());
    }
  }

  @Test
  public void activationCantFindFunction() throws Exception {
    deploymentSpec.put("function", "goodbye1");
    when(activationContext.getBinary(anyString()))
        .thenReturn("function goodbye(name){ return 'Goodbye, ' + name;}".getBytes());
    try {
      adapter.activate("hello-world", "", "", deploymentSpec);
    } catch (Exception ex) {
      assertEquals("Error loading source", ex.getMessage());
      assertEquals("Function goodbye1 not found", ex.getCause().getMessage());
    }
  }

  @Test
  public void getTypeReturnsJavascript() {
    assertEquals("JAVASCRIPT", adapter.getType());
  }

}
