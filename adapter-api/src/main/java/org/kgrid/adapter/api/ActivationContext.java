package org.kgrid.adapter.api;

import org.kgrid.adapter.api.Executor;

import java.net.URI;

public interface ActivationContext {

  Executor getExecutor(String key);

  byte[] getBinary(URI pathToBinary);

  String getProperty(String key);
}
