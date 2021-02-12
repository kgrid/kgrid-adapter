package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kgrid.adapter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Proxy Adapter Tests")
public class ProxyAdapterTest {

  @InjectMocks
  ProxyAdapter proxyAdapter;

  @Mock
  RestTemplate restTemplate;

  @Mock
  ProxyActivationController proxyActivationController;

  private static final String REMOTE_RUNTIME_URL = "http://remote-runtime.com";
  private static final String PROXY_SHELF_URL = "http://proxy-adapter.com";
  private static final String NAAN = "hello";
  private static final String NAME = "proxy";
  private static final String API_VERSION = "v1.0";
  private static final String ENDPOINT_NAME = "welcome";
  private static final URI ENDPOINT_URI =
      URI.create(String.format("%s/%s/%s/%s", NAAN, NAME, API_VERSION, ENDPOINT_NAME));
  private static final String REMOTE_URL_HASH = "remote-hash";
  private static final String TYPE_JSON = "application/json";
  public static final String NODE_ENGINE = "node";
  public static final String NODE_VERSION = "1.0";
  public static final String RUNTIME_EXECUTE_RESPONSE = "response from runtime";
  private final String ERROR_MESSAGE = "Kaboom, baby";
  private final URI objectLocation = URI.create(String.format("%s-%s-%s", NAAN, NAME, API_VERSION));
  private final ObjectMapper mapper = new ObjectMapper();

  ClassPathResource helloWorldCode = new ClassPathResource("shelf/hello-proxy-v1.0/src/welcome.js");
  private final MockEnvironment env = new MockEnvironment();
  private JsonNode infoResponseBody;
  private final ObjectNode deploymentDesc = mapper.createObjectNode();
  private ObjectNode activationRequestBody = mapper.createObjectNode();
  private final ObjectNode activationResponseBody = mapper.createObjectNode();
  private final JsonNode input = mapper.createObjectNode().put("name", "test");
  private final HttpHeaders headers = new HttpHeaders();
  private final MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();

  @BeforeEach
  public void setUp() throws JsonProcessingException {
    setUpResponseBodies();
    headers.setContentType(MediaType.APPLICATION_JSON);

    proxyAdapter.initialize(
        new ActivationContext() {
          @Override
          public Executor getExecutor(String key) {
            return null;
          }

          @Override
          public InputStream getBinary(URI pathToBinary) {
            InputStream code;
            try {
              code = helloWorldCode.getInputStream();
            } catch (Exception e) {
              throw new AdapterException(e.getMessage(), e);
            }
            return code;
          }

          @Override
          public String getProperty(String key) {
            return env.getProperty(key);
          }

          @Override
          public void reactivate(String engineName) {}
        });
    mockHttpServletRequest.setRequestURI("/proxy/environments");
    mockHttpServletRequest.setServerPort(8080);
    ObjectNode runtimeDetailNode =
        (ObjectNode)
            mapper.readTree(
                    "{\"engine\":\""
                        + NODE_ENGINE
                        + "\", \"version\":\""
                        + NODE_VERSION
                        + "\", \"forceUpdate\":\"false\",\"url\":\""
                        + REMOTE_RUNTIME_URL
                        + "\"}");
    proxyAdapter.registerRemoteRuntime(runtimeDetailNode, mockHttpServletRequest);

    Mockito.lenient()
        .when(
            restTemplate.postForObject(
                REMOTE_RUNTIME_URL + "/endpoints",
                new HttpEntity<>(activationRequestBody, headers),
                JsonNode.class))
        .thenReturn(activationResponseBody);
    Mockito.lenient()
        .when(restTemplate.getForEntity(REMOTE_RUNTIME_URL + "/info", JsonNode.class))
        .thenReturn(new ResponseEntity<>(infoResponseBody, HttpStatus.OK));
  }

  @Test
  @DisplayName("Initialize fails with good error")
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
            public InputStream getBinary(URI pathToBinary) {
              return null;
            }

            @Override
            public String getProperty(String key) {
              return env.getProperty(key);
            }

