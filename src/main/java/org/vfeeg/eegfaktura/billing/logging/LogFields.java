package org.vfeeg.eegfaktura.billing.logging;

import org.slf4j.MDC;

/** MDC keys (ECS names) the service sets per request; every log line of the request carries them. */
public final class LogFields {

    public static final String REQUEST_ID = "request.id";
    public static final String TRACE_ID = "trace.id";
    public static final String SPAN_ID = "span.id";
    public static final String TENANT_ID = "tenant.id";
    public static final String CLIENT_ID = "client.id";
    public static final String SUPPORT_ACCESS = "support.access";
    public static final String EVENT_ACTION = "event.action";

    private LogFields() {
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
