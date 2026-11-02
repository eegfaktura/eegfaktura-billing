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
import java.util.regex.Pattern;

/**
 * Runs in the security chain right after {@code JwtRequestFilter}: once the token is valid and the requested tenant
 * was checked against it, puts {@code tenant.id} (the tenant the request works on, when the token grants it) and
 * {@code client.id} (the token's {@code azp}, the Keycloak client — not a person) into the MDC. A superuser on a
 * tenant its token does not name gets {@code support.access=true}, and {@code tenant.id} when the header has the
 * format of a tenant. Without a valid token both stay empty.
 * Not a {@code @Component}, so it runs only inside the security chain; {@link RequestLogFilter} clears the MDC.
 */
public class LogContextFilter extends OncePerRequestFilter {

    // e.g. RC100123, GC100123-001
    private static final Pattern TENANT = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,63}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthentication auth) {
            String tenant = request.getHeader("Tenant");
            if (tenant != null && !tenant.isBlank()) {
                if (auth.hasTenant(tenant)) {
                    MDC.put(LogFields.TENANT_ID, tenant.strip().toUpperCase(Locale.ROOT));
                } else if (auth.isSuperuser()) {
                    // operator/support working on a community the token does not name: visible as such. No token
                    // vouches for this value, it is the raw header, so it goes into the log only if it looks like a tenant
                    String requested = tenant.strip().toUpperCase(Locale.ROOT);
                    if (TENANT.matcher(requested).matches()) {
                        MDC.put(LogFields.TENANT_ID, requested);
                    }
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
