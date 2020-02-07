package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class ProxyAdapterTest {

  @Mock
  RestTemplate restTemplate;

  @InjectMocks
  @Spy
  private ProxyAdapter proxyAdapter = new ProxyAdapter();

  private CompoundDigitalObjectStore cdoStore;

  static final ArkId A_B_C = new ArkId("a-b/c");
  static final String endpoint = "welcome.js";

  private MockEnvironment env = new MockEnvironment();
  private String remoteURL = "http://localhost:2000";

  private ObjectNode activationResponseBody;

  @Before
  public void setUp() throws URISyntaxException {

    URI uri = this.getClass().getResource("/shelf").toURI();
    cdoStore = new FilesystemCDOStore("filesystem:" + uri.toString());

    env.setProperty("kgrid.adapter.proxy.url", remoteURL);

    Mockito.when(restTemplate.getForEntity(remoteURL, String.class))
        .thenReturn(new ResponseEntity<>("up", HttpStatus.OK));


    activationResponseBody = new ObjectMapper().createObjectNode();
    activationResponseBody.put("activated", true);
    activationResponseBody.put("handle", A_B_C.getDashArkVersion() + "/" + endpoint );

    Mockito.when(restTemplate.postForObject(Mockito.anyString(), Mockito.any(), Mockito.any()))
        .thenReturn(activationResponseBody);

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
      assertEquals("Remote execution environment not online, no response from " + remoteURL + randomLocation, e.getMessage());
    }
  }

  @Test
  public void testActivateRemoteObject() {

    ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    Executor activatedHello = proxyAdapter.activate(Paths.get(
        Paths.get("a-b-c", "welcome.js").toString()), "welcome");

  }

  @Test
  public void testExecuteRemoteObject() {
    Executor activatedHello = proxyAdapter.activate(Paths.get(
        Paths.get("a-b-c", "welcome.js").toString()), "welcome");
    Object result = activatedHello.execute("test");
    System.out.println(result);

  }
}
