package org.vfeeg.eegfaktura.billing.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
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
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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

    @Test
    void homePageIsPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string("Hello World!"));
    }
}
