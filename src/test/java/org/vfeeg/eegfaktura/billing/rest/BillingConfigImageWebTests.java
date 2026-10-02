package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.model.BillingConfigImageType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/** {@code BillingConfigResource}: logo and footer image upload, delete, download (6 handlers). */
@WebMvcTest(controllers = BillingConfigResource.class)
class BillingConfigImageWebTests extends BillingConfigWebSlice {

    private static final String CONFIG = "/api/billingConfigs/{id}";

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingConfigResource.class).stream()
                .filter(e -> e.pattern().endsWith("Image"))
                .toList();
    }

    @Test
    void logoUploadStoresTheFileAsLogo() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(POST, CONFIG + "/logoImage").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk());
        verify(billingConfigService).storeImage(argThat((BillingConfigDTO c) -> c.getTenantId().equals(OWN)),
                eq(BillingConfigImageType.LOGO_IMAGE),
                argThat((MultipartFile f) -> "logo.png".equals(f.getOriginalFilename())));
    }

    @Test
    void footerUploadStoresTheFileAsFooter() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(POST, CONFIG + "/footerImage").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk());
        verify(billingConfigService).storeImage(any(), eq(BillingConfigImageType.FOOTER_IMAGE), any());
    }

    @Test
    void deleteOfLogoAndFooterNamesTheImageType() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(DELETE, CONFIG + "/logoImage").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk());
        mvc.perform(endpoint(DELETE, CONFIG + "/footerImage").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk());
        verify(billingConfigService).deleteImage(any(), eq(BillingConfigImageType.LOGO_IMAGE));
        verify(billingConfigService).deleteImage(any(), eq(BillingConfigImageType.FOOTER_IMAGE));
    }

    @Test
    void logoDownloadReturnsTheImageWithItsType() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(GET, CONFIG + "/logoImage").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(content().bytes(Endpoint.PNG));
    }

    /** F10: the footer download demands a multipart part {@code file}; a plain GET must work. */
    @Disabled("known-errors #21")
    @Test
    void footerDownloadWorksWithoutAnUpload() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(get("/api/billingConfigs/" + ID + "/footerImage")
                        .header("Tenant", OWN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(content().bytes(Endpoint.PNG));
    }

    /** F10: a config without a logo must answer 404, not 500. */
    @Disabled("known-errors #21")
    @Test
    void logoDownloadWithoutALogoIsNotFound() throws Exception {
        BillingConfigDTO withoutLogo = config(OWN);
        withoutLogo.setHeaderImageFileDataId(null);
        when(billingConfigService.get(ID)).thenReturn(withoutLogo);
        // what Spring Data's findById(null) throws inside the real FileDataService
        when(fileDataService.get(null)).thenThrow(new IllegalArgumentException("The given id must not be null"));
        mvc.perform(endpoint(GET, CONFIG + "/logoImage").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNotFound());
    }
}
