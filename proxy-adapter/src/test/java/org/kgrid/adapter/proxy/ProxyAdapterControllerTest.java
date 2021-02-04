package org.kgrid.adapter.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.net.URI;
import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kgrid.adapter.api.ActivationContext;

@DisplayName("Proxy Adapter Controller Tests")
public class ProxyAdapterControllerTest {

  @Test
  @DisplayName("Get code artifact returns binary")
  public void testGetCodeArtifactHandlesActualNameIsProxy() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    ActivationContext ctx = mock(ActivationContext.class);
    ProxyAdapter adapter = new ProxyAdapter();
    adapter.initialize(ctx);

    given(ctx.getBinary(any(URI.class))).willReturn(new ByteArrayInputStream("Hi, Bob".getBytes()));
    given(req.getRequestURI())
        .willReturn("/proxy/artifacts/proxy/name/version/src/index.js")
        .willReturn("/proxy/artifacts/naan/name/version/src/index.js");

    adapter.getCodeArtifact(req);

    then(ctx).should().getBinary(URI.create("proxy/name/version/src/index.js"));

    adapter.getCodeArtifact(req);

    then(ctx).should().getBinary(URI.create("naan/name/version/src/index.js"));
  }
}
