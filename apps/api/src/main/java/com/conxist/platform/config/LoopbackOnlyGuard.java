package com.conxist.platform.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Until sign-in exists every endpoint is open, so the API refuses to start unless it listens on this machine only
 * (ADR 0002). Remove this guard in the change that adds authentication.
 */
@Component
class LoopbackOnlyGuard implements InitializingBean {

    static final String ADDRESS_PROPERTY = "server.address";

    private final Environment environment;

    LoopbackOnlyGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        requireLoopback(environment.getProperty(ADDRESS_PROPERTY));
    }

    static void requireLoopback(String address) {
        if (address == null || !isLoopback(address)) {
            throw new IllegalStateException("ConXist has no sign-in yet, so " + ADDRESS_PROPERTY
                    + " must be a loopback address such as 127.0.0.1 (was: " + address + ").");
        }
    }

    private static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException unknown) {
            return false;
        }
    }
}
