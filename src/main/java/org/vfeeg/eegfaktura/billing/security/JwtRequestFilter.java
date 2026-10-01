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

        // Pruefen, ob der angefragte Mandant im Token steht.
        //
        // Der Mandant wird hier bewusst DIREKT aus dem Header gelesen und nicht ueber
        // TenantContext bezogen: den fuellt TenantFilter (@Order(1)), die
        // Spring-Security-Kette laeuft aber auf Order -100, also davor. An dieser Stelle
        // waere TenantContext noch leer. Genau das hat die Pruefung bisher mit verdeckt —
        // zusammen mit einer invertierten Bedingung und einem fehlenden equals() in
        // Authority, wodurch contains() ohnehin nie etwas gefunden hat. In 30 Tagen
        // Produktionsprotokoll ist sie kein einziges Mal angeschlagen.
        //
        // Kein Header: hier nicht abweisen. Die Ressourcen pruefen den Mandanten
        // ohnehin ueber TenantContext.validateTenant(); Pfade ohne Mandantenbezug
        // (z. B. "/", "/swagger-ui") sollen deshalb nicht an dieser Stelle scheitern.
        final String requestedTenant = request.getHeader("Tenant");
        if (requestedTenant != null && !requestedTenant.isBlank()
                && !jwtAuthentication.hasTenant(requestedTenant)) {
            log.warn("User {} not granted permission for tenant {}",
                    jwtAuthentication.getName(), requestedTenant);
            throw new AccessDeniedException("User not granted permission for " + requestedTenant);
        }

        // set user details on spring security context
        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication);

        // continue with authenticated user
        chain.doFilter(request, response);
    }

}
