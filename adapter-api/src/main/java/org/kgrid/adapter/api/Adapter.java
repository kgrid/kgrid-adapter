package org.kgrid.adapter.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.file.Path;

public interface Adapter {

  String getType();

  void initialize(ActivationContext context);

  Executor activate(URI objectLocation, String naan, String name, String version, String endpointName, JsonNode deploymentSpec);

  String status();
}
