package org.kgrid.adapter.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.io.ByteArrayInputStream;
import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;
import org.kgrid.adapter.api.ActivationContext;

public class ProxyAdapterControllerTest {

  public void setUp() throws Exception {
  }

  @Test
  public void testGetCodeArtifactHandlesActualNameIsProxy() {
    // given
    HttpServletRequest req = mock(HttpServletRequest.class);
    ActivationContext ctx = mock(ActivationContext.class);
    ProxyAdapter adapter = new ProxyAdapter();
    adapter.initialize(ctx);

    // and
    given(ctx.getBinary(any(URI.class))).willReturn(new ByteArrayInputStream("Hi, Bob".getBytes()));
    given(req.getRequestURI())
        .willReturn("/proxy/proxy/name/version/src/index.js")
        .willReturn("/proxy/naan/name/version/src/index.js");

    adapter.getCodeArtifact(req);

    then(ctx).should().getBinary(URI.create("proxy/name/version/src/index.js"));

    // and also
    adapter.getCodeArtifact(req);

    then(ctx).should().getBinary(URI.create("naan/name/version/src/index.js"));
  }
}