            @Override
            public void reactivate(String engineName) {

            }
          });
    } catch (AdapterException e) {
      assertEquals("Remote execution environment not online", e.getMessage().substring(0, 39));
    }
  }

  @Test
  @DisplayName("Execute remote object")
  public void testExecuteRemoteObject_whenStringIsReturned() {
    Executor activatedHello = proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);

    when(restTemplate.postForObject(
            PROXY_SHELF_URL + "/" + REMOTE_URL_HASH,
            new HttpEntity<>(input, headers),
            String.class))
        .thenReturn(RUNTIME_EXECUTE_RESPONSE);
    String result = (String) activatedHello.execute(input, TYPE_JSON);
    assertAll(
        () -> assertNotNull(activatedHello), () -> assertEquals(RUNTIME_EXECUTE_RESPONSE, result));
  }

  @Test
  @DisplayName("Execute remote object gets result json")
  public void testExecuteRemoteObject_whenJsonIsReturned_WithResult() {
    when(restTemplate.postForObject(
            PROXY_SHELF_URL + "/" + REMOTE_URL_HASH,
            new HttpEntity<>(input, headers),
            String.class))
        .thenReturn("{\"result\":\"" + RUNTIME_EXECUTE_RESPONSE + "\"}");
    Executor activatedHello = proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);
    JsonNode result = (JsonNode) activatedHello.execute(input, TYPE_JSON);
    assertEquals(RUNTIME_EXECUTE_RESPONSE, result.asText());
  }

  @Test
  @DisplayName("Execute remote object only receives json object")
  public void testExecuteRemoteObject_whenJsonIsReturned_WithNoResult() {
    String returnedJson = "{\"somethingElse\":\"" + RUNTIME_EXECUTE_RESPONSE + "\"}";
    when(restTemplate.postForObject(
            PROXY_SHELF_URL + "/" + REMOTE_URL_HASH,
            new HttpEntity<>(input, headers),
            String.class))
        .thenReturn(returnedJson);
    Executor activatedHello = proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);
    JsonNode result = (JsonNode) activatedHello.execute(input, TYPE_JSON);
    assertEquals(returnedJson, result.toString());
  }

  @Test
  @DisplayName("Execute remote object handles client error")
  public void testExecuteRemoteObject_ThrowsAdapterClientErrorException() {
    when(restTemplate.postForObject(
            PROXY_SHELF_URL + "/" + REMOTE_URL_HASH,
            new HttpEntity<>(input, headers),
            String.class))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, ERROR_MESSAGE));
    Executor executor = proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);

    AdapterClientErrorException exception =
        assertThrows(
            AdapterClientErrorException.class,
            () ->
                executor.execute(
                    input, Objects.requireNonNull(headers.getContentType()).toString()));
    assertEquals("400 " + ERROR_MESSAGE, exception.getMessage());
  }

  @Test
  @DisplayName("Execute remote object handles server error")
  public void testExecuteRemoteObject_ThrowsAdapterServerErrorException() {
    when(restTemplate.postForObject(
            PROXY_SHELF_URL + "/" + REMOTE_URL_HASH,
            new HttpEntity<>(input, headers),
            String.class))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_MESSAGE));
    Executor executor = proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);

    AdapterServerErrorException exception =
        assertThrows(
            AdapterServerErrorException.class,
            () ->
                executor.execute(
                    input, Objects.requireNonNull(headers.getContentType()).toString()));
    assertEquals("500 " + ERROR_MESSAGE, exception.getMessage());
  }

  @Test
  @DisplayName("Execute remote object handles other errors")
  public void testExecuteRemoteObject_ThrowsAdapterExceptionForNonClientOrServerExceptions() {
    when(restTemplate.postForObject(
            PROXY_SHELF_URL + "/" + REMOTE_URL_HASH,
            new HttpEntity<>(input, headers),
            String.class))
        .thenThrow(new RuntimeException(ERROR_MESSAGE));
    Executor executor = proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);

    AdapterException exception =
        assertThrows(
            AdapterException.class,
            () ->
                executor.execute(
                    input, Objects.requireNonNull(headers.getContentType()).toString()));
    assertEquals(ERROR_MESSAGE, exception.getMessage());
  }

  @Test
  @DisplayName("Execute remote object handles remote runtime down")
  public void testActivateThrowsAdapterServerError_IfRemoteIsDown() {
    when(restTemplate.getForEntity(REMOTE_RUNTIME_URL + "/info", JsonNode.class))
        .thenThrow(new RuntimeException(ERROR_MESSAGE));

    AdapterServerErrorException exception =
        assertThrows(
            AdapterServerErrorException.class,
            () -> proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc));
    assertEquals(
        String.format(
            "Remote runtime %s is not online. Runtime status: Activator could not connect to runtime.",
            NODE_ENGINE),
        exception.getMessage());
  }

  @Test
  @DisplayName("Activation handles remote client error")
  public void testActivateThrowsAdapterClientError_WhenClientErrorDuringActivation() {
    when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                ERROR_MESSAGE,
                ERROR_MESSAGE.getBytes(),
                Charset.defaultCharset()));

    Exception expected =
        assertThrows(
            AdapterClientErrorException.class,
            () -> proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc));
    assertEquals("400 " + ERROR_MESSAGE, expected.getMessage());
    assertEquals(HttpClientErrorException.class, expected.getCause().getClass());
  }

  @Test
  @DisplayName("Activation handles remote server error")
  public void testActivateThrowsAdapterServerError_WhenServerErrorDuringActivation() {
    when(restTemplate.postForObject(anyString(), any(), eq(JsonNode.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_MESSAGE));

    Exception expected =
        assertThrows(
            AdapterServerErrorException.class,
            () -> proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc));
    assertEquals("500 " + ERROR_MESSAGE, expected.getMessage());
    assertEquals(HttpServerErrorException.class, expected.getCause().getClass());
  }

  @Test
  @DisplayName("Activation handles remote other errors")
  public void testActivateThrowsAdapterError_WhenGeneralErrorDuringActivation() {
    when(restTemplate.postForObject(anyString(), any(), eq(JsonNode.class)))
        .thenThrow(new RuntimeException(ERROR_MESSAGE));

    Exception expected =
        assertThrows(
            AdapterException.class,
            () -> proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc));
    assertEquals(ERROR_MESSAGE, expected.getMessage());
    assertEquals(RuntimeException.class, expected.getCause().getClass());
  }

  @Test
  @DisplayName("Engine list contains engines")
  public void returnsCorrectEngine() {
    assertEquals(Collections.singletonList(NODE_ENGINE), proxyAdapter.getEngines());
  }

  @Test
  @DisplayName("Proxy api returns environment list")
  public void returnsEnvironmentList() {
    ArrayNode environments = new ObjectMapper().createArrayNode();
    ObjectNode envDetails = new ObjectMapper().createObjectNode();
    envDetails.put("status", "up");
    envDetails.put("url", "http://remote-runtime.com");
    environments.add(envDetails);
    assertEquals(environments, proxyAdapter.getRuntimeDetails());
  }

  @Test
  @DisplayName("Proxy api returns details for one environment")
  public void returnsEnvironmentForEngine() {
    assertEquals("node", proxyAdapter.getRuntimeDetails(NODE_ENGINE).get("engine").asText());
  }

  @Test
  @DisplayName("Proxy api returns binary files")
  public void returnsCodeArtifact() throws IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("http://activator.com/proxy/artifacts/naan/name/version/artifact.js");
    assertArrayEquals(
        helloWorldCode.getInputStream().readAllBytes(),
        proxyAdapter.getCodeArtifact(request).getInputStream().readAllBytes());
  }

  @Test
  @DisplayName("Proxy adapter has status")
  public void returnsStatus() {
    assertEquals("up", proxyAdapter.status());
    ProxyAdapter proxyAdapter1 = new ProxyAdapter();
    proxyAdapter1.initialize(null);
    assertEquals("down", proxyAdapter1.status());
  }

  private void setUpResponseBodies() {
    infoResponseBody = mapper.createObjectNode().put("status", "up").put("url", REMOTE_RUNTIME_URL);
    deploymentDesc
        .put("engine", NODE_ENGINE)
        .put("adapter", "PROXY")
        .put("entry", "welcome.js")
        .put("function", "welcome")
        .putArray("artifact")
        .add("src/welcome.js");
    activationRequestBody = deploymentDesc.deepCopy();
    activationRequestBody
        .put(
            "baseUrl",
            "http://localhost"
                + ":"
                + "8080"
                + "/proxy/artifacts/"
                + NAAN
                + "-"
                + NAME
                + "-"
                + API_VERSION)
        .put("uri", ENDPOINT_URI.toString());
    activationResponseBody
        .put("baseUrl", PROXY_SHELF_URL)
        .put("uri", REMOTE_URL_HASH)
        .put("activated", "Tue Feb 18 2020 16:44:15 GMT-0500 (Eastern Standard Time)");
  }
}
