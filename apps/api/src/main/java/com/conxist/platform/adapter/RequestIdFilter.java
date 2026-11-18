package com.conxist.platform.adapter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gives every request an id that appears in each log line and in the response, so a report can be traced. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String LOG_KEY = "requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = idFrom(request.getHeader(HEADER));
        MDC.put(LOG_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LOG_KEY);
        }
    }

    /** A caller's id is kept only when it is plainly an id, so headers cannot inject text into the logs. */
    private static String idFrom(String header) {
        return header != null && SAFE_ID.matcher(header).matches() ? header : UUID.randomUUID().toString();
    }
}
