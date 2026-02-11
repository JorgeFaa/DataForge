package com.dataforge.config;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Component
public class PortLogger {

    private final WebServerApplicationContext context;

    public PortLogger(WebServerApplicationContext context) {
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logPort() {
        int port = context.getWebServer().getPort();
        System.out.println("APP_RUNNING_PORT=" + port);
    }
}
