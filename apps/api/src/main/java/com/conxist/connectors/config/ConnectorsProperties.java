package com.conxist.connectors.config;

import java.net.URI;
import java.time.Duration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How ConXist calls outside MCP servers (ADR 0017).
 *
 * @param redirectUri   where a server's sign-in page sends the person back: this API's callback, on this machine
 * @param allowLoopback lets plain http on this computer through; only tests and the local fake turn it on
 * @param githubApi     GitHub's REST API (ADR 0019); only tests and the local fake point it elsewhere
 * @param bitbucketApi  Bitbucket Cloud's REST API, version 2.0 (ADR 0020); likewise
 * @param gmailApi      Gmail's API for the signed-in person (ADR 0021); likewise
 * @param google        this ConXist's Google OAuth client for Gmail; without a client id and secret, Gmail is not offered
 */
@Validated
@ConfigurationProperties("conxist.connectors")
public record ConnectorsProperties(
        @NotNull URI redirectUri,
        boolean allowLoopback,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull URI githubApi,
        @NotNull URI bitbucketApi,
        @NotNull URI gmailApi,
        @NotNull @Valid Google google) {

    /**
     * @param redirectUri where Google sends the person back: this API's callback, registered on the OAuth client
     */
    public record Google(String clientId, String clientSecret, @NotNull URI authorizationUri, @NotNull URI tokenUri,
            @NotNull URI redirectUri) {

        @Override
        public String toString() {
            return "Google[" + clientId + "]";
        }
    }
}
