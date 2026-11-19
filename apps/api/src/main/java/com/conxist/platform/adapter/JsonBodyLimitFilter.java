package com.conxist.platform.adapter;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a JSON body larger than any request ConXist expects (a meeting edit is the largest) before it is parsed,
 * so a huge body cannot tie up memory. A body without a stated length is refused too. File uploads have their own
 * multipart limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class JsonBodyLimitFilter extends OncePerRequestFilter {

    static final long MAX_JSON_BYTES = 4L * 1024 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getContentType();
        boolean json = type != null && type.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE);
        long length = request.getContentLengthLong();
        if (json && (length > MAX_JSON_BYTES || (length < 0 && request.getHeader("Transfer-Encoding") != null))) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
