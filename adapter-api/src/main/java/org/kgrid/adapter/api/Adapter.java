package org.kgrid.adapter.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;

public interface Adapter {

  List<String> getEngines();

  void initialize(ActivationContext context);

  Executor activate(URI absoluteLocation, URI endpointURI, JsonNode deploymentSpec);

  String status();
}
