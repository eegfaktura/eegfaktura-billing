package org.vfeeg.eegfaktura.billing.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.vfeeg.eegfaktura.billing.security.JwtAuthentication;

import java.io.IOException;
import java.util.Locale;

/**
 * Runs in the security chain right after {@code JwtRequestFilter}: once the token is valid and the requested tenant
 * was checked against it, puts {@code tenant.id} (the tenant the request works on, when the token grants it) and
 * {@code client.id} (the token's {@code azp}, the Keycloak client — not a person) into the MDC. A superuser on a
 * tenant its token does not name gets {@code tenant.id} plus {@code support.access=true}. Without a valid token both stay empty.
 * Not a {@code @Component}, so it runs only inside the security chain; {@link RequestLogFilter} clears the MDC.
 */
public class LogContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthentication auth) {
            String tenant = request.getHeader("Tenant");
            if (tenant != null && !tenant.isBlank()) {
                if (auth.hasTenant(tenant)) {
                    MDC.put(LogFields.TENANT_ID, tenant.strip().toUpperCase(Locale.ROOT));
                } else if (auth.isSuperuser()) {
                    // operator/support working on a community the token does not name: visible as such
                    MDC.put(LogFields.TENANT_ID, tenant.strip().toUpperCase(Locale.ROOT));
                    MDC.put(LogFields.SUPPORT_ACCESS, "true");
                }
            }
            if (auth.getClientId() != null) {
                MDC.put(LogFields.CLIENT_ID, auth.getClientId());
            }
        }
        chain.doFilter(request, response);
    }
}
