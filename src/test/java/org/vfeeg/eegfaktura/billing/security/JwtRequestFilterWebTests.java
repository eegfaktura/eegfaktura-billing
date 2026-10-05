package org.vfeeg.eegfaktura.billing.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.vfeeg.eegfaktura.billing.controller.HomeController;
import org.vfeeg.eegfaktura.billing.rest.Endpoint;
import org.vfeeg.eegfaktura.billing.rest.FileDataResource;
import org.vfeeg.eegfaktura.billing.rest.TestTokens;
import org.vfeeg.eegfaktura.billing.rest.WebSliceTest;
import org.vfeeg.eegfaktura.billing.model.FileDataDTO;
import org.vfeeg.eegfaktura.billing.service.FileDataService;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.FOREIGN;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/**
 * {@code JwtRequestFilter} and {@code JwtTokenService} through the real security chain: which
 * tokens authenticate. {@code GET /} is public, every other path needs {@code ROLE_EEG_ADMIN}.
 */
@WebMvcTest(controllers = {FileDataResource.class, HomeController.class})
class JwtRequestFilterWebTests extends WebSliceTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    FileDataService fileDataService;

    private ResultActions getFile(String authorization) throws Exception {
        FileDataDTO file = new FileDataDTO();
        file.setTenantId(OWN);
        file.setName("a.pdf");
        file.setMimeType("application/pdf");
        file.setData(new byte[]{1});
        when(fileDataService.get(Endpoint.ID)).thenReturn(file);
        return mvc.perform(get("/api/fileData/" + Endpoint.ID)
                .header("Tenant", OWN).header(HttpHeaders.AUTHORIZATION, authorization));
    }

    @Test
    void validAdminTokenIsAccepted() throws Exception {
        getFile(TestTokens.admin()).andExpect(status().isOk());
    }

    @Test
    void keycloakGroupPathWithSlashIsTheSameRole() throws Exception {
        getFile(TestTokens.bearer(TestTokens.token(List.of(OWN), List.of("/EEG_ADMIN")))).andExpect(status().isOk());
    }

    @Test
    void otherAuthorizationSchemeIsIgnored() throws Exception {
        getFile("Basic dXNlcjpwYXNzd29yZA==").andExpect(status().isForbidden());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        getFile(TestTokens.bearer(TestTokens.signedWithForeignKey())).andExpect(status().isForbidden());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        getFile(TestTokens.bearer(TestTokens.expired())).andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutTenantClaimIsRejected() throws Exception {
        getFile(TestTokens.bearer(TestTokens.withoutTenantClaim())).andExpect(status().isForbidden());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        getFile("Bearer not.a.jwt").andExpect(status().isForbidden());
    }

    @Test
    void tenantContextIsClearedAfterTheRequest() throws Exception {
        getFile(TestTokens.admin()).andExpect(status().isOk());
        assertThat(TenantContext.getCurrentTenant(), nullValue());
    }

    /**
     * The filter refuses a Tenant header that is not in the token before any resource runs (upstream
     * #50). It does so by throwing out of the filter, which MockMvc surfaces as the exception; Tomcat
     * answers 403 through its error path but logs an ERROR stack trace per refusal
     * (private known-errors.md #35, a clean 403 from the filter would be the better contract).
     */
    @Test
    void foreignTenantHeaderIsRefusedByTheFilter() {
        AccessDeniedException refused = assertThrows(AccessDeniedException.class, () ->
                mvc.perform(get("/api/fileData/" + Endpoint.ID)
                        .header("Tenant", FOREIGN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin())));
        assertThat(refused.getMessage(), containsString(FOREIGN));
        verify(fileDataService, never()).get(any());
    }

    @Test
    void ownTenantHeaderIsComparedCaseInsensitively() throws Exception {
        FileDataDTO file = new FileDataDTO();
        file.setTenantId(OWN);
        file.setName("a.pdf");
        file.setMimeType("application/pdf");
        file.setData(new byte[]{1});
        when(fileDataService.get(Endpoint.ID)).thenReturn(file);
        mvc.perform(get("/api/fileData/" + Endpoint.ID)
                        .header("Tenant", OWN.toLowerCase()).header(HttpHeaders.AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isOk());
    }

    /** Without a Tenant header the filter lets the request through; paths without a tenant still work. */
    @Test
    void requestWithoutTenantHeaderPassesTheFilter() throws Exception {
        mvc.perform(get("/").header(HttpHeaders.AUTHORIZATION, TestTokens.admin())).andExpect(status().isOk());
    }

    @Test
    void homePageIsPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string("Hello World!"));
    }
}
