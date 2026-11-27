package com.conxist.connectors.application;

import java.util.regex.Pattern;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.InvalidIntegration;

/**
 * What a credential must look like before ConXist sends it to the service, and how it is kept: GitHub's is its token
 * (ADR 0019); Bitbucket's is the person's Atlassian email and API token, kept together as one sealed value (ADR 0020).
 */
public final class IntegrationCredentials {

    /** Fine-grained tokens only: a classic token reaches every repository its person can see. */
    static final String GITHUB_PREFIX = "github_pat_";
    private static final Pattern GITHUB = Pattern.compile("github_pat_[A-Za-z0-9_]{1,244}");
    /** Atlassian account API tokens; Bitbucket's app passwords are being retired. */
    static final String ATLASSIAN_PREFIX = "ATATT";
    private static final Pattern ATLASSIAN = Pattern.compile("ATATT[A-Za-z0-9_=+/-]{1,495}");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]{1,64}@[^\\s@]{1,185}\\.[^\\s@]{1,63}");
    private static final String SEPARATOR = "\n";

    private IntegrationCredentials() {
    }

    /** The credential to send and to seal, once its parts look right. */
    static String checked(Integration.Kind kind, String email, String token) {
        String value = token == null ? "" : token.strip();
        return switch (kind) {
            case GITHUB -> github(value);
            case BITBUCKET -> bitbucket(email == null ? "" : email.strip(), value);
        };
    }

    /** The token part of a credential, whose last characters the app shows. */
    static String tokenOf(String credential) {
        int at = credential.indexOf(SEPARATOR);
        return at < 0 ? credential : credential.substring(at + 1);
    }

    /** Bitbucket's email and API token, from the sealed credential (ADR 0020). */
    public static Basic basic(String credential) {
        int at = credential.indexOf(SEPARATOR);
        if (at < 0) {
            throw new IllegalArgumentException("not a Bitbucket credential");
        }
        return new Basic(credential.substring(0, at), credential.substring(at + 1));
    }

    public record Basic(String email, String token) {
    }

    private static String github(String token) {
        if (token.isEmpty()) {
            throw new InvalidIntegration("token-required", "Paste the token first.");
        }
        if (!token.startsWith(GITHUB_PREFIX)) {
            throw new InvalidIntegration("token-not-fine-grained",
                    "Use a fine-grained token. It starts with github_pat_.");
        }
        if (!GITHUB.matcher(token).matches()) {
            throw new InvalidIntegration("github-token-invalid",
                    "That doesn't look like a GitHub token. Copy it again.");
        }
        return token;
    }

    private static String bitbucket(String email, String token) {
        if (email.isEmpty()) {
            throw new InvalidIntegration("email-required", "Enter the email you sign in to Atlassian with.");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new InvalidIntegration("email-invalid", "That doesn't look like an email address.");
        }
        if (token.isEmpty()) {
            throw new InvalidIntegration("token-required", "Paste the token first.");
        }
        if (!token.startsWith(ATLASSIAN_PREFIX)) {
            throw new InvalidIntegration("token-not-api-token",
                    "Use an API token from your Atlassian account. It starts with ATATT.");
        }
        if (!ATLASSIAN.matcher(token).matches()) {
            throw new InvalidIntegration("bitbucket-token-invalid",
                    "That doesn't look like an Atlassian API token. Copy it again.");
        }
        return email + SEPARATOR + token;
    }
}
