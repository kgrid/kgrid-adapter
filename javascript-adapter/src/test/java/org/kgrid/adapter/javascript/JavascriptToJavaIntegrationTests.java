package org.kgrid.adapter.javascript;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Executor;
import org.kgrid.adapter.javascript.utils.MyMath;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class JavascriptToJavaIntegrationTests {

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
          public byte[] getBinary(URI pathToBinary) {
            return new byte[0];
          }

          @Override
          public String getProperty(String key) {
            return null;
          }
        });
  }

  @Test
  public void activateHappyPath() throws IOException {

    Executor executor = null;

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

    Executor executor = null;

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
  public void canPassInstanceToScript() {

    endpoints.put("hello-object/v1.0/welcome", new Endpoint(null));
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
