package org.kgrid.adapter.proxy;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;

@RunWith(MockitoJUnitRunner.class)
public class ProxyAdapterTest {

  private static final String URL_FROM_INFO_RESPONSE = "http://url-from-info-response.com";
  private static final String URL_FROM_ACTIVATION_RESPONSE =
      "http://url-from-activation-response.com";
  private static final String ARK_NAAN = "hello";
  private static final String ARK_NAME = "proxy";
  private static final String ARK_VERSION = "v1.0";
  private static final String PROXY_PORT = "port";
  private static final String PROXY_VIP_ADDRESS = "proxy-address";
  private static final String REMOTE_URL_HASH = "remote-hash";
  private final URI objectLocation = URI.create(ARK_NAAN + "-" + ARK_NAME + "-" + ARK_VERSION);
  @Rule public ExpectedException expected = ExpectedException.none();
  @Mock RestTemplate restTemplate;
  private ObjectMapper mapper = new ObjectMapper();
  @InjectMocks @Spy private ProxyAdapter proxyAdapter = new ProxyAdapter();

  ClassPathResource helloWorldCode = new ClassPathResource("shelf/hello-proxy-v1.0/src/welcome.js");
  private MockEnvironment env = new MockEnvironment();

  private String arkId;
  private String endpointName;
  private JsonNode infoResponseBody;
  private ObjectNode deploymentDesc;
  private JsonNode activationRequestBody;
  private JsonNode activationResponseBody;
  private JsonNode executionResponseBody;
  private JsonNode input;
  private HttpHeaders headers;
  private String arkIdentifier;

  @Before
  public void setUp() throws Exception {

    endpointName = "/welcome";

    URI uri = getClass().getResource("/shelf").toURI();


    env.setProperty("kgrid.adapter.proxy.port", PROXY_PORT);
    env.setProperty("kgrid.adapter.proxy.vipAddress", PROXY_VIP_ADDRESS);

    infoResponseBody =
        mapper.createObjectNode().put("Status", "Up").put("url", URL_FROM_INFO_RESPONSE);

    // For checking if remote server is up
    Mockito.when(restTemplate.getForEntity(URL_FROM_INFO_RESPONSE + "/info", JsonNode.class))
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
                PROXY_VIP_ADDRESS
                    + ":"
                    + PROXY_PORT
                    + "/proxy/"
                    + ARK_NAAN
                    + "-"
                    + ARK_NAME
                    + "-"
                    + ARK_VERSION)
            .put("identifier", arkIdentifier)
            .put("version", ARK_VERSION)
            .put("endpoint", "/welcome");

    activationResponseBody =
        mapper
            .createObjectNode()
            .put("baseUrl", URL_FROM_ACTIVATION_RESPONSE)
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
                URL_FROM_INFO_RESPONSE + "/deployments",
                new HttpEntity<JsonNode>(activationRequestBody, headers),
                JsonNode.class))
        .thenReturn(activationResponseBody);

    // For executing a remote object
    input = mapper.createObjectNode().put("name", "test");
    Mockito.when(
            restTemplate.postForObject(
                URL_FROM_ACTIVATION_RESPONSE + "/" + REMOTE_URL_HASH,
                new HttpEntity<JsonNode>(input, headers),
                JsonNode.class))
        .thenReturn(executionResponseBody);

    // Set up the map of runtimes
    proxyAdapter.runtimes.put("node", URL_FROM_INFO_RESPONSE);

    proxyAdapter.initialize(
        new ActivationContext() {
          @Override
          public Executor getExecutor(String key) {
            return null;
          }

          @Override
          public byte[] getBinary(URI pathToBinary) {
            byte[] code = null;
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
  }

  @Test
  public void testInitializeThrowsGoodError() {
    String randomLocation = "/" + UUID.randomUUID();
    try {
      ProxyAdapter adapter2 = new ProxyAdapter();

      env.setProperty("kgrid.adapter.proxy.url", URL_FROM_INFO_RESPONSE + randomLocation);
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

    Executor activatedHello =
        proxyAdapter.activate(
            objectLocation, ARK_NAAN, ARK_NAME, ARK_VERSION, endpointName, deploymentDesc);
    assertNotNull(activatedHello);
  }

  @Test
  public void testExecuteRemoteObject() {
    Executor activatedHello =
        proxyAdapter.activate(
            objectLocation, ARK_NAAN, ARK_NAME, ARK_VERSION, endpointName, deploymentDesc);
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
        String.format("Cannot activate object at address %s/deployments", URL_FROM_INFO_RESPONSE));
    expected.expectCause(instanceOf(HttpClientErrorException.class));

    proxyAdapter.activate(objectLocation, ARK_NAAN, ARK_NAME, ARK_VERSION, endpointName, deploymentDesc);
  }

  @Test
  public void remoteRuntimeFailureGeneratesAdapterException() {
    Mockito.when(restTemplate.postForObject(anyString(), any(), eq(JsonNode.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    expected.expect(AdapterException.class);
    expected.expectMessage(
        String.format("Remote runtime server: %s is unavailable", URL_FROM_INFO_RESPONSE));
    expected.expectCause(instanceOf(HttpServerErrorException.class));

    proxyAdapter.activate(objectLocation, ARK_NAAN, ARK_NAME, ARK_VERSION, endpointName, deploymentDesc);
  }
}
