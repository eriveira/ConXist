package com.conxist.connectors.application;

import java.util.List;
import java.util.Map;
import com.conxist.connectors.domain.Integration;

/**
 * Reads GitHub's REST API with a person's token (ADR 0019). It only sends GET requests. Everything GitHub answers is
 * untrusted customer data. Failures are the exceptions below.
 */
public interface GitHubApi {

    /** The token's account and the repositories it reaches, as {@code owner/name}, by name, at most 100. */
    Account account(String token);

    /**
     * Calls one of ConXist's GitHub tools ({@link com.conxist.connectors.domain.GitHubTools}) for the integration's
     * repositories only, and returns GitHub's answer as short text.
     *
     * @throws Problem when an argument is wrong, before anything is sent
     */
    String call(Integration integration, String token, String tool, Map<String, Object> arguments);

    record Account(String login, List<String> repositories) {
    }

    /** A wrong argument: the words tell the model what to send instead. */
    final class Problem extends RuntimeException {
        public Problem(String message) {
            super(message);
        }
    }

    /** GitHub refused the token (401). */
    final class TokenRefused extends RuntimeException {
        public TokenRefused(String reason) {
            super(reason);
        }
    }

    /** GitHub has nothing there, or the token can't see it (404). */
    final class Missing extends RuntimeException {
        public Missing(String reason) {
            super(reason);
        }
    }

    /** GitHub refused the request itself (403, 422), for example a rate limit or a query it can't run. */
    final class Refused extends RuntimeException {
        public Refused(String reason) {
            super(reason);
        }
    }

    /** Network, timeouts, server errors. */
    final class Unreachable extends RuntimeException {
        public Unreachable(String reason) {
            super(reason);
        }
    }
}
