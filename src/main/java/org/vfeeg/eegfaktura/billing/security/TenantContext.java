package org.vfeeg.eegfaktura.billing.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;

import java.text.MessageFormat;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<Authority> CURRENT_TENANT = new ThreadLocal<>();

    public static Authority getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(Authority tenant) {
        CURRENT_TENANT.set(tenant);
    }

    public static void validateTenant(String tenant) {
        if (tenant==null || CURRENT_TENANT.get()==null || !CURRENT_TENANT.get().getAuthority().equals(tenant)) {
            log.error(MessageFormat.format("Access denied for {0} in {1}", tenant, CURRENT_TENANT.get()));
            throw new AccessDeniedException("Failed to validate tenant ("+tenant+")");
        }
    }

}
