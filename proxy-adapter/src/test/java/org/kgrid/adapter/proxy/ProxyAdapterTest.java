package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Paths;
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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class ProxyAdapterTest {

  @Mock
  RestTemplate restTemplate;

  @InjectMocks
  @Spy
  private ProxyAdapter proxyAdapter = new ProxyAdapter();

  private CompoundDigitalObjectStore cdoStore;

  static final ArkId helloWorld = new ArkId("hello-world/v1.0");
  static final String endpoint = "welcome";

  private MockEnvironment env = new MockEnvironment();
  private String remoteURL = "http://localhost:2000";

  private JsonNode infoResponseBody;
  private JsonNode activationRequestBody;
  private JsonNode activationResponseBody;
  private JsonNode executionResponseBody;
  private String input;

  @Before
  public void setUp() throws Exception {

    URI uri = this.getClass().getResource("/shelf").toURI();
    cdoStore = new FilesystemCDOStore("filesystem:" + uri.toString());

    env.setProperty("kgrid.adapter.proxy.url", remoteURL);

    infoResponseBody = new ObjectMapper().readTree(
        "{\"Status\":\"Up\",\"Url\":\"" + remoteURL + "\"}");

    // For checking if remote server is up
    Mockito.when(restTemplate.getForEntity(remoteURL + "/info", JsonNode.class))
        .thenReturn(new ResponseEntity<>(infoResponseBody, HttpStatus.OK));

    activationRequestBody = new ObjectMapper().readTree(
        "{\"arkid\":\"ark:/hello/world\","
            + "\"version\":\"v1.0\",\"default\":true,"
            + "\"endpoint\":\"welcome\",\"entry\":\"src/index.js\""
            + ",\"artifacts\":[\"https://github.com/kgrid-objects/example-collection/releases/download/2.0.0/hello-world-v1.0.zip\"]}"
    );

    activationResponseBody = new ObjectMapper().readTree("{\n"
        + "    \"arkid\": \"ark:/hello/world\",\n"
        + "    \"version\": \"v1.0\",\n"
        + "    \"endpoint_url\": \"http://localhost:2000/hello/world/welcome\",\n"
        + "    \"activated\": \"Tue Feb 11 2020 14:32:30 GMT-0500 (Eastern Standard Time)\",\n"
        + "    \"artifact\": [\n"
        + "        \"./shelf/hello-world-v1.0/hello-world-v1.0.zip\"\n"
        + "    ]\n"
        + "}");

    executionResponseBody = new ObjectMapper().readTree("{\n"
        + "    \"ko\": \"ark:/hello/world\",\n"
        + "    \"result\": \"Welcome to Knowledge Grid, test\"\n"
        + "}");


    // For activating a remote object
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> activationReq = new HttpEntity<>(activationRequestBody.toString(), headers);
    Mockito.when(restTemplate.postForObject(remoteURL + "/activate", activationReq, JsonNode.class))
        .thenReturn(activationResponseBody);

    // For executing a remote object
    input = "{\"name\":\"test\"}";
    HttpEntity<String> executionReq = new HttpEntity<>(input, headers);
    Mockito.when(restTemplate.postForObject(remoteURL + "/hello/world/welcome", executionReq, JsonNode.class))
        .thenReturn(executionResponseBody);

    proxyAdapter.initialize(new ActivationContext() {
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
      adapter2.initialize(new ActivationContext() {
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
    } catch (AdapterException  e) {
      assertEquals("Remote execution environment not online", e.getMessage().substring(0,39));
    }
  }

  @Test
  public void testActivateRemoteObject() {

    Executor activatedHello = proxyAdapter.activate(Paths.get(
        Paths.get("hello-world-v1.0", "metadata.json").toString()), "welcome");
    assertNotNull(activatedHello);
  }

  @Test
  public void testExecuteRemoteObject() {
    Executor activatedHello = proxyAdapter.activate(Paths.get(
        Paths.get("hello-world-v1.0", "metadata.json").toString()), "welcome");
    JsonNode result = (JsonNode)activatedHello.execute(input);
    assertEquals("ark:/hello/world", result.get("ko").asText());
    assertEquals("Welcome to Knowledge Grid, test", result.get("result").asText());

  }
}
