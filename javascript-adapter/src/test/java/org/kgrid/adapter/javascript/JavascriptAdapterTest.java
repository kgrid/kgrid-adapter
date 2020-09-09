package org.kgrid.adapter.javascript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JavascriptAdapterTest {
  private static final String JAVASCRIPT = "JAVASCRIPT";
  private static final String SRC_WELCOME_JS = "src/welcome.js";
  private static final String WELCOME = "welcome";
  private static final String ADAPTER = "adapter";
  private static final String FUNCTION = "function";
  private static final String ARTIFACT = "artifact";
  @Mock ActivationContext context;

  @InjectMocks private JavascriptAdapter adapter;

  private static final String NAAN = "NAAN";
  private static final String NAME = "NAME";
  private static final String VERSION = "VERSION";
  private static final String ENDPOINT = "ENDPOINT";
  private static final URI OBJECT_LOCATION = URI.create("LOCATION/");
  private static final String HELLO_CODE = "function welcome(inputs){\n" +
          "  var name = inputs.name;\n" +
          "  return \"Welcome, \" + name;\n" +
          "}";

  private static final String BAD_FUNCTION_CALL = "function welcome(inputs){\n" +
          "  return \"Welcome, \" + hello();\n" +
          "}";
  private ObjectNode deploymentSpec;


  @Before
  public void setUp() {

    when(context.getBinary(any())).thenReturn(HELLO_CODE.getBytes());
    adapter.initialize(context);
    deploymentSpec = new ObjectMapper().createObjectNode();
    deploymentSpec
            .put(ADAPTER, JAVASCRIPT)
            .put(FUNCTION, WELCOME)
            .putArray(ARTIFACT)
            .add(SRC_WELCOME_JS);
  }

  @Test
  public void getType_returnsJS() {
    assertEquals(JAVASCRIPT, adapter.getType());
  }

  @Test
  public void activate_createsGoodExecutorThatRuns() {
    Map<Object, Object> inputs = new HashMap<>();
    inputs.put("name", "Hank");
    Executor ex = adapter.activate(OBJECT_LOCATION, NAAN, NAME, VERSION, ENDPOINT, deploymentSpec);
    assertEquals("Welcome, Hank", ex.execute(inputs));
  }

  @Test
  public void activate_createsGoodExecutorWithMultipleArtifactsThatRuns() {
    deploymentSpec = new ObjectMapper().createObjectNode();
    deploymentSpec
            .put(ADAPTER, JAVASCRIPT)
            .put(FUNCTION, WELCOME)
            .put("entry", SRC_WELCOME_JS)
            .putArray(ARTIFACT)
            .add(SRC_WELCOME_JS)
            .add("src/goodbye.js");
    Map<Object, Object> inputs = new HashMap<>();
    inputs.put("name", "Hank");
    Executor ex = adapter.activate(OBJECT_LOCATION, NAAN, NAME, VERSION, ENDPOINT, deploymentSpec);
    assertEquals("Welcome, Hank", ex.execute(inputs));
  }

  @Test
  public void activate_createsGoodExecutorWithOneArtifactThatRuns() {
    deploymentSpec = new ObjectMapper().createObjectNode();
    deploymentSpec
            .put(ADAPTER, JAVASCRIPT)
            .put(FUNCTION, WELCOME)
            .put(ARTIFACT, SRC_WELCOME_JS);
    Map<Object, Object> inputs = new HashMap<>();
    inputs.put("name", "Hank");
    Executor ex = adapter.activate(OBJECT_LOCATION, NAAN, NAME, VERSION, ENDPOINT, deploymentSpec);
    assertEquals("Welcome, Hank", ex.execute(inputs));
  }

  @Test
  public void activate_throwsErrorWithNoArtifactLocation() {
    deploymentSpec = new ObjectMapper().createObjectNode();
    deploymentSpec
            .put(ADAPTER, JAVASCRIPT)
            .put(FUNCTION, WELCOME);

    assertThrows(AdapterException.class, ()-> adapter.activate(OBJECT_LOCATION, NAAN, NAME, VERSION, ENDPOINT, deploymentSpec));

  }

  @Test
  public void activate_createsGoodExecutorWithEntryThatRuns() {
    deploymentSpec = new ObjectMapper().createObjectNode();
    deploymentSpec
            .put(ADAPTER, JAVASCRIPT)
            .put("entry", WELCOME)
            .put(ARTIFACT, SRC_WELCOME_JS);
    Map<Object, Object> inputs = new HashMap<>();
    inputs.put("name", "Hank");
    Executor ex = adapter.activate(OBJECT_LOCATION, NAAN, NAME, VERSION, ENDPOINT, deploymentSpec);
    assertEquals("Welcome, Hank", ex.execute(inputs));
  }

  @Test
  public void activate_ThrowstErrorWhenScriptDoesntCompile() {
    when(context.getBinary(any())).thenReturn("function welcome(){{{{".getBytes());
    assertThrows(AdapterException.class, ()-> adapter.activate(OBJECT_LOCATION, NAAN, NAME, VERSION, ENDPOINT, deploymentSpec));
  }

  @Test
  public void activate_throwsErrorWithNullBinary() {
    when(context.getBinary(any())).thenReturn(null);
    assertThrows(AdapterException.class, ()-> adapter.activate(OBJECT_LOCATION, NAAN, NAME, VERSION, ENDPOINT, deploymentSpec));
  }

  @Test
  public void activate_throwsErrorWhenGetBinaryErrors() {
    when(context.getBinary(any())).thenThrow(new RuntimeException("Bad stuff"));
    assertThrows(AdapterException.class, ()-> adapter.activate(OBJECT_LOCATION, NAAN, NAME, VERSION, ENDPOINT, deploymentSpec));
  }

  @Test
  public void status_isUpWithContextAndEngine() {
    assertEquals("UP", adapter.status());
  }

  @Test
  public void status_isDownWithNoEngine() {

    assertEquals("DOWN", new JavascriptAdapter().status());
  }

  @Test
  public void status_isDownWithNoContext() {
    Adapter jsAdapter = new JavascriptAdapter();
    jsAdapter.initialize(null);
    assertEquals("DOWN", jsAdapter.status());
  }
}
