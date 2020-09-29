package org.kgrid.adapter.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;

public interface Adapter {

  String getType();

  void initialize(ActivationContext context);

  Executor activate(URI absoluteLocation, URI endpointURI, JsonNode deploymentSpec);

  String status();
}
