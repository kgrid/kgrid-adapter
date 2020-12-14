package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
public class ProxyAdapter implements Adapter {
    static Map<String, RuntimeDetails> runtimes = new HashMap<>();
    static String shelfAddress;
    static ActivationContext activationContext;
    private Logger log = LoggerFactory.getLogger(getClass());
    @Autowired
    private RestTemplate restTemplate;

    @PostMapping(
            value = "/proxy/environments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> registerRemoteRuntime(
            @RequestBody ObjectNode runtimeDetails, HttpServletRequest req) {

        String runtimeEngine = runtimeDetails.at("/engine").asText();
        String runtimeAddress = runtimeDetails.at("/url").asText();
        String runtimeVersion = runtimeDetails.at("/version").asText();

        if (runtimes.get(runtimeEngine) != null) {
            log.info("Overwriting remote address for the " + runtimeEngine + " environment. New address is: " + runtimeAddress);
        } else {
            log.info(
                    "Adding a new remote environment to the registry that can handle "
                            + runtimeEngine
                            + " and is located at "
                            + runtimeAddress);
        }
        String thisURL = req.getRequestURL().toString();
        shelfAddress = StringUtils.substringBefore(thisURL, "/proxy/environments");
        log.info("The address of this server is " + shelfAddress);
        runtimes.put(runtimeEngine, new RuntimeDetails(runtimeEngine, runtimeVersion, runtimeAddress));

        ObjectNode body = runtimeDetails.put("registered", "success");
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @GetMapping(value = "/proxy/environments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ArrayNode getRuntimeList() {

        log.info("Returning list of all available runtimes.");
        ArrayNode runtimeList = new ObjectMapper().createArrayNode();
        runtimes.forEach(
                (runtimeName, runtimeDetails) -> runtimeList.add(getEnvDetails(runtimeDetails)));
        return runtimeList;
    }

    @GetMapping(value = "/proxy/environments/{engine}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode getRuntimeList(@PathVariable String engine) {

        log.info(String.format("Returning info on the %s engine.", engine));

        return getEnvDetails(runtimes.get(engine));
    }

    private ObjectNode getEnvDetails(RuntimeDetails details) {
        ObjectNode runtimeDetails = new ObjectMapper().createObjectNode();

        runtimeDetails.put("engine", details.getEngine());
        runtimeDetails.put("version", details.getVersion());
        runtimeDetails.put("url", details.getAddress());
        String status;
        try {
            status = isRemoteUp(details.getEngine(), details.getAddress()) ? "up" : "down";
        } catch (Exception e) {
            status = "error";
            runtimeDetails.put("error_details", e.getMessage());
        }
        runtimeDetails.put("status", status);
        return runtimeDetails;
    }

    @GetMapping(value = "/proxy/**")
    public InputStreamResource getCodeArtifact(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        URI path = URI.create(StringUtils.substringAfter(requestURI, "proxy/"));
        return new InputStreamResource(activationContext.getBinary(path));
    }

    @Override
    public List<String> getEngines() {
        return new ArrayList(runtimes.keySet());
    }

    @Override
    public void initialize(ActivationContext context) {
        activationContext = context;
    }

    @Override
    public Executor activate(URI absoluteLocation, URI endpointURI, JsonNode deploymentSpec) {
        String engine = deploymentSpec.at("/engine").asText();
        String remoteServer = runtimes.get(engine).getAddress();
        isRemoteUp(engine, remoteServer);

        try {
            String proxyEndpoint = "proxy";
            ((ObjectNode) deploymentSpec)
                    .put("baseUrl", String.format("%s/%s/%s", shelfAddress, proxyEndpoint, absoluteLocation));

            ((ObjectNode) deploymentSpec).put("uri", endpointURI.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            HttpEntity<JsonNode> activationReq = new HttpEntity<>(deploymentSpec, headers);
            JsonNode activationResult =
                    restTemplate.postForObject(remoteServer + "/endpoints", activationReq, JsonNode.class);
            URL remoteServerUrl =
                    (null == activationResult.get("baseUrl"))
                            ? new URL(remoteServer)
                            : new URL(activationResult.get("baseUrl").asText());
            URL remoteEndpoint = new URL(remoteServerUrl, activationResult.get("uri").asText());

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
                public Object execute(Object input, String contentType) {

                    try {
                        headers.setContentType(MediaType.valueOf(contentType));
                        HttpEntity<Object> executionReq = new HttpEntity<>(input, headers);
                        Object result =
                                restTemplate.postForObject(remoteEndpoint.toString(), executionReq, JsonNode.class).get("result");
                        return result;
                    } catch (HttpClientErrorException e) {
                        if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                            throw new AdapterException(
                                    "Runtime error when executing object. Check code and inputs.");
                        } else {
                            throw new AdapterException(
                                    "Cannot access object payload in remote environment. "
                                            + "Cannot connect to url "
                                            + remoteEndpoint.toString());
                        }
                    } catch (ResourceAccessException e) {
                        throw new AdapterException(
                                "Cannot access object payload in remote environment. "
                                        + "Cannot connect to url "
                                        + remoteEndpoint.toString());
                    }
                }
            };
        } catch (HttpClientErrorException e) {
            String errorMessage;
            try {
                errorMessage = new ObjectMapper().readTree(e.getResponseBodyAsString()).get("description").asText();
            } catch (JsonProcessingException jsonProcessingException) {
                errorMessage = e.getResponseBodyAsString();
            }
            throw new AdapterException(
                    String.format("Client error activating object at address %s/deployments: %s", remoteServer, errorMessage), e);
        } catch (HttpServerErrorException e) {
            throw new AdapterException(
                    String.format("Remote runtime %s server error: %s", remoteServer, e.getMessage()), e);
        } catch (MalformedURLException e) {
            throw new AdapterException(
                    String.format(
                            "Invalid URL returned when activating object at address %s/deployments",
                            remoteServer),
                    e);
        }
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
        if (remoteServer == null || "".equals(remoteServer)) {
            throw new AdapterException(
                    "Remote server address not set, check that the remote environment for "
                            + engine
                            + " has been set up.");
        }
        try {
            ResponseEntity<JsonNode> resp =
                    restTemplate.getForEntity(remoteServer + "/info", JsonNode.class);
            if (resp.getStatusCode() != HttpStatus.OK
                    || !resp.getBody().has("status")
                    || !"up".equalsIgnoreCase(resp.getBody().get("status").asText())) {
                throw new AdapterException(
                        "Remote execution environment not online, \"status\":\"up\" response not received from "
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

    public static Map<String, Object> getRuntimes() {
        return new HashMap<>(runtimes);
    }

}
