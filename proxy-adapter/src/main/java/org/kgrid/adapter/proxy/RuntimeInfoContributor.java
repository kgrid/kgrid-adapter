package org.kgrid.adapter.proxy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class RuntimeInfoContributor implements InfoContributor {
    @Autowired
    ProxyAdapter proxyAdapter;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("runtimes", proxyAdapter.getRuntimes());
    }
}
