package org.vfeeg.eegfaktura.billing.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.vfeeg.eegfaktura.billing.rest.Endpoint;
import org.vfeeg.eegfaktura.billing.rest.FileDataResource;
import org.vfeeg.eegfaktura.billing.rest.TestTokens;
import org.vfeeg.eegfaktura.billing.rest.WebSliceTest;
import org.vfeeg.eegfaktura.billing.model.FileDataDTO;
import org.vfeeg.eegfaktura.billing.service.FileDataService;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/**
 * Snyk review 28.10.2026: with the profile "dev" the former DevSecurityConfig replaced the JWT chain and left every
 * endpoint open. The JWT chain now applies in every profile.
 */
@WebMvcTest(controllers = FileDataResource.class)
@ActiveProfiles("dev")
class DevProfileSecurityWebTests extends WebSliceTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    FileDataService fileDataService;

    @Test
    void withTheDevProfileARequestWithoutTokenIsStillRefused() throws Exception {
        mvc.perform(get("/api/fileData/" + Endpoint.ID).header("Tenant", OWN)).andExpect(status().isForbidden());
        verifyNoInteractions(fileDataService);
    }

    @Test
    void withTheDevProfileAnAdminTokenStillWorks() throws Exception {
        FileDataDTO file = new FileDataDTO();
        file.setTenantId(OWN);
        file.setName("a.pdf");
        file.setMimeType("application/pdf");
        file.setData(new byte[]{1});
        when(fileDataService.get(Endpoint.ID)).thenReturn(file);
        mvc.perform(get("/api/fileData/" + Endpoint.ID).header("Tenant", OWN)
                .header("Authorization", TestTokens.admin())).andExpect(status().isOk());
    }
}
