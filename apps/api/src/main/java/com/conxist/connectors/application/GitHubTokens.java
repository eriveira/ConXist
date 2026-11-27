package com.conxist.connectors.application;

import java.util.regex.Pattern;
import com.conxist.connectors.domain.InvalidIntegration;

/** What a GitHub token must look like before ConXist sends it to GitHub (ADR 0019). */
final class GitHubTokens {

    /** Fine-grained tokens only: a classic token reaches every repository its person can see. */
    static final String PREFIX = "github_pat_";
    private static final Pattern SHAPE = Pattern.compile("github_pat_[A-Za-z0-9_]{1,244}");

    private GitHubTokens() {
    }

    static String checked(String token) {
        String value = token == null ? "" : token.strip();
        if (value.isEmpty()) {
            throw new InvalidIntegration("token-required", "Paste the token first.");
        }
        if (!value.startsWith(PREFIX)) {
            throw new InvalidIntegration("token-not-fine-grained",
                    "Use a fine-grained token. It starts with github_pat_.");
        }
        if (!SHAPE.matcher(value).matches()) {
            throw new InvalidIntegration("token-invalid", "That doesn't look like a GitHub token. Copy it again.");
        }
        return value;
    }
}
