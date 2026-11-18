package com.conxist.platform.adapter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Until sign-in exists, answers only requests addressed to this machine from pages on this machine. This blocks
 * DNS rebinding (a foreign site whose name points at 127.0.0.1) and cross-site posts from other origins.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class LocalOriginFilter extends OncePerRequestFilter {

    static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isLocal(request.getServerName()) && originIsLocal(request.getHeader(HttpHeaders.ORIGIN))) {
            chain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private static boolean originIsLocal(String origin) {
        if (origin == null) {
            return true;
        }
        try {
            return isLocal(URI.create(origin).getHost());
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static boolean isLocal(String host) {
        return host != null && LOCAL_HOSTS.contains(host.toLowerCase());
    }
}
