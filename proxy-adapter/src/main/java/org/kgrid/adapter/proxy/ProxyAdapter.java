package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.kgrid.shelf.ShelfResourceNotFound;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException.InternalServerError;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class ProxyAdapter implements Adapter {

  private String remoteServer;

  RestTemplate restTemplate = new RestTemplate();

  ActivationContext activationContext;

  @Override
  public String getType() {
    return "PROXY";
  }

  @Override
  public void initialize(ActivationContext context) {

    // Check that the remote server is up
    remoteServer = context.getProperty("kgrid.adapter.proxy.url");

    activationContext = context;

    isRemoteUp();
  }

  @Override
  public Executor activate(Path resource, String endpoint) {

    try {

      isRemoteUp();

      byte[] metadata = activationContext.getBinary(resource.toString());
      JsonNode koDetails = new ObjectMapper().readTree(metadata);

      ObjectNode activationBody = new ObjectMapper().createObjectNode();
      activationBody.put("arkid", koDetails.get("identifier").asText());
      activationBody.put("version", koDetails.get("version").asText());
      activationBody.put("default", true);
      activationBody.put("endpoint", endpoint);
      activationBody.put("entry", koDetails.get("main").asText());
      activationBody.set("artifacts", new ObjectMapper().createArrayNode().add(
          koDetails.get("hasPayload").asText()));

      try {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> activationReq = new HttpEntity<>(activationBody.toString(), headers);
        JsonNode activationResult = restTemplate
            .postForObject(remoteServer + "/activate", activationReq, JsonNode.class);
        String remoteEndpoint = activationResult.get("endpoint_url").asText();

        return new Executor() {
          @Override
          public Object execute(Object input) {

            try {
              HttpEntity<Object> executionReq = new HttpEntity<>(input, headers);
              Object result = restTemplate
                  .postForObject(remoteEndpoint, executionReq, JsonNode.class);
              return result;
            } catch (HttpClientErrorException | ResourceAccessException e) {
              throw new AdapterException("Cannot access object payload in remote enviornment");
            }
          }
        };
      } catch (HttpClientErrorException | InternalServerError ex) {
        throw new AdapterException("Cannot activate object at address " + remoteServer + "/activate"
                + " with body " + activationBody.toString() + " " + ex.getMessage());
      }
    } catch (IOException | NullPointerException e) {
      throw new AdapterException("Cannot read in metadata info to generate remote execution request"
          + " in proxy adapter for endpoint " + endpoint);
    } catch (ShelfResourceNotFound srnfEx) {
      throw new AdapterException("Cannot read info from file at " + resource.toString()
                + " Check that the service description for this ko is correct.");
    }
  }

  @Override
  public String status() {
    if(isRemoteUp() && activationContext != null) {
      return "UP" ;
    } else {
      return "DOWN";
    }
  }

  private boolean isRemoteUp() {
    try {
      if(remoteServer == null || "".equals(remoteServer)) {
        throw new AdapterException("Remote server not set, check that the remote server property "
            + "kgrid.adapter.proxy.url has been set");
      }
      ResponseEntity<JsonNode> resp = restTemplate.getForEntity(remoteServer +"/info", JsonNode.class);
      if (resp.getStatusCode() != HttpStatus.OK || !"Up".equals(resp.getBody().get("Status").asText())) {
        throw new AdapterException(
            "Remote execution environment not online, no response from " + remoteServer);
      }

    } catch (HttpClientErrorException | ResourceAccessException | IllegalArgumentException e) {
      throw new AdapterException(
          "Remote execution environment not online, could not resolve remote server address, "
              + "check that address is correct and server is running at " + remoteServer
              + " Root error " + e.getMessage());
    }
    return true;
  }
}
