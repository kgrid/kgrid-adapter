package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.kgrid.adapter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@CrossOrigin
@RestController
@EnableAsync
@RequestMapping("/proxy")
public class ProxyAdapter implements Adapter {
    static Map<String, ObjectNode> runtimes = new HashMap<>();
    static String koArtifactsBaseUrl;
    static ActivationContext activationContext;
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping(
            value = "/environments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> registerRemoteRuntime(
            @RequestBody ObjectNode runtimeDetails, HttpServletRequest req) {

        boolean update;

        JsonNode runtimeEngine = runtimeDetails.at("/engine");
        JsonNode runtimeAddress = runtimeDetails.at("/url");
        JsonNode runtimeForceUpdate = runtimeDetails.at("/forceUpdate");
        boolean forceUpdate = false;
        if (runtimeForceUpdate != null) {
            forceUpdate = runtimeForceUpdate.asBoolean();
        }
        if (runtimeEngine.isMissingNode() || runtimeEngine.asText().equals("")) {
            runtimeDetails.put("status", "Not registered: Runtime failed to specify engine");
            return new ResponseEntity<>(runtimeDetails, HttpStatus.BAD_REQUEST);
        }
        if (runtimeAddress.isMissingNode() || runtimeAddress.asText().equals("")) {
            runtimeDetails.put("status", "Not registered: Runtime failed to specify its url");
            return new ResponseEntity<>(runtimeDetails, HttpStatus.BAD_REQUEST);
        }
        if (runtimes.get(runtimeEngine.asText()) != null) {
            if (runtimeAddress == runtimes.get(runtimeEngine.asText()).get("url"))
                log.info("Overwriting remote address for the " + runtimeEngine + " environment. New address is: " + runtimeAddress);
            runtimeDetails.put("status", "existing runtime");
            update = forceUpdate;
        } else {
            runtimeDetails.put("status", "new");
            log.info(
                    "Adding a new remote environment to the registry that can handle "
                            + runtimeEngine
                            + " and is located at "
                            + runtimeAddress);
            update = true;
        }
        runtimes.put(runtimeEngine.asText(), runtimeDetails);
        String thisURL = req.getRequestURL().toString();
        koArtifactsBaseUrl = StringUtils.substringBefore(thisURL, "/proxy/environments");
        if (update) {
            activationContext.refresh(runtimeEngine.asText());
        }
        log.debug("Runtime Registration is completed");

        return new ResponseEntity<>(runtimeDetails, HttpStatus.OK);
    }


    @GetMapping(value = "/environments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ArrayNode getRuntimeDetails() {
        log.info("Returning list of all available runtimes.");
        return getRuntimes();
    }

    @GetMapping(value = "/environments/{engine}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode getRuntimeDetails(@PathVariable String engine) {
        log.info(String.format("Returning info on the %s engine.", engine));
        return runtimes.get(engine);
    }

    @GetMapping(value = "/artifacts/**")
    public InputStreamResource getCodeArtifact(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        URI path = URI.create(StringUtils.substringAfter(requestURI, "proxy/artifacts/"));
        return new InputStreamResource(activationContext.getBinary(path));
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
            throw new AdapterServerErrorException(
                    String.format("Remote runtime %s is not online. Runtime status: %s.",
                            engine,
                            runtimes.get(engine).get("status").asText()));
        }
        String remoteServer = runtimes.get(engine).at("/url").asText();

        try {
            String proxyEndpoint = "proxy/artifacts";  // or /proxy/{naan}/{name}/**
            ((ObjectNode) deploymentSpec)
                    .put("baseUrl", String.format("%s/%s/%s", koArtifactsBaseUrl, proxyEndpoint, absoluteLocation));

            ((ObjectNode) deploymentSpec).put("uri", endpointURI.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            HttpEntity<JsonNode> activationReq = new HttpEntity<>(deploymentSpec, headers);
            long startTime = System.currentTimeMillis();
            JsonNode activationResult =
                    restTemplate.postForObject(remoteServer + "/endpoints", activationReq, JsonNode.class);
            long endTime = System.currentTimeMillis();
            URL remoteServerUrl =
                    (null == activationResult.get("baseUrl"))
                            ? new URL(remoteServer)
                            : new URL(activationResult.get("baseUrl").asText());
            URL remoteEndpoint = new URL(remoteServerUrl, activationResult.get("uri").asText());

            long time = endTime - startTime;
            log.debug(
                    "Activation result: "
                            + activationResult
                            + " time: "
                            + time
                            + " milliseconds.");

            return new Executor() {
                @Override
                public Object execute(Object input, String contentType) {
                    try {
                        headers.setContentType(MediaType.valueOf(contentType));
                        HttpEntity<Object> executionReq = new HttpEntity<>(input, headers);
                        String runtimeResult = restTemplate.postForObject(
                                remoteEndpoint.toString(), executionReq, String.class);
                        try {
                            JsonNode resultJson = new ObjectMapper().readTree(runtimeResult);
                            if (resultJson.has("result")) {
                                return resultJson.get("result");
                            }
                            return resultJson;
                        } catch (Exception e) {
                            log.error(e.getMessage());
                        }
                        return runtimeResult;
                    } catch (HttpClientErrorException e) {
                        throw new AdapterClientErrorException(e.getMessage(), e);
                    } catch (HttpServerErrorException e) {
                        throw new AdapterServerErrorException(e.getMessage(), e);
                    } catch (Exception e) {
                        throw new AdapterException(e.getMessage(), e);
                    }
                }
            };
        } catch (HttpClientErrorException e) {
            throw new AdapterClientErrorException(e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            throw new AdapterServerErrorException(e.getMessage(), e);
        } catch (Exception e) {
            throw new AdapterException(e.getMessage(), e);
        }
    }

    @Override
    public String status() {
        if (activationContext != null) {
            return "up";
        } else {
            return "down";
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
                runtimeDetails.put("status", "Error while retrieving runtime status: " + response.getStatusCodeValue());
            }
        } catch (Exception e) {
            runtimeDetails.put("status", "Activator could not connect to runtime");
        }
        return runtimeDetails;
    }
}
