package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.bytebuddy.description.NamedElement;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;

@RunWith(MockitoJUnitRunner.class)
public class ProxyAdapterTest {
    private static final String REMOTE_RUNTIME_URL = "http://remote-runtime.com";
    private static final String PROXY_SHELF_URL = "http://proxy-adapter.com";
    private static final String NAAN = "hello";
    private static final String NAME = "proxy";
    private static final String API_VERSION = "v1.0";
    private static final String ENDPOINT_NAME = "welcome";
    private static final URI ENDPOINT_URI =
            URI.create(NAAN + "/" + NAME + "/" + API_VERSION + "/" + ENDPOINT_NAME);
    private static final String REMOTE_URL_HASH = "remote-hash";
    private static final String TYPE_JSON = "application/json";
    public static final String NODE_ENGINE = "node";
    public static final String NODE_VERSION = "1.0";
    private String ERROR_MESSAGE = "Kaboom, baby";
    private final URI objectLocation = URI.create(NAAN + "-" + NAME + "-" + API_VERSION);

    private ObjectMapper mapper = new ObjectMapper();

    @Rule
    public ExpectedException expected = ExpectedException.none();
    @Mock
    RestTemplate restTemplate;
    @InjectMocks
    private ProxyAdapter proxyAdapter;

    ClassPathResource helloWorldCode = new ClassPathResource("shelf/hello-proxy-v1.0/src/welcome.js");
    private MockEnvironment env = new MockEnvironment();
    private JsonNode infoResponseBody;
    private ObjectNode deploymentDesc = mapper.createObjectNode();
    private ObjectNode activationRequestBody = mapper.createObjectNode();
    private ObjectNode activationResponseBody = mapper.createObjectNode();
    private ObjectNode executionResponseBody = mapper.createObjectNode();
    private JsonNode input;
    private HttpHeaders headers = new HttpHeaders();
    private ObjectNode runtimeDetailNode;
    private MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();

    @Before
    public void setUp() throws JsonProcessingException {
        setUpResponseBodies();
        headers.setContentType(MediaType.APPLICATION_JSON);
        input = mapper.createObjectNode().put("name", "test");

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
        mockHttpServletRequest.setRequestURI("/proxy/environments");
        mockHttpServletRequest.setServerPort(8080);
        runtimeDetailNode = (ObjectNode)
                new ObjectMapper()
                        .readTree(
                                "{\"engine\":\"" + NODE_ENGINE + "\", \"version\":\"" + NODE_VERSION + "\", \"url\":\"" + REMOTE_RUNTIME_URL + "\"}");
        proxyAdapter.registerRemoteRuntime(runtimeDetailNode, mockHttpServletRequest);

        Mockito.when(
                restTemplate.postForObject(
                        REMOTE_RUNTIME_URL + "/endpoints",
                        new HttpEntity<>(activationRequestBody, headers),
                        JsonNode.class))
                .thenReturn(activationResponseBody);
        Mockito.when(restTemplate.getForEntity(REMOTE_RUNTIME_URL + "/info", JsonNode.class))
                .thenReturn(new ResponseEntity<>(infoResponseBody, HttpStatus.OK));
        Mockito.when(
                restTemplate.postForObject(
                        PROXY_SHELF_URL + "/" + REMOTE_URL_HASH,
                        new HttpEntity<>(input, headers),
                        JsonNode.class))
                .thenReturn(executionResponseBody);
    }

    @After
    public void tearDown() {
        proxyAdapter.runtimes.remove(NODE_ENGINE);
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
        JsonNode result = (JsonNode) activatedHello.execute(input, TYPE_JSON);
        assertEquals("Welcome to Knowledge Grid, test", result.asText());
    }

    @Test
    public void testActivateRemoteNonexistentObject() {
        Mockito.when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, ERROR_MESSAGE, ERROR_MESSAGE.getBytes(), Charset.defaultCharset()));

        expected.expect(AdapterException.class);
        expected.expectMessage(
                String.format("Client error activating object at address %s/deployments: %s",
                        REMOTE_RUNTIME_URL, ERROR_MESSAGE));
        expected.expectCause(instanceOf(HttpClientErrorException.class));

        proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);
    }

    @Test
    public void remoteRuntimeFailureGeneratesAdapterException() {
        Mockito.when(restTemplate.postForObject(anyString(), any(), eq(JsonNode.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_MESSAGE));

        expected.expect(AdapterException.class);
        expected.expectMessage(
                String.format("Remote runtime %s server error: %s",
                        REMOTE_RUNTIME_URL, "500 " + ERROR_MESSAGE));
        expected.expectCause(instanceOf(HttpServerErrorException.class));

        proxyAdapter.activate(objectLocation, ENDPOINT_URI, deploymentDesc);
    }

    @Test
    public void returnsCorrectEngine() {
        assertEquals(Collections.singletonList(NODE_ENGINE), proxyAdapter.getEngines());
    }

    private void setUpResponseBodies() {
        infoResponseBody = mapper.createObjectNode()
                .put("status", "up")
                .put("url", REMOTE_RUNTIME_URL);
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
                                + "/proxy/"
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
        executionResponseBody
                .put("result", "Welcome to Knowledge Grid, test");
    }
}
