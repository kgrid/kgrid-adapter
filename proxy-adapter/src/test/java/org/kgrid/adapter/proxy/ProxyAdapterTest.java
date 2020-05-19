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
import org.kgrid.shelf.domain.ArkId;
import org.kgrid.shelf.repository.CompoundDigitalObjectStore;
import org.kgrid.shelf.repository.FilesystemCDOStore;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
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

  @Rule
  public ExpectedException expected = ExpectedException.none();

  private static final String REMOTE_URL_HASH = "knlME7rU6X80";
  private ObjectMapper mapper = new ObjectMapper();

  @Mock RestTemplate restTemplate;

  @InjectMocks @Spy private ProxyAdapter proxyAdapter = new ProxyAdapter();

  private CompoundDigitalObjectStore cdoStore;

  private MockEnvironment env = new MockEnvironment();
  private String remoteURL = "http://localhost:3000";

  private ArkId arkId;
  private String endpointName;
  private JsonNode infoResponseBody;
  private ObjectNode deploymentDesc;
  private JsonNode activationRequestBody;
  private JsonNode activationResponseBody;
  private JsonNode executionResponseBody;
  private JsonNode input;
  private HttpHeaders headers;

  @Before
  public void setUp() throws Exception {

    arkId = new ArkId("hello-proxy-v1.0");
    endpointName = "welcome";

    URI uri = this.getClass().getResource("/shelf").toURI();
    cdoStore = new FilesystemCDOStore("filesystem:" + uri.toString());

    env.setProperty("kgrid.adapter.proxy.port", "8082");
    env.setProperty("kgrid.adapter.proxy.vipAddress", "http://127.0.0.1");

    infoResponseBody = mapper.createObjectNode()
        .put("Status", "Up")
        .put("url", remoteURL);

    // For checking if remote server is up
    Mockito.when(restTemplate.getForEntity(remoteURL + "/info", JsonNode.class))
        .thenReturn(new ResponseEntity<>(infoResponseBody, HttpStatus.OK));

    // It all starts here
    deploymentDesc = mapper.createObjectNode();
    deploymentDesc
        .put("engine", "node")
        .put("adapter", "PROXY")
        .put("entry", "welcome.js")
        .put("function", "welcome")
        .putArray("artifact").add("src/welcome.js");

    activationRequestBody = deploymentDesc.deepCopy();
    activationRequestBody = ((ObjectNode) activationRequestBody)
        .put("baseUrl","http://127.0.0.1:8082/kos/hello/proxy/v1.0")
        .put("identifier","ark:/hello/proxy")
        .put("version","v1.0")
        .put("endpoint","welcome");

    activationResponseBody = mapper.createObjectNode()
        .put("endpoint_url", REMOTE_URL_HASH)
        .put("activated", "Tue Feb 18 2020 16:44:15 GMT-0500 (Eastern Standard Time)");

    executionResponseBody = mapper.createObjectNode()
        .put("ko","ark:/hello/proxy")
        .put("result","Welcome to Knowledge Grid, test");

    // For activating a remote object
    headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Happy path; good case
    Mockito.when(
            restTemplate.postForObject(remoteURL + "/deployments",
                new HttpEntity<JsonNode>(activationRequestBody, headers), JsonNode.class))
        .thenReturn(activationResponseBody);

    // For executing a remote object
    input = mapper.createObjectNode().put("name", "test");
    Mockito.when(
            restTemplate.postForObject(remoteURL + "/" + REMOTE_URL_HASH,
                new HttpEntity<JsonNode>(input, headers), JsonNode.class))
        .thenReturn(executionResponseBody);

    // Set up the map of runtimes
    proxyAdapter.runtimes.put("node", remoteURL);

    proxyAdapter.initialize(
        new ActivationContext() {
          @Override
          public Executor getExecutor(String key) {
            return null;
          }

          @Override
          public byte[] getBinary(String pathToBinary) {
            return cdoStore.getBinary(pathToBinary);
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

      env.setProperty("kgrid.adapter.proxy.url", remoteURL + randomLocation);
      adapter2.initialize(
          new ActivationContext() {
            @Override
            public Executor getExecutor(String key) {
              return null;
            }

            @Override
            public byte[] getBinary(String pathToBinary) {
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

    Executor activatedHello = proxyAdapter.activate("hello-proxy-v1.0", arkId, endpointName, deploymentDesc);
    assertNotNull(activatedHello);
  }

  @Test
  public void testExecuteRemoteObject() {
    Executor activatedHello = proxyAdapter.activate("hello-proxy-v1.0", arkId, endpointName, deploymentDesc);
    JsonNode result = (JsonNode) activatedHello.execute(input);
    assertEquals("ark:/hello/proxy", result.get("ko").asText());
    assertEquals("Welcome to Knowledge Grid, test", result.get("result").asText());
  }

  @Test
  public void testExecuteRemoteBadInput() {
    Executor activatedHello = proxyAdapter.activate("hello-proxy-v1.0", arkId, endpointName, deploymentDesc);
    JsonNode result = (JsonNode) activatedHello.execute(input);
    assertEquals("ark:/hello/proxy", result.get("ko").asText());
    assertEquals("Welcome to Knowledge Grid, test", result.get("result").asText());
  }

  @Test
  public void testActivateRemoteNonexistentObject() {
    Mockito.when(
        restTemplate.postForObject(
            anyString(),
            any(HttpEntity.class),
            eq(JsonNode.class)
        ))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    expected.expect(AdapterException.class);
    expected.expectMessage(String.format("Cannot activate object at address %s/deployments", remoteURL));
    expected.expectCause(instanceOf(HttpClientErrorException.class));

    proxyAdapter.activate("hello-proxy-v1.0", arkId, endpointName, deploymentDesc);
  }

  @Test
  public void remoteRuntimeFailureGeneratesAdapterException() {
    Mockito.when(
        restTemplate.postForObject(
            anyString(),
            any(),
            eq(JsonNode.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    expected.expect(AdapterException.class);
    expected.expectMessage(String.format("Remote runtime server: %s is unavailable", remoteURL));
    expected.expectCause(instanceOf(HttpServerErrorException.class));

    proxyAdapter.activate("hello-proxy-v1.0", arkId, endpointName, deploymentDesc);
  }
}
