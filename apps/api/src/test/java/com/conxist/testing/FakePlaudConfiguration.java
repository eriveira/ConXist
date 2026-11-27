package com.conxist.testing;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points the API at a {@link FakePlaudServer} (Teams) and a {@link FakePlaudMcpServer} (Sign in with Plaud, which
 * approves at once) on localhost, test runs at a {@link FakeClaudeServer}, and gives it a throwaway secrets key. Used
 * by the integration tests and by {@code mvn spring-boot:test-run} (Client ID {@code demo-team} works with any key).
 * The fake Claude takes any key that starts with {@code sk-ant-fake-}; it is also this computer's key unless
 * {@code CONXIST_FAKE_COMPUTER_KEY=false}, to try Settings with no key at all.
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

    @Bean(destroyMethod = "close")
    FakeClaudeServer fakeClaude() throws IOException {
        return new FakeClaudeServer();
    }

    @Bean
    DynamicPropertyRegistrar fakePlaudProperties(FakePlaudServer fake, FakePlaudMcpServer mcp,
            FakeClaudeServer claude) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String secretsKey = Base64.getEncoder().encodeToString(key);
        return registry -> {
            registry.add("conxist.plaud.api-base-url", () -> fake.apiBase().toString());
            registry.add("conxist.plaud.token-url", () -> fake.tokenUrl().toString());
            registry.add("conxist.plaud.token-audience", () -> FakePlaudServer.AUDIENCE);
            registry.add("conxist.plaud.account.mcp-url", () -> mcp.mcpUrl().toString());
            registry.add("conxist.secrets.encryption-key", () -> secretsKey);
            registry.add("conxist.runs.anthropic-base-url", () -> claude.baseUrl().toString());
            registry.add("conxist.runs.anthropic-api-key",
                    () -> "false".equals(System.getenv("CONXIST_FAKE_COMPUTER_KEY")) ? "" : FakeClaudeServer.KEY);
        };
    }
}
