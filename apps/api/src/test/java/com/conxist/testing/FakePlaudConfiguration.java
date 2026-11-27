package com.conxist.testing;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points the API at a {@link FakePlaudServer} (Teams) and a {@link FakePlaudMcpServer} (Sign in with Plaud, which
 * approves at once) on localhost, test runs at a {@link FakeClaudeServer} and a {@link FakeOpenAiServer}, and gives it a throwaway secrets key. Used
 * by the integration tests and by {@code mvn spring-boot:test-run} (Client ID {@code demo-team} works with any key).
 * The fake Claude takes any key that starts with {@code sk-ant-fake-}; it is also this computer's key unless
 * {@code CONXIST_FAKE_COMPUTER_KEY=false}, to try Settings with no key at all. The fake OpenAI takes any key that
 * starts with {@code sk-fake-} and is never this computer's key. Three fake MCP servers ({@link FakeMcpServers}) log
 * their addresses at start, and a fake GitHub API ({@link FakeGitHubServer}) takes any token that starts with
 * {@code github_pat_fake}.
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

    @Bean(destroyMethod = "close")
    FakeOpenAiServer fakeOpenAi() throws IOException {
        return new FakeOpenAiServer();
    }

    /** Outside MCP servers to add in Settings › MCPs: one with its own sign-in, one that takes a token, one public. */
    @Bean(destroyMethod = "close")
    FakeMcpServers fakeMcpServers() throws IOException {
        return FakeMcpServers.start();
    }

    /** GitHub's API for Settings › Integrations: any token that starts with github_pat_fake. */
    @Bean(destroyMethod = "close")
    FakeGitHubServer fakeGitHub() throws IOException {
        return new FakeGitHubServer();
    }

    @Bean
    DynamicPropertyRegistrar fakePlaudProperties(FakePlaudServer fake, FakePlaudMcpServer mcp,
            FakeClaudeServer claude, FakeOpenAiServer openAi, FakeGitHubServer github) {
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
            // OpenAI has no key on this computer, so a workspace connects its own (any key that starts with sk-fake-).
            registry.add("conxist.runs.openai-base-url", () -> openAi.baseUrl().toString());
            registry.add("conxist.runs.openai-api-key", () -> "");
            // The fake MCP servers run on this computer, over plain http.
            registry.add("conxist.connectors.allow-loopback", () -> "true");
            registry.add("conxist.connectors.github-api", () -> github.baseUrl().toString());
        };
    }
}
