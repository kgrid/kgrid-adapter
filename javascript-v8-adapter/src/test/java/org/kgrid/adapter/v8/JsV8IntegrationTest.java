package org.kgrid.adapter.v8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.Before;
import org.junit.Test;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JsV8IntegrationTest {

  Adapter adapter;
  TestActivationContext activationContext;

  @Before
  public void setUp() {
    activationContext = new TestActivationContext();
    adapter = new JsV8Adapter();
    adapter.initialize(activationContext);
  }

  @Test
  public void testActivatesObjectAndGetsExecutor() throws IOException {
    JsonNode deploymentSpec = getDeploymentSpec("hello-world/deploymentSpec.yaml");
    JsonNode endpointObject = deploymentSpec.get("endpoints").get("/welcome");
    Executor executor = adapter.activate("hello-world", "", "", endpointObject);
    Object helloResult = executor.execute("{\"name\":\"Bob\"}");
    assertEquals("Hello, Bob", helloResult);
  }

  @Test
  public void testActivatesBundledJSObjectAndGetsExecutor() throws IOException {
    JsonNode deploymentSpec = getDeploymentSpec("hello-world-v1.3/deploymentSpec.yaml");
    JsonNode endpointObject = deploymentSpec.get("endpoints").get("/welcome");
    Executor executor = adapter.activate("hello-world-v1.3", "", "", endpointObject);
    Object helloResult = executor.execute("{\"name\":\"Bob\"}");
    assertEquals("Hello, Bob", helloResult);
  }

  @Test(expected = AdapterException.class)
  public void testCanCallOtherExecutor() throws IOException {
    JsonNode deploymentSpec = getDeploymentSpec("hello-world/deploymentSpec.yaml");
    JsonNode endpointObject = deploymentSpec.get("endpoints").get("/welcome");
    Executor helloExecutor = adapter.activate("hello-world", "", "", endpointObject);
    activationContext.addExecutor("hello-world/welcome", helloExecutor);
    deploymentSpec = getDeploymentSpec("hello-exec/deploymentSpec.yaml");
    endpointObject = deploymentSpec.get("endpoints").get("/welcome");
    Executor executor = adapter.activate("hello-exec", "", "", endpointObject);
    Object helloResult = executor.execute("{\"name\":\"Bob\"}");
    assertEquals("Hello, Bob", helloResult);
  }

//  @Test
  public void canCallBundledExec() throws IOException {
    // Activate the child
    JsonNode deploymentSpec = getDeploymentSpec("exec-step-v1.0.0/deploymentSpec.yaml");
    JsonNode endpointObject = deploymentSpec.get("endpoints").get("/welcome");
    Executor childExecutor = adapter.activate("exec-step-v1.0.0", "", "", endpointObject);
    activationContext.addExecutor("exec-step-v1.0.0/welcome", childExecutor);

    // Activate the executive Obj
    deploymentSpec = getDeploymentSpec("exec-example-v1.0.0/deploymentSpec.yaml");
    endpointObject = deploymentSpec.get("endpoints").get("/welcome");
    Executor executor = adapter.activate("exec-example-v1.0.0", "", "", endpointObject);

    Object helloResult =
        executor.execute(
            "{\n"
                + "  \"name\": \"Test 21\",\n"
                + "  \"iterations\": 2,\n"
                + "  \"steps\": 2\n"
                + "}");
    assertEquals("Hello, Bob", helloResult);
  }

  private JsonNode getDeploymentSpec(String deploymentLocation) throws IOException {
    YAMLMapper yamlMapper = new YAMLMapper();
    ClassPathResource classPathResource = new ClassPathResource(deploymentLocation);
    JsonNode deploymentSpec =
        yamlMapper.readTree(classPathResource.getInputStream().readAllBytes());
    return deploymentSpec;
  }

}

class TestActivationContext implements ActivationContext {

  Map<String, Executor> executorMap = new HashMap<>();

  @Override
  public Executor getExecutor(String s) {
    return executorMap.get(s);
  }

  @Override
  public byte[] getBinary(String s) {
    try {
      return new ClassPathResource(s).getInputStream().readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getProperty(String s) {
    return null;
  }

  public void addExecutor(String id, Executor executor){
    executorMap.put(id, executor);
  }
}

