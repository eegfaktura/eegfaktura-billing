package org.vfeeg.eegfaktura.billing.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String tenant = req.getHeader("Tenant");
        TenantContext.setCurrentTenant(new Authority(tenant));

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.setCurrentTenant(null);
        }
    }
}