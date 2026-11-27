package com.conxist.connectors.application;

import java.util.List;
import java.util.Map;
import com.conxist.connectors.domain.Integration;

/**
 * Reads one service's API with a person's credential: GitHub (ADR 0019) or Bitbucket (ADR 0020). It only reads.
 * Everything the service answers is untrusted customer data. Failures are the exceptions below.
 */
public interface IntegrationApi {

    /** The service this reads. */
    Integration.Kind kind();

    /**
     * The credential's account and the repositories it reaches, as {@code owner/name}, by name, at most
     * {@link Integration#MAX_REPOSITORIES}.
     */
    Account account(String credential);

    /**
     * Calls one of ConXist's tools for this service ({@link Integration.Kind#tools()}) for the integration's
     * repositories only, and returns the service's answer as short text.
     *
     * @throws Problem when an argument is wrong, before anything is sent
     */
    String call(Integration integration, String credential, String tool, Map<String, Object> arguments);

    record Account(String login, List<String> repositories) {
    }

    /** A wrong argument: the words tell the model what to send instead. */
    final class Problem extends RuntimeException {
        public Problem(String message) {
            super(message);
        }
    }

    /** The service refused the credential (401). */
    final class TokenRefused extends RuntimeException {
        public TokenRefused(String reason) {
            super(reason);
        }
    }

    /** The service has nothing there, or the credential can't see it (404). */
    final class Missing extends RuntimeException {
        public Missing(String reason) {
            super(reason);
        }
    }

    /** The service refused the request itself (403, 422, 429), for example a rate limit or a query it can't run. */
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
