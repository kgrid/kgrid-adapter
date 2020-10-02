package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;

@RunWith(MockitoJUnitRunner.class)
public class ProxyAdapterTest {

  private static final String REMOTE_RUNTIME_URL = "http://url-from-info-response.com";
  private static final String PROXY_SHELF_URL = "http://url-from-activation-response.com";
  private static final String ARK_NAAN = "hello";
  private static final String ARK_NAME = "proxy";
  private static final String ARK_VERSION = "v1.0";
  private static final String ENDPOINT_NAME = "/welcome";
  private static final URI ENDPOINT_URI =
      URI.create(ARK_NAAN + "/" + ARK_NAME + "/" + ARK_VERSION + ENDPOINT_NAME);
  private static final String REMOTE_URL_HASH = "remote-hash";
  private final URI objectLocation = URI.create(ARK_NAAN + "-" + ARK_NAME + "-" + ARK_VERSION);
  @Rule public ExpectedException expected = ExpectedException.none();
  @Mock RestTemplate restTemplate;
  private ObjectMapper mapper = new ObjectMapper();
  @InjectMocks @Spy private ProxyAdapter proxyAdapter = new ProxyAdapter();

  ClassPathResource helloWorldCode = new ClassPathResource("shelf/hello-proxy-v1.0/src/welcome.js");
  private MockEnvironment env = new MockEnvironment();

  private JsonNode infoResponseBody;
  private ObjectNode deploymentDesc;
  private JsonNode activationRequestBody;
  private JsonNode activationResponseBody;
  private JsonNode executionResponseBody;
  private JsonNode input;
  private HttpHeaders headers;
  private String arkIdentifier;

  @Before
  public void setUp() throws JsonProcessingException {
    infoResponseBody = mapper.createObjectNode().put("Status", "Up").put("url", REMOTE_RUNTIME_URL);

    // For checking if remote server is up
    Mockito.when(restTemplate.getForEntity(REMOTE_RUNTIME_URL + "/info", JsonNode.class))
        .thenReturn(new ResponseEntity<>(infoResponseBody, HttpStatus.OK));

    // It all starts here
    deploymentDesc = mapper.createObjectNode();
    deploymentDesc
        .put("engine", "node")
        .put("adapter", "PROXY")
        .put("entry", "welcome.js")
        .put("function", "welcome")
        .putArray("artifact")
        .add("src/welcome.js");

    activationRequestBody = deploymentDesc.deepCopy();
    arkIdentifier = "ark:/" + ARK_NAAN + "/" + ARK_NAME + "/" + ARK_VERSION;
    activationRequestBody =
        ((ObjectNode) activationRequestBody)
            .put(
                "baseUrl",
                "http://localhost"
                    + ":"
                    + "8080"
                    + "/proxy/"
                    + ARK_NAAN
                    + "-"
                    + ARK_NAME
                    + "-"
                    + ARK_VERSION)
            .put("uri", ENDPOINT_URI.toString());

    activationResponseBody =
        mapper
            .createObjectNode()
            .put("baseUrl", PROXY_SHELF_URL)
            .put("endpoint_url", REMOTE_URL_HASH)
            .put("activated", "Tue Feb 18 2020 16:44:15 GMT-0500 (Eastern Standard Time)");

    executionResponseBody =
        mapper
            .createObjectNode()
            .put("ko", arkIdentifier)
            .put("result", "Welcome to Knowledge Grid, test");

    // For activating a remote object
    headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Happy path; good case
    Mockito.when(
            restTemplate.postForObject(
                REMOTE_RUNTIME_URL + "/deployments",
                new HttpEntity<JsonNode>(activationRequestBody, headers),
                JsonNode.class))
        .thenReturn(activationResponseBody);

    // For executing a remote object
    input = mapper.createObjectNode().put("name", "test");
    Mockito.when(
            restTemplate.postForObject(
                PROXY_SHELF_URL + "/" + REMOTE_URL_HASH,
                new HttpEntity<JsonNode>(input, headers),
                JsonNode.class))
        .thenReturn(executionResponseBody);

    // Set up the map of runtimes
    proxyAdapter.runtimes.put("node", REMOTE_RUNTIME_URL);

    proxyAdapter.initialize(
        new ActivationContext() {
          @Override
          public Executor getExecutor(String key) {
            return null;
          }

          @Override
          public byte[] getBinary(URI pathToBinary) {
            byte[] code;
            try {
              code = helloWorldCode.getInputStream().readAllBytes();
            } catch (Exception e) {
              throw new AdapterException(e.getMessage(), e);
            }
            return code;
          }

          @Override
          public String getProperty(String key) {
            return env.getProperty(key);
          }
        });
    String runtimeType = "node";
    MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
    mockHttpServletRequest.setRequestURI("/proxy/environments");
    mockHttpServletRequest.setServerPort(8080);
    ObjectNode runtimeDetailNode =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    "{\"type\":\"" + runtimeType + "\", \"url\":\"" + REMOTE_RUNTIME_URL + "\"}");
    proxyAdapter.registerRemoteRuntime(runtimeDetailNode, mockHttpServletRequest);
  }

  @Test
  public void testInitializeThrowsGoodError() {
    String randomLocation = "/" + UUID.randomUUID();
    try {
      ProxyAdapter adapter2 = new ProxyAdapter();

      env.setProperty("kgrid.adapter.proxy.url", REMOTE_RUNTIME_URL + randomLocation);
      adapter2.initialize(
          new ActivationContext() {
            @Override
            public Executor getExecutor(String key) {
              return null;
            }

            @Override
            public byte[] getBinary(URI pathToBinary) {
              return new byte[0];
            }

            @Override
            public String getProperty(String key) {
              return env.getProperty(key);
            }
          });
    } catch (AdapterException e) {
      assertEquals("Remote execution environment not online", e.getMessage().substring(0, 39));
    }
  }

  @Test
  public void testActivateRemoteObject() {
    Executor activatedHello = proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);
    assertNotNull(activatedHello);
  }

  @Test
  public void testExecuteRemoteObject() {
    Executor activatedHello = proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);
    JsonNode result = (JsonNode) activatedHello.execute(input);
    assertEquals(arkIdentifier, result.get("ko").asText());
    assertEquals("Welcome to Knowledge Grid, test", result.get("result").asText());
  }

  @Test
  public void testActivateRemoteNonexistentObject() {
    Mockito.when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    expected.expect(AdapterException.class);
    expected.expectMessage(
        String.format("Cannot activate object at address %s/deployments", REMOTE_RUNTIME_URL));
    expected.expectCause(instanceOf(HttpClientErrorException.class));

    proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);
  }

  @Test
  public void remoteRuntimeFailureGeneratesAdapterException() {
    Mockito.when(restTemplate.postForObject(anyString(), any(), eq(JsonNode.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    expected.expect(AdapterException.class);
    expected.expectMessage(
        String.format("Remote runtime server: %s is unavailable", REMOTE_RUNTIME_URL));
    expected.expectCause(instanceOf(HttpServerErrorException.class));

    proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);
  }

  @Test
  public void returnsCorrectEngine(){
    assertEquals(Collections.singletonList("node"), proxyAdapter.getEngines());
  }
}
