package org.kgrid.adapter.proxy;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class RuntimeInfoContributor implements InfoContributor {

  @Override
  public void contribute(Info.Builder builder) {
    if (ProxyAdapter.getRuntimes().size() > 0) {
      builder.withDetail("runtimes", ProxyAdapter.getRuntimes());
    }
  }
}
