package org.vfeeg.eegfaktura.billing.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.vfeeg.eegfaktura.billing.logging.LogFields;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<Authority> CURRENT_TENANT = new ThreadLocal<>();

    public static Authority getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(Authority tenant) {
        CURRENT_TENANT.set(tenant);
    }

    /**
     * Vergleicht den Mandanten aus Pfad oder Datensatz gegen den Mandanten der Anfrage.
     *
     * Dass der Anfrage-Mandant tatsaechlich zum Benutzer gehoert, stellt JwtRequestFilter
     * sicher — dort wird der Header gegen die Mandantenliste des Tokens geprueft. Erst
     * beides zusammen ergibt eine Mandantentrennung: ohne den Filter wuerden hier nur
     * zwei Werte verglichen, die beide der Aufrufer bestimmt.
     */
    public static void validateTenant(String tenant) {
        final Authority current = CURRENT_TENANT.get();
        final String requestTenant = current == null ? null : current.getAuthority();

        if (tenant == null || requestTenant == null || !requestTenant.equalsIgnoreCase(tenant)) {
            // no values: both can be what the caller typed (header, path, body); a valid tenant of the request is on
            // the line as tenant.id. The caller's error, not the service's: one WARN, the exception handler and the
            // container write no second record (eegfaktura-dev#54 #10)
            log.atWarn().addKeyValue(LogFields.EVENT_ACTION, "tenant.refused")
                    .log("Tenant of the resource does not match the tenant of the request");
            throw new AccessDeniedException("Failed to validate tenant");
        }
    }

}
