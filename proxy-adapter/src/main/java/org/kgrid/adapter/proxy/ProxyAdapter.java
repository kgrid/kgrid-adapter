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
import java.util.*;

@CrossOrigin
@RestController
public class ProxyAdapter implements Adapter {
    static Map<String, ObjectNode> runtimes = new HashMap<>();
    static String shelfAddress;
    static ActivationContext activationContext;
    private final Logger log = LoggerFactory.getLogger(getClass());
    @Autowired
    private RestTemplate restTemplate;

    @PostMapping(
            value = "/proxy/environments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> registerRemoteRuntime(
            @RequestBody ObjectNode runtimeDetails, HttpServletRequest req) {

        JsonNode runtimeEngine = runtimeDetails.at("/engine");
        JsonNode runtimeAddress = runtimeDetails.at("/url");

        if (runtimeEngine.isMissingNode() || runtimeEngine.asText().equals("")) {
            runtimeDetails.put("status", "Not registered: Runtime failed to specify engine");
            runtimes.put(runtimeEngine.asText(), runtimeDetails);
            return new ResponseEntity<>(runtimeDetails, HttpStatus.BAD_REQUEST);
        }
        if (runtimeAddress.isMissingNode() || runtimeAddress.asText().equals("")) {
            runtimeDetails.put("status", "Not registered: Runtime failed to specify its url");
            runtimes.put(runtimeEngine.asText(), runtimeDetails);
            return new ResponseEntity<>(runtimeDetails, HttpStatus.BAD_REQUEST);
        }
        if (runtimes.get(runtimeEngine.asText()) != null) {
            log.info("Overwriting remote address for the " + runtimeEngine + " environment. New address is: " + runtimeAddress);
        } else {
            log.info(
                    "Adding a new remote environment to the registry that can handle "
                            + runtimeEngine
                            + " and is located at "
                            + runtimeAddress);
        }
        runtimes.put(runtimeEngine.asText(), runtimeDetails);
        String thisURL = req.getRequestURL().toString();
        shelfAddress = StringUtils.substringBefore(thisURL, "/proxy/environments");
        return new ResponseEntity<>(runtimeDetails, HttpStatus.OK);
    }

    @GetMapping(value = "/proxy/environments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ArrayNode getRuntimeDetails() {
        log.info("Returning list of all available runtimes.");
        return getRuntimes();
    }

    @GetMapping(value = "/proxy/environments/{engine}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode getRuntimeDetails(@PathVariable String engine) {
        log.info(String.format("Returning info on the %s engine.", engine));
        return runtimes.get(engine);
    }

    @GetMapping(value = "/proxy/**")
    public byte[] getCodeArtifact(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        URI path = URI.create(StringUtils.substringAfter(requestURI, "proxy/"));
        return activationContext.getBinary(path);
    }

    @Override
    public List<String> getEngines() {
        return new ArrayList<>(runtimes.keySet());
    }

    @Override
    public void initialize(ActivationContext context) {
        activationContext = context;
    }

    @Override
    public Executor activate(URI absoluteLocation, URI endpointURI, JsonNode deploymentSpec) {
        String engine = deploymentSpec.at("/engine").asText();
        if (!isRemoteUp(engine)) {
            throw new AdapterException(String.format("Remote runtime %s is not online. Runtime status: %s",
                    engine, runtimes.get(engine).get("status").asText()));
        }
        String remoteServer = runtimes.get(engine).at("/url").asText();

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
                        return restTemplate.postForObject(remoteEndpoint.toString(), executionReq, JsonNode.class).get("result");
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

    public ArrayNode getRuntimes() {
        ArrayNode runtimeList = new ObjectMapper().createArrayNode();
        runtimes.forEach(
                (engine, runtimeDetails) -> {
                    runtimeList.add(updateRuntimeInfo(runtimeDetails));
                });
        return runtimeList;
    }

    private boolean isRemoteUp(String engine) {
        ObjectNode runtimeDetails = runtimes.get(engine);
        runtimes.put(engine, updateRuntimeInfo(runtimeDetails));
        return runtimes.get(engine).get("status").asText().toLowerCase().equals("up");
    }

    private ObjectNode updateRuntimeInfo(ObjectNode runtimeDetails) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(runtimeDetails.at("/url").asText() + "/info", JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                runtimeDetails = (ObjectNode) response.getBody();
            } else {
                runtimeDetails.put("status","Error while retrieving runtime status: " + response.getStatusCodeValue());
            }
        } catch (Exception e) {
            runtimeDetails.put("status", "Activator could not connect to runtime");
        }
        return runtimeDetails;
    }
}
