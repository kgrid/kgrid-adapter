package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.kgrid.shelf.domain.ArkId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private final Logger log = LoggerFactory.getLogger(this.getClass());

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
  public Executor activate(String objectLocation, ArkId arkId, JsonNode deploymentSpec) {

    try{
      isRemoteUp();

      if(deploymentSpec.has("artifact") && deploymentSpec.get("artifact").isArray()) {
        String serverHost = activationContext.getProperty("kgrid.adapter.proxy.self");
        if(serverHost == null || "".equals(serverHost)) {
          log.warn("Server host not set correctly");
        }
        String shelfEndpoint = activationContext.getProperty("kgrid.shelf.endpoint") != null ?
            activationContext.getProperty("kgrid.shelf.endpoint"): "kos";
        ArrayNode artifactURLs = new ObjectMapper().createArrayNode();
        deploymentSpec.get("artifact").forEach(path -> {
          String artifactPath = path.asText();
          String artifactURL = String.format("http://%s/%s/%s/%s", serverHost,
              shelfEndpoint, arkId.getSlashArkVersion(), artifactPath);
          artifactURLs.add(artifactURL);
        });
        ((ObjectNode) deploymentSpec).set("artifact", artifactURLs);
        ((ObjectNode) deploymentSpec).put("identifier", arkId.getFullArk());
        ((ObjectNode) deploymentSpec).put("version", arkId.getVersion());
        ((ObjectNode) deploymentSpec).put("endpoint", deploymentSpec.get("entry"));
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> activationReq = new HttpEntity<>(deploymentSpec.toString(), headers);
      JsonNode activationResult = restTemplate
          .postForObject(remoteServer + "/deployments", activationReq, JsonNode.class);
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
      throw new AdapterException("Cannot activate object at address " + remoteServer + "/deployments"
          + " with body " + deploymentSpec.toString() + " " + ex.getMessage());
    }
  }

  @Override
  @Deprecated
  public Executor activate(Path resource, String entry) {
    return null;
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
      if (resp.getStatusCode() != HttpStatus.OK || !resp.getBody().has("Status")
          || !"Up".equals(resp.getBody().get("Status").asText())) {
        throw new AdapterException(
            "Remote execution environment not online, \"Up\" response not recieved from " + remoteServer
                  + "/info");
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
