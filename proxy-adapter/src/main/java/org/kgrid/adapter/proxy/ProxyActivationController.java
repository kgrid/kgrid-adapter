package org.kgrid.adapter.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;

@Component
public class ProxyActivationController {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Async
    public void initiateRefreshEngine(String engine){
        log.warn("Refresh has started.");
        ProxyAdapter.activationContext.reactivate(engine);
    }
}
