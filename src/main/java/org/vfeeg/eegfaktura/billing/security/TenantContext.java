package org.vfeeg.eegfaktura.billing.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(String tenant) {
        CURRENT_TENANT.set(tenant);
    }

    public static void validateTenant(String tenant) {
        if (tenant==null || CURRENT_TENANT.get()==null || !CURRENT_TENANT.get().equals(tenant)) {
            log.error("Access denied for "+tenant+" in "+CURRENT_TENANT.get());
            throw new AccessDeniedException("Failed to validate tenant ("+tenant+")");
        }
    }

}
