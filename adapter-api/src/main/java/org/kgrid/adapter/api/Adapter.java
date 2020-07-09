package org.kgrid.adapter.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;

public interface Adapter {

  String getType();

  void initialize(ActivationContext context);

  Executor activate(Path resource, String entry);

  Executor activate(String objectLocation, String arkId, String endpointName, JsonNode deploymentSpec);

  String status();
}
