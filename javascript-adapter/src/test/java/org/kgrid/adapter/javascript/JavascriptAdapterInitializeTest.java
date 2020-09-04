package org.kgrid.adapter.javascript;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.Executor;

public class JavascriptAdapterInitializeTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void initializeWithCDOStore() {

    Adapter adapter = new JavascriptAdapter();
    adapter.initialize(new ActivationContext() {
      @Override
      public Executor getExecutor(String key) {
        return null;
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
    assertEquals("UP", adapter.status());

  }

  @Test
  public void initializeWithOutCDOStore() {

    Adapter adapter = new JavascriptAdapter();
    adapter.initialize(null);
    assertEquals("DOWN", adapter.status());

  }

}