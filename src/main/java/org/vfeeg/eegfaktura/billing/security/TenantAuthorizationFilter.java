package org.vfeeg.eegfaktura.billing.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class TenantAuthorizationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Authority currentTenant = TenantContext.getCurrentTenant();

        if (authentication != null && authentication.isAuthenticated() && currentTenant != null && currentTenant.getAuthority() != null) {
            if (!authentication.getAuthorities().contains(currentTenant)) {
                log.error("User {} not granted permission for tenant {}", authentication.getName(), currentTenant.getAuthority());
                throw new AccessDeniedException("User not granted permission for " + currentTenant.getAuthority());
            }
        }

        filterChain.doFilter(request, response);
    }
}
