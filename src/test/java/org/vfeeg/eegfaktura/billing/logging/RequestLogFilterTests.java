package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exception that leaves the chain: the container answers on its error dispatch, and only then is the status known.
 * The filter writes the line there, with the original request's route and ids.
 */
class RequestLogFilterTests {

    private final RequestLogFilter filter = new RequestLogFilter();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        ((Logger) RequestLogFilter.ACCESS).addAppender(appender);
    }

    @AfterEach
    void detach() {
        ((Logger) RequestLogFilter.ACCESS).detachAppender(appender);
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> fields = new HashMap<>(event.getMDCPropertyMap());
        for (KeyValuePair kv : event.getKeyValuePairs()) {
            fields.put(kv.key, kv.value);
        }
        return fields;
    }

    @Test
    void lineIsWrittenOnTheErrorDispatchWithTheFinalStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fileData/42");
        request.addHeader(RequestLogFilter.REQUEST_ID_HEADER, "req-7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(AccessDeniedException.class, () -> filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/fileData/{id}");
            throw new AccessDeniedException("refused");
        }));
        assertTrue(appender.list.isEmpty(), "status unknown before the error dispatch");
        assertNull(MDC.get(LogFields.REQUEST_ID), "MDC cleared between the dispatches");

        // the container's error dispatch: /error answers 403, the error controller's own pattern must not win
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/error");
            ((MockHttpServletResponse) res).setStatus(403);
        });

        assertEquals(1, appender.list.size());
        Map<String, Object> f = fields(appender.list.get(0));
        assertEquals(403, f.get("http.response.status_code"));
        assertEquals("refused", f.get("event.outcome"));
        assertEquals("/api/fileData/{id}", f.get("http.route"));
        assertEquals("req-7", f.get("request.id"));
        assertEquals(AccessDeniedException.class.getName(), f.get("error.type"));
        assertEquals("GET /api/fileData/{id} 403", appender.list.get(0).getFormattedMessage());
        assertNull(MDC.get(LogFields.REQUEST_ID));
    }

    @Test
    void errorDispatchOfASendErrorWritesNoSecondLine() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fileData/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).sendError(401));
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (req, res) -> { });

        assertEquals(1, appender.list.size());
        assertEquals(401, fields(appender.list.get(0)).get("http.response.status_code"));
    }

    @Test
    void exceptionAfterTheResponseWasCommittedIsLoggedAtOnce() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fileData/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, (req, res) -> {
            res.getOutputStream().write(1);
            res.flushBuffer();
            throw new IllegalStateException("broken stream");
        }));

        assertEquals(1, appender.list.size());
        Map<String, Object> f = fields(appender.list.get(0));
        assertEquals(200, f.get("http.response.status_code"));
        assertEquals("failure", f.get("event.outcome"));
    }
}
