package org.vfeeg.eegfaktura.billing.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtRequestFilter  extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    private JwtRequestFilter(final JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        // look for Bearer auth header
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        final String token = header.substring(7);

        final JwtAuthentication jwtAuthentication = jwtTokenService.validateTokenAndGetAuthentication(token);
        if (jwtAuthentication == null) {
            // validation failed or token expired
            chain.doFilter(request, response);
            return;
        }

        // check if user is granted permission to the current tenant
        if (jwtAuthentication.getAuthorities().contains(TenantContext.getCurrentTenant())) {
            log.error("User not granted permission for "+TenantContext.getCurrentTenant());
            throw new AccessDeniedException("User not granted permission for "+TenantContext.getCurrentTenant());
        }

        // set user details on spring security context
        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication);

        // continue with authenticated user
        chain.doFilter(request, response);
    }

}
