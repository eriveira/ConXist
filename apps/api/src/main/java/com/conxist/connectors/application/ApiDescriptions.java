package com.conxist.connectors.application;

import java.net.URI;
import java.util.List;
import com.conxist.connectors.domain.ApiTool;

/** Reads an OpenAPI description (OpenAPI 3 or Swagger 2, JSON or YAML) into tools for one API (ADR 0027). */
public interface ApiDescriptions {

    /**
     * The operations ConXist can call, as tools under {@code address}, at most {@code max}; and the ones left out, with
     * why. Reads only ticked are none: the caller decides which start allowed.
     *
     * @param text  the description, untrusted
     * @param from  where it was fetched, to resolve a relative server address; null for an uploaded file
     * @throws Unreadable when it is not an OpenAPI description ConXist reads
     */
    Read read(String text, URI address, URI from, int max);

    record Read(String title, String version, List<ApiTool> tools, List<LeftOut> leftOut) {
    }

    /** @param operation the operation's id, or its method and path */
    record LeftOut(String operation, String reason) {
    }

    final class Unreadable extends RuntimeException {
        public Unreadable(String reason) {
            super(reason);
        }
    }
}
