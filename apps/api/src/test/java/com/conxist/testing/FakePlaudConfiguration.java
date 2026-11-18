package com.conxist.testing;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points the API at a {@link FakePlaudServer} (Teams) and a {@link FakePlaudMcpServer} (Sign in with Plaud, which
 * approves at once) on localhost and gives it a throwaway secrets key. Used by the
 * integration tests and by {@code mvn spring-boot:test-run} (Client ID {@code demo-team} works with any key).
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakePlaudConfiguration {

    @Bean(destroyMethod = "close")
    FakePlaudServer fakePlaud() throws IOException {
        return FakePlaudServer.demo();
    }

    @Bean(destroyMethod = "close")
    FakePlaudMcpServer fakePlaudMcp() throws IOException {
        return FakePlaudMcpServer.demo();
    }

    @Bean
    DynamicPropertyRegistrar fakePlaudProperties(FakePlaudServer fake, FakePlaudMcpServer mcp) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String secretsKey = Base64.getEncoder().encodeToString(key);
        return registry -> {
            registry.add("conxist.plaud.api-base-url", () -> fake.apiBase().toString());
            registry.add("conxist.plaud.token-url", () -> fake.tokenUrl().toString());
            registry.add("conxist.plaud.token-audience", () -> FakePlaudServer.AUDIENCE);
            registry.add("conxist.plaud.account.mcp-url", () -> mcp.mcpUrl().toString());
            registry.add("conxist.secrets.encryption-key", () -> secretsKey);
        };
    }
}
