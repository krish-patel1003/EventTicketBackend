package com.tickify.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One structured access-log line per request: method, path, status, duration and caller.
 *
 * <p>Kept deliberately free of request/response bodies — those carry passwords and
 * base64 QR payloads, neither of which belongs in a log aggregator.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** Health checks and metrics scrapes would otherwise dominate the log volume. */
    private static final String[] EXCLUDED_PREFIXES = {"/actuator", "/swagger-ui", "/v3/api-docs"};

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

            MDC.put("httpMethod", request.getMethod());
            MDC.put("httpPath", request.getRequestURI());
            MDC.put("httpStatus", String.valueOf(response.getStatus()));
            MDC.put("durationMs", String.valueOf(durationMs));

            String caller = currentUser();
            if (caller != null) {
                MDC.put("user", caller);
            }

            try {
                if (response.getStatus() >= 500) {
                    log.error("{} {} -> {} ({}ms)", request.getMethod(), request.getRequestURI(),
                            response.getStatus(), durationMs);
                } else if (response.getStatus() >= 400) {
                    log.warn("{} {} -> {} ({}ms)", request.getMethod(), request.getRequestURI(),
                            response.getStatus(), durationMs);
                } else {
                    log.info("{} {} -> {} ({}ms)", request.getMethod(), request.getRequestURI(),
                            response.getStatus(), durationMs);
                }
            } finally {
                MDC.remove("httpMethod");
                MDC.remove("httpPath");
                MDC.remove("httpStatus");
                MDC.remove("durationMs");
                MDC.remove("user");
            }
        }
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        return "anonymousUser".equals(name) ? null : name;
    }
}
