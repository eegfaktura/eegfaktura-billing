package org.vfeeg.eegfaktura.billing.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outermost filter (before the security chain): sets {@code request.id} (from {@code X-Request-Id} or generated)
 * and {@code trace.id}/{@code span.id} (from W3C {@code traceparent}) in the MDC for every log line of the request,
 * and writes one access line per request when it ends (logging concept point 6).
 *
 * <p>Fields as in every eegfaktura service (logging alignment 2026-10-30): {@code event.duration} in nanoseconds,
 * {@code event.outcome} success/refused/failure, {@code http.*} ECS names. The access line carries the route template, never the raw path or query; {@code tenant.id} and
 * {@code client.id} only as set by {@link LogContextFilter} after the token check. No user, e-mail, IP or user agent
 * (no user tracking, decided 2026-10-29).
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final Logger ACCESS = LoggerFactory.getLogger("org.vfeeg.eegfaktura.billing.access");
    private static final Set<String> SKIPPED = Set.of("/healthz", "/readyz");
    // a caller's id is kept only when it is short and harmless; otherwise a new one is generated
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    // W3C trace context: version ff is invalid, all-zero ids are invalid
    private static final Pattern TRACEPARENT = Pattern.compile(
            "(?!ff)[0-9a-f]{2}-(?!0{32})([0-9a-f]{32})-(?!0{16})([0-9a-f]{16})-[0-9a-f]{2}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(LogFields.REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null) {
            Matcher m = TRACEPARENT.matcher(traceparent);
            if (m.matches()) {
                MDC.put(LogFields.TRACE_ID, m.group(1));
                MDC.put(LogFields.SPAN_ID, m.group(2));
            }
        }
        long start = System.nanoTime();
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            try {
                if (!SKIPPED.contains(request.getRequestURI())) {
                    access(request, response, failure, System.nanoTime() - start);
                }
            } finally {
                LogFields.clear();
            }
        }
    }

    private static void access(HttpServletRequest request, HttpServletResponse response, Throwable failure,
                               long durationNanos) {
        // an exception that leaves the chain is answered by the container's error path: 403 for the tenant refusal
        // that JwtRequestFilter throws (private known-errors.md #35), 500 for anything else
        int status = failure == null ? response.getStatus() : failure instanceof AccessDeniedException ? 403 : 500;
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String outcome = status == 401 || status == 403 ? "refused"
                : status >= 400 || failure != null ? "failure" : "success";
        LoggingEventBuilder line = ACCESS.atInfo()
                .addKeyValue(LogFields.EVENT_ACTION, "http.access")
                .addKeyValue("event.outcome", outcome)
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("http.response.status_code", status)
                .addKeyValue("event.duration", durationNanos);
        if (pattern != null) { // unmatched requests (e.g. refused before the router) carry no route
            line = line.addKeyValue("http.route", pattern.toString());
        }
        if (failure != null) {
            line = line.addKeyValue("error.type", failure.getClass().getName());
        }
        line.log("{} {} {} {} ms", request.getMethod(), pattern == null ? "-" : pattern, status,
                durationNanos / 1_000_000);
    }
}
