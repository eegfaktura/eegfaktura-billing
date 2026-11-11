package org.vfeeg.eegfaktura.billing.logging;

import org.slf4j.MDC;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Names (ECS) of the service's own log fields: MDC keys set per request, which every log line of the request carries,
 * and keys of a line's key-value pairs ({@link #EVENT_ACTION}, {@link #LOG_EFFECTIVE_LEVEL}).
 */
public final class LogFields {

    public static final String REQUEST_ID = "request.id";
    public static final String TRACE_ID = "trace.id";
    public static final String SPAN_ID = "span.id";
    public static final String TENANT_ID = "tenant.id";
    public static final String CLIENT_ID = "client.id";
    public static final String SUPPORT_ACCESS = "support.access";
    public static final String LOG_EFFECTIVE_LEVEL = "log.effective_level";
    public static final String EVENT_ACTION = "event.action";

    // e.g. RC100123, GC100123-001
    private static final Pattern TENANT = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,63}");

    private LogFields() {
    }

    /**
     * The value for {@link #TENANT_ID}: upper-cased and stripped if it has the format of a tenant, otherwise null and
     * the field stays out (the one place of that rule, Q7).
     */
    public static String tenant(String value) {
        String tenant = value == null ? null : value.strip().toUpperCase(Locale.ROOT);
        return tenant != null && TENANT.matcher(tenant).matches() ? tenant : null;
    }

    static void clear() {
        MDC.remove(REQUEST_ID);
        MDC.remove(TRACE_ID);
        MDC.remove(SPAN_ID);
        MDC.remove(TENANT_ID);
        MDC.remove(CLIENT_ID);
        MDC.remove(SUPPORT_ACCESS);
    }
}
