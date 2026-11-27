package com.conxist.connectors.application;

import java.net.URI;

/**
 * Google's sign-in for Gmail (ADR 0021): an authorization code with PKCE, for read-only Gmail access only. It works
 * only when this ConXist has a Google OAuth client; without one, Gmail is not offered.
 */
public interface GoogleSignIn {

    /** Whether a Google OAuth client is set up, so Gmail can be offered. */
    boolean configured();

    /** Google's page where the person signs in and allows read-only Gmail access. */
    URI authorizationUrl(String state, String verifier);

    /**
     * Trades the code Google sent back for a refresh token, the credential ConXist keeps.
     *
     * @throws IntegrationApi.TokenRefused when Google refuses the code, or sends no refresh token
     * @throws IntegrationApi.Refused      when the person allowed more or less than read-only Gmail
     * @throws IntegrationApi.Unreachable  when Google can't be reached
     */
    String exchange(String code, String verifier);
}
