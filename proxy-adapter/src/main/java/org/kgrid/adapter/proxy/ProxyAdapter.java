package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException.InternalServerError;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@CrossOrigin
@RestController
public class ProxyAdapter implements Adapter {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  RestTemplate restTemplate = new RestTemplate();

  ActivationContext activationContext;

  static Map<String, String> runtimes = new HashMap<>();

  @PostMapping(value = "/register",
          consumes = MediaType.APPLICATION_JSON_VALUE,
          produces = MediaType.APPLICATION_JSON_VALUE)
  public ObjectNode registerRemoteRuntime(@RequestBody ObjectNode runtimeDetails) {

    String runtimeName = runtimeDetails.get("type").asText();
    String runtimeAddress = runtimeDetails.get("url").asText();
    runtimes.put(runtimeName, runtimeAddress);
    isRemoteUp(runtimeName, runtimeAddress);

    log.info("Adding a remote environment to the registry that can handle " + runtimeName
            + " and is located at " + runtimeAddress);
    return new ObjectMapper().createObjectNode().put("Registered", "success");
  }

  @Override
  public String getType() {
    return "PROXY";
  }

  @Override
  public void initialize(ActivationContext context) {
    activationContext = context;
  }

  @Override
  public Executor activate(String objectLocation, ArkId arkId, String endpointName, JsonNode deploymentSpec) {

    if(!deploymentSpec.has("engine") || "".equals(deploymentSpec.get("engine").asText())) {
      throw new AdapterException("Cannot find engine type in proxy object with arkId " + arkId.getDashArkVersion());
    }
    String adapterName = deploymentSpec.get("engine").asText();
    String remoteServer = runtimes.get(adapterName);
    isRemoteUp(adapterName, remoteServer);

    try{
      if(deploymentSpec.has("artifact") && deploymentSpec.get("artifact").isArray()) {
        String serverHost = activationContext.getProperty("kgrid.adapter.proxy.vipAddress");
        String serverPort = activationContext.getProperty("kgrid.adapter.proxy.port");
        if(serverHost == null || "".equals(serverHost)) {
          log.warn("kgrid.adapter.proxy.vipAddress not set correctly");
        }
        String shelfEndpoint = activationContext.getProperty("kgrid.shelf.endpoint") != null ?
            activationContext.getProperty("kgrid.shelf.endpoint"): "kos";
        ArrayNode artifactURLs = new ObjectMapper().createArrayNode();
        deploymentSpec.get("artifact").forEach(path -> {
          String artifactPath = path.asText();
          String artifactURL = String.format("%s:%s/%s/%s/%s", serverHost, serverPort,
              shelfEndpoint, arkId.getSlashArkVersion(), artifactPath);
          artifactURLs.add(artifactURL);
        });
        ((ObjectNode) deploymentSpec).set("artifact", artifactURLs);
        ((ObjectNode) deploymentSpec).put("identifier", arkId.getFullArk());
        ((ObjectNode) deploymentSpec).put("version", arkId.getVersion());
        ((ObjectNode) deploymentSpec).put("endpoint", endpointName);
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
            throw new AdapterException("Cannot access object payload in remote environment. " +
                    "Cannot connect to url " + remoteEndpoint);
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
    if(activationContext != null) {
      return "UP" ;
    } else {
      return "DOWN";
    }
  }

  private boolean isRemoteUp(String envName, String remoteServer) {
    try {
      if(remoteServer == null || "".equals(remoteServer)) {
        throw new AdapterException("Remote server not set, check that the remote environment for "
              + envName + " has been set up");
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
