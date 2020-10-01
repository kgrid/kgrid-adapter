package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin
@RestController
public class ProxyAdapter implements Adapter {

  static Map<String, String> runtimes = new HashMap<>();
  static String shelfAddress;
  static ActivationContext activationContext;
  private Logger log = LoggerFactory.getLogger(getClass());
  @Autowired private RestTemplate restTemplate;

  @PostMapping(
      value = "/proxy/environments",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ObjectNode registerRemoteRuntime(
      @RequestBody ObjectNode runtimeDetails, HttpServletRequest req) {

    String runtimeName = runtimeDetails.get("type").asText();
    String runtimeAddress = runtimeDetails.get("url").asText();
    isRemoteUp(runtimeName, runtimeAddress);
    if (runtimes.get(runtimeName) != null) {
      log.info(
          "Overwriting the remote environment url that can handle "
              + runtimeName
              + " with new address "
              + runtimeAddress);
    } else {
      log.info(
          "Adding a new remote environment to the registry that can handle "
              + runtimeName
              + " and is located at "
              + runtimeAddress);
    }
    String thisURL = req.getRequestURL().toString();
    shelfAddress = StringUtils.substringBefore(thisURL, "/proxy/environments");
    log.info("The address of this server is " + shelfAddress);
    runtimes.put(runtimeName, runtimeAddress);

    return new ObjectMapper().createObjectNode().put("Registered", "success");
  }

  @GetMapping(value = "/proxy/environments", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArrayNode getRuntimeList() {

    log.info("Returning list of all available runtimes.");
    ArrayNode runtimeList = new ObjectMapper().createArrayNode();
    runtimes.forEach(
        (runtimeName, runtimeAddress) -> {
          ObjectNode runtimeDetails = new ObjectMapper().createObjectNode();

          runtimeDetails.put("type", runtimeName);
          runtimeDetails.put("url", runtimeAddress);
          boolean running;
          try {
            running = isRemoteUp(runtimeName, runtimeAddress);
          } catch (Exception e) {
            running = false;
            runtimeDetails.put("error_details", e.getMessage());
          }
          runtimeDetails.put("running", running);
          runtimeList.add(runtimeDetails);
        });
    return runtimeList;
  }

  @GetMapping(value = "/proxy/{location}/**")
  public byte[] getCodeArtifact(@PathVariable URI location, HttpServletRequest request) {
    String requestURI = request.getRequestURI();
    URI path = URI.create(StringUtils.substringAfter(requestURI, "proxy/"));
    return activationContext.getBinary(path);
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
  public Executor activate(URI absoluteLocation, URI endpointURI, JsonNode deploymentSpec) {
    String engine = getEngine(deploymentSpec);
    String remoteServer = runtimes.get(engine);
    isRemoteUp(engine, remoteServer);

    try {
      if (deploymentSpec.has("artifact")) {
        if (shelfAddress == null || "".equals(shelfAddress)) {
          shelfAddress = buildShelfAddress();
        }
        String proxyEndpoint = "proxy";
        ((ObjectNode) deploymentSpec)
            .put(
                "baseUrl",
                String.format("%s/%s/%s", shelfAddress, proxyEndpoint, absoluteLocation));

        ((ObjectNode) deploymentSpec).put("uri", endpointURI.toString());
      } else {
        log.info(
            "Object with endpoint id "
                + endpointURI
                + " does not have an artifact in the deployment spec. Cannot create executor.");
        return null;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<JsonNode> activationReq = new HttpEntity<>(deploymentSpec, headers);
      JsonNode activationResult =
          restTemplate.postForObject(remoteServer + "/deployments", activationReq, JsonNode.class);
      URL remoteServerUrl =
          (null == activationResult.get("baseUrl"))
              ? new URL(remoteServer)
              : new URL(activationResult.get("baseUrl").asText());
      URL remoteEndpoint = new URL(remoteServerUrl, activationResult.get("endpoint_url").asText());

      log.info(
          "Deployed object with endpoint id "
              + endpointURI
              + " to the "
              + engine
              + " runtime and got back an endpoint url of "
              + remoteEndpoint.toString()
              + " at ");

      return new Executor() {
        @Override
        public Object execute(Object input) {

          try {
            HttpEntity<Object> executionReq = new HttpEntity<>(input, headers);
            Object result =
                restTemplate.postForObject(remoteEndpoint.toString(), executionReq, JsonNode.class);
            return result;
          } catch (HttpClientErrorException | ResourceAccessException e) {
            throw new AdapterException(
                "Cannot access object pay load in remote environment. "
                    + "Cannot connect to url "
                    + remoteEndpoint.toString());
          }
        }
      };
    } catch (HttpClientErrorException e) {
      throw new AdapterException(
          String.format("Cannot activate object at address %s/deployments", remoteServer), e);
    } catch (HttpServerErrorException e) {
      throw new AdapterException(
          String.format("Remote runtime server: %s is unavailable", remoteServer), e);
    } catch (MalformedURLException e) {
      throw new AdapterException(
          String.format(
              "Invalid URL returned when activating object at address %s/deployments",
              remoteServer),
          e);
    }
  }

  private String buildShelfAddress() {
    String serverHost = activationContext.getProperty("kgrid.adapter.proxy.vipAddress");
    String serverPort = activationContext.getProperty("kgrid.adapter.proxy.port");
    if (serverHost == null || "".equals(serverHost)) {
      log.error("Activator does not know own address");
    }
    return serverHost + ":" + serverPort;
  }

  private String getEngine(JsonNode deploymentSpec) {
    String engine;
    if (!deploymentSpec.has("engine") || "".equals(deploymentSpec.get("engine").asText())) {
      throw new AdapterException("Cannot find engine type in proxy object");
    }
    engine = deploymentSpec.get("engine").asText();
    if (runtimes.get(engine) == null) {
      throw new AdapterException(
          "No engine for " + engine + " has been registered. Please register one and reactivate.");
    }
    return engine;
  }

  @Override
  public String status() {
    if (activationContext != null) {
      return "UP";
    } else {
      return "DOWN";
    }
  }

  private boolean isRemoteUp(String engine, String remoteServer) {
    try {
      if (remoteServer == null || "".equals(remoteServer)) {
        throw new AdapterException(
            "Remote server address not set, check that the remote environment for "
                + engine
                + " has been set up.");
      }
      ResponseEntity<JsonNode> resp =
          restTemplate.getForEntity(remoteServer + "/info", JsonNode.class);
      if (resp.getStatusCode() != HttpStatus.OK
          || !resp.getBody().has("Status")
          || !"Up".equalsIgnoreCase(resp.getBody().get("Status").asText())) {
        throw new AdapterException(
            "Remote execution environment not online, \"Up\" response not received from "
                + remoteServer
                + "/info");
      }

    } catch (HttpClientErrorException | ResourceAccessException | IllegalArgumentException e) {
      throw new AdapterException(
          "Remote execution environment not online, could not resolve remote server address, "
              + "check that address is correct and server for environment "
              + engine
              + " is running at "
              + remoteServer
              + " Root error "
              + e.getMessage());
    }
    return true;
  }
}
