package org.kgrid.adapter.javascript;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Executor;
import org.kgrid.adapter.javascript.utils.MyMath;
import org.kgrid.shelf.repository.CompoundDigitalObjectStore;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.kgrid.adapter.javascript.utils.RepoUtils.getBinaryTestFile;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class JavascriptToJavaIntegrationTests {

  @Mock CompoundDigitalObjectStore cdoStore;

  private JavascriptAdapter adapter;
  private Map<String, Object> endpoints = new HashMap<>();

  @Before
  public void setUp() throws Exception {
    adapter = new JavascriptAdapter();
    adapter.initialize(
        new ActivationContext() {
          @Override
          public Executor getExecutor(String key) {
            return ((Endpoint) endpoints.get(key)).getExecutor();
          }

          @Override
          public byte[] getBinary(String pathToBinary) {
            return cdoStore.getBinary(pathToBinary);
          }

          @Override
          public String getProperty(String key) {
            return null;
          }
        });
  }

  @Test
  public void activateHappyPath() throws IOException {
    given(cdoStore.getBinary(eq(Paths.get("echo-object-v1.0/src/echo.js").toString())))
        .willReturn(getBinaryTestFile("echo-object-v1.0", "src/echo.js"));

    Executor executor = adapter.activate(Paths.get("echo-object-v1.0/src/echo.js"), "echo");

    Object result =
        executor.execute(
            new HashMap<String, String>() {
              {
                put("name", "Bob");
              }
            });
    assertEquals("{name=Bob}", result.toString());
  }

  @Test
  public void canCallStandardJavaObject() throws IOException {
    given(cdoStore.getBinary(eq(Paths.get("math-object-v1.0/src/math.js").toString())))
        .willReturn(getBinaryTestFile("math-object-v1.0", "src/math.js"));

    Executor executor = adapter.activate(Paths.get("math-object-v1.0/src/math.js"), "math");

    int[] a = {1, 2};
    Map inputs =
        new HashMap<String, Object>() {
          {
            put("value1", 2);
            put("value2", a);
          }
        };

    Map result = (Map) executor.execute(inputs);

    assertEquals(MyMath.doubler(2), result.get("result1"));

    MyMath myMath = new MyMath();
    assertEquals(myMath.add(1, 2), result.get("result2"));
  }

  @Test
  public void canPassInstanceToScript() throws IOException {
    given(cdoStore.getBinary(eq(Paths.get("hello-object-v1.0/src/welcome.js").toString())))
        .willReturn(getBinaryTestFile("hello-object-v1.0", "src/welcome.js"));

    given(cdoStore.getBinary(eq(Paths.get("math-object-v1.0/src/math.js").toString())))
        .willReturn(getBinaryTestFile("math-object-v1.0", "src/math.js"));

    //    Map<String, Object> endpoints = new HashMap<>();
    //    adapter.setEndpoints(endpoints);

    final Executor welcome =
        adapter.activate(Paths.get("hello-object-v1.0/src/welcome.js"), "welcome");
    final Executor add = adapter.activate(Paths.get("math-object-v1.0/src/math.js"), "math2");

    endpoints.put("hello-object/v1.0/welcome", new Endpoint(welcome));

    Map inputs =
        new HashMap<String, Object>() {
          {
            put("value1", 2);
            put("value2", new int[] {1, 2});
          }
        };

    Map result = (Map) add.execute(inputs);

    assertEquals("Welcome to Knowledge Grid, Ted! Answer is: 3", result.get("execResult"));
  }

  // Dummy endpoint; loaded into the endpoints map and invoked by Javascript KO
  // as 'endpoints["a-b/c/welcome"].getExecutor().execute(inputs)'
  public class Endpoint {

    final Executor executor;

    public Endpoint(Executor executor) {
      this.executor = executor;
    }

    public Executor getExecutor() {
      return executor;
    }
  }
}
