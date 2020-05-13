package org.kgrid.adapter.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@RunWith(MockitoJUnitRunner.class)
public class ProxyAdapterTest {

  public static final String DEPLOYMENT_DESCRIPTOR = "{\"artifact\":[\"src/welcome.js\"],"
      + "\"engine\":\"node\","
      + "\"adapter\":\"PROXY\",\"entry\":\"welcome.js\","
      + "\"function\":\"welcome\"}";
  @Mock RestTemplate restTemplate;

  @InjectMocks @Spy private ProxyAdapter proxyAdapter = new ProxyAdapter();

  private CompoundDigitalObjectStore cdoStore;

  private MockEnvironment env = new MockEnvironment();
  private String remoteURL = "http://localhost:3000";

  private ArkId arkId;
  private String endpointName;
  private JsonNode infoResponseBody;
  private JsonNode deploymentDesc;
  private JsonNode badDeploymentDesc;
  private JsonNode activationRequestBody;
  private JsonNode badActivationRequestBody;
  private JsonNode activationResponseBody;
  private JsonNode activationErrorResponseBody;
  private JsonNode executionResponseBody;
  private JsonNode input;
  private ObjectMapper mapper = new ObjectMapper();

  @Before
  public void setUp() throws Exception {

    arkId = new ArkId("hello-proxy-v1.0");
    endpointName = "welcome";

    URI uri = this.getClass().getResource("/shelf").toURI();
    cdoStore = new FilesystemCDOStore("filesystem:" + uri.toString());

    env.setProperty("kgrid.adapter.proxy.port", "8082");
    env.setProperty("kgrid.adapter.proxy.vipAddress", "http://127.0.0.1");

    infoResponseBody =
        mapper.readTree("{\"Status\":\"Up\",\"Url\":\"" + remoteURL + "\"}");

    // For checking if remote server is up
    Mockito.when(restTemplate.getForEntity(remoteURL + "/info", JsonNode.class))
        .thenReturn(new ResponseEntity<>(infoResponseBody, HttpStatus.OK));

    deploymentDesc = mapper.readTree(DEPLOYMENT_DESCRIPTOR);

    activationRequestBody = deploymentDesc.deepCopy();
    activationRequestBody = ((ObjectNode) activationRequestBody)
        .put("baseUrl","http://127.0.0.1:8082/kos/hello/proxy/v1.0")
        .put("identifier","ark:/hello/proxy")
        .put("version","v1.0")
        .put("endpoint","welcome");

    badDeploymentDesc = deploymentDesc.deepCopy();
    ((ObjectNode) badDeploymentDesc)
        .put("entry", "notthere.js")
        .putArray("artifact").add("src/notthere.js");

    badActivationRequestBody = ((ObjectNode) badDeploymentDesc)
        .put("baseUrl","http://127.0.0.1:8082/kos/hello/proxy/v1.0")
        .put("identifier","ark:/hello/proxy")
        .put("version","v1.0")
        .put("endpoint","welcome");

    activationResponseBody = mapper
            .readTree(
                "{\n"
                    + "    \"endpoint_url\": \""
                    + remoteURL
                    + "/knlME7rU6X80\",\n"
                    + "    \"activated\": \"Tue Feb 18 2020 16:44:15 GMT-0500 (Eastern Standard Time)\"\n"
                    + "}");

    activationErrorResponseBody = mapper
            .readTree(
                "{\n"
                    + "    \"Error\": \"Cannot download http://127.0.0.1:8082/kos/hello/proxy/v1.0/src/notthere.js\"\n"
                    + "}");

    executionResponseBody = mapper
            .readTree(
                "{\n"
                    + "    \"ko\": \"ark:/hello/proxy\",\n"
                    + "    \"result\": \"Welcome to Knowledge Grid, test\"\n"
                    + "}");

    // For activating a remote object
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<JsonNode> activationReq = new HttpEntity<>(activationRequestBody, headers);

    Mockito.when(
            restTemplate.postForObject(remoteURL + "/deployments", activationReq, JsonNode.class))
        .thenReturn(activationResponseBody);

    HttpEntity<JsonNode> badActivationReq =
        new HttpEntity<>(badActivationRequestBody, headers);

    Mockito.when(
            restTemplate.postForObject(
                remoteURL + "/deployments", badActivationReq, JsonNode.class))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    // For executing a remote object
    input = mapper.readTree("{\"name\":\"test\"}");
    HttpEntity<JsonNode> executionReq = new HttpEntity<>(input, headers);
    Mockito.when(
            restTemplate.postForObject(remoteURL + "/knlME7rU6X80", executionReq, JsonNode.class))
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
  public void testActivateRemoteNonexistantObject() {
    try {
      Executor activatedHello = proxyAdapter.activate("hello-proxy-v1.0", arkId, endpointName, badDeploymentDesc);
    } catch (AdapterException e) {
      assertTrue(
          e.getMessage()
              .startsWith(
                  "Cannot activate object at address " + remoteURL + "/deployments with body"));
    }
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
  public void remoteRuntimeTimeoutExceptionLoggedAndTranslated() {

  }
}
