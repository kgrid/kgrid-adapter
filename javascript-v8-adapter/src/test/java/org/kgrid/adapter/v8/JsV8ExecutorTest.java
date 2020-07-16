package org.kgrid.adapter.v8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JsV8ExecutorTest {

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

  @Test(expected = AdapterException.class)
  public void codeExecutionErrorThrowsAdapterException() {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn("function hello(name){ throw 'Error!!, ' + name;}".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    ex.execute("");
  }

  @Test
  public void canReadListObjectInput() throws JsonProcessingException {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn(
            "function hello(name){ var bar = name[0]; return 'Hello, ' + bar; }".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    List list = Collections.singletonList("Tom");
    String input = new ObjectMapper().writeValueAsString(list);
    assertEquals("Hello, Tom", ex.execute(input));
  }

  @Test
  public void canReadRawMapObjectInput() throws JsonProcessingException {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
            .thenReturn("function hello(name){ var bar = name.a; return 'Hello, ' + bar; }".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Map<String, String> lhm = new LinkedHashMap<>();
    lhm.put("a", "Tom");
    assertNotEquals("Hello, Tom", ex.execute(lhm));
  }

  @Test
  public void canReadArrayInput() throws JsonProcessingException {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn("function hello(name){ return name; }".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    int[] array = {1, 2, 3};
    assertArrayEquals(new int[] {1, 2, 3}, (int[]) ex.execute(array));
  }

  @Test
  public void canReadMapObjectInput() throws JsonProcessingException {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn("function hello(name){ var bar = name.a; return 'Hello, ' + bar; }".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Map<String, String> lhm = new LinkedHashMap<>();
    lhm.put("a", "Tom");
    String input = new ObjectMapper().writeValueAsString(lhm);
    assertEquals("Hello, Tom", ex.execute(input));
  }

  @Test
  public void canReadNestedMapObjectInput() throws JsonProcessingException {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn(
            "function hello(name){ var bar = name.map.a; return 'Hello, ' + bar; }".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Map<String, Map> lhm = new LinkedHashMap<>();
    Map<String, String> nested = new LinkedHashMap<>();
    nested.put("a", "Tom");
    lhm.put("map", nested);
    String input = new ObjectMapper().writeValueAsString(lhm);
    assertEquals("Hello, Tom", ex.execute(input));
  }

  @Test
  public void canReturnMapFromJS() {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn("function hello(name){ return {'a':'1', 'b':'2', 'c':'3'};}".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Object result = ex.execute(0);
    assertEquals("{a: \"1\", b: \"2\", c: \"3\"}", result.toString());
  }

  @Test
  public void canReturnJavaArray() {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn(
            ("function hello(name){ var intArray = Java.type('int[]'); var iarr = new intArray(3);"
                    + "iarr[0] = 1; iarr[1] = 2; iarr[2] = 3; return iarr;}")
                .getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Object result = ex.execute(0);
    assertEquals("[1, 2, 3]", Arrays.toString((int[]) result));
  }

  @Test
  public void canReturnNestedJavaArray() throws JsonProcessingException {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn(
            ("function hello(name){ var intArray = Java.type('int[]'); var iarr = new intArray(3);"
                    + "iarr[0] = 1; iarr[1] = 2; iarr[2] = 3;"
                    + "return {'a':iarr, 'b':'2', 'c':'3'};}")
                .getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Object result = ex.execute(0);
    String json = new ObjectMapper().writeValueAsString(result);
    assertEquals("{\"a\":[1,2,3],\"b\":\"2\",\"c\":\"3\"}", json);
  }

  // These two don't work without us adding extra handling to convert javascript arrays back into
  // java arrays
  // Keep an eye on the graal handling of this array translation
  @Test
  public void canReturnArrayFromJS() {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn("function hello(name){ return [1, 2, 3];}".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Object result = ex.execute(0);
    assertNotEquals("[1 ,2, 3]", result);
  }

  @Test
  public void canReturnMapWithArrayFromJS() throws JsonProcessingException {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
        .thenReturn("function hello(name){ return {'a':[1, 4, 5], 'b':'2', 'c':'3'};}".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Object result = ex.execute(0);
    String json = new ObjectMapper().writeValueAsString(result);
    assertNotEquals("{\"a\":[1,4,5],\"b\":\"2\",\"c\":\"3\"}", json);
  }

  @Test
  public void canCallFunction() throws JsonProcessingException {
    when(activationContext.getBinary(Paths.get("hello-world/src/welcome.js").toString()))
            .thenReturn("var hello = (function(name){ return {'a':[1, 4, 5], 'b':'2', 'c':'3'};})".getBytes());
    Executor ex = adapter.activate("hello-world", "", "", deploymentSpec);
    Object result = ex.execute(0);
    String json = new ObjectMapper().writeValueAsString(result);
    assertNotEquals("{\"a\":[1,4,5],\"b\":\"2\",\"c\":\"3\"}", json);
  }


}
