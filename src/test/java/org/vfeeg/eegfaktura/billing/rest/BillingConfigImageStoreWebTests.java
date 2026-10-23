package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.FileDataService;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/**
 * Image upload through the real {@code BillingConfigService} (repositories mocked): replacing a logo
 * and refusing a wrong file type (F16, {@code known-errors.md} #27).
 */
@WebMvcTest(controllers = BillingConfigResource.class)
@Import({BillingConfigService.class, FileDataService.class})
class BillingConfigImageStoreWebTests extends WebSliceTest {

    private static final UUID OLD_LOGO = UUID.fromString("00000000-0000-0000-0000-0000000001d1");
    private static final UUID NEW_LOGO = UUID.fromString("00000000-0000-0000-0000-0000000001d2");

    @Autowired
    MockMvc mvc;
    @MockitoBean
    BillingConfigRepository billingConfigRepository;
    @MockitoBean
    FileDataRepository fileDataRepository;

    private void storedConfigWithLogo() {
        BillingConfig config = BillingConfig.builder().id(ID).tenantId(OWN).headerImageFileDataId(OLD_LOGO).build();
        when(billingConfigRepository.findById(ID)).thenReturn(Optional.of(config));
        when(fileDataRepository.save(any())).thenAnswer(invocation -> {
            FileData saved = invocation.getArgument(0);
            saved.setId(NEW_LOGO);
            return saved;
        });
    }

    private static MockHttpServletRequestBuilder upload(String contentType) {
        return multipart("/api/billingConfigs/" + ID + "/logoImage")
                .file(new MockMultipartFile("file", "logo", contentType, Endpoint.PNG))
                .header("Tenant", OWN)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin());
    }

    @Test
    void replacingTheLogoStoresTheNewOneAndDeletesTheOld() throws Exception {
        storedConfigWithLogo();
        mvc.perform(upload("image/png")).andExpect(status().isOk());
        verify(fileDataRepository).save(argThat(f -> "image/png".equals(f.getMimeType())));
        verify(billingConfigRepository).save(argThat(c -> NEW_LOGO.equals(c.getHeaderImageFileDataId())));
        verify(fileDataRepository).deleteById(OLD_LOGO);
    }

    /** F16: the old logo is deleted before the config is updated; a failed update loses it. */
    @Disabled("known-errors #27")
    @Test
    void oldLogoSurvivesAFailedUpdate() throws Exception {
        storedConfigWithLogo();
        when(billingConfigRepository.save(any())).thenThrow(new IllegalStateException("database down"));
        mvc.perform(upload("image/png"));
        verify(fileDataRepository, never()).deleteById(OLD_LOGO);
    }

    /** F16: a file that is not png/jpg/gif is the caller's error (4xx), not 500. */
    @Disabled("known-errors #27")
    @Test
    void wrongFileTypeIsAClientError() throws Exception {
        storedConfigWithLogo();
        int status = mvc.perform(upload("text/plain")).andReturn().getResponse().getStatus();
        assertThat(status, allOf(greaterThanOrEqualTo(400), lessThan(500)));
        verify(fileDataRepository, never()).save(any());
    }

    /** A config without a footer image answers 404, not 500 (upstream #51). */
    @Test
    void footerImageOfAConfigWithoutOneIsNotFound() throws Exception {
        storedConfigWithLogo();
        mvc.perform(get("/api/billingConfigs/" + ID + "/footerImage")
                        .header("Tenant", OWN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isNotFound());
    }
}
