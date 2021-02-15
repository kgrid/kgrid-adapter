package org.kgrid.adapter.api;

import org.kgrid.adapter.api.Executor;

import java.io.InputStream;
import java.net.URI;

public interface ActivationContext {

  Executor getExecutor(String key);

  InputStream getBinary(URI pathToBinary);

  String getProperty(String key);

  void refresh(String engineName);
}
