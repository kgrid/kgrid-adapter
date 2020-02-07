package org.kgrid.adapter.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class ProxyAdapter implements Adapter {

  private String remoteServer;

  private boolean remoteUp = false;

  RestTemplate restTemplate = new RestTemplate();

  ActivationContext activationContext;

  @Override
  public String getType() {
    return "PROXY";
  }

  @Override
  public void initialize(ActivationContext context) {

    try {
      // Check that the remote server is up
      remoteServer = context.getProperty("kgrid.adapter.proxy.url");
      ResponseEntity<String> resp = restTemplate.getForEntity(remoteServer, String.class);
      if (resp.getStatusCode() != HttpStatus.OK) {
        throw new AdapterException(
            "Remote execution environment not online, no response from " + remoteServer);
      }

    } catch (HttpClientErrorException | ResourceAccessException e) {
      throw new AdapterException("Remote execution environment not online, no response from " + remoteServer);
    }

    remoteUp = true;
    activationContext = context;

  }

  @Override
  public Executor activate(Path resource, String endpoint) {

    ByteArrayResource byteArray = new ByteArrayResource(activationContext.getBinary(
        resource.toString()));
    HttpEntity<ByteArrayResource> request = new HttpEntity<>(byteArray);
    JsonNode activationResult = restTemplate.postForObject(remoteServer + resource, request, JsonNode.class);
    String remoteEndpoint = activationResult.get("handle").asText();

    return new Executor() {
      @Override
      public Object execute(Object input) {

        try {
          Object result = restTemplate
              .postForObject(remoteServer + remoteEndpoint, input, JsonNode.class);
          return result;
        } catch (HttpClientErrorException | ResourceAccessException e ) {
          throw new AdapterException("Cannot access object payload in remote enviornment");
        }
      }
    };
  }

  @Override
  public String status() {
    if(remoteUp && activationContext != null) {
      return "UP" ;
    } else {
      return "DOWN";
    }
  }

}
