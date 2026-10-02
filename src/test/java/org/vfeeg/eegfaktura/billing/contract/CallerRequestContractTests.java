package org.vfeeg.eegfaktura.billing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.vfeeg.eegfaktura.billing.config.JacksonConfig;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.model.BillingConfigImageType;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.rest.BillingConfigResource;
import org.vfeeg.eegfaktura.billing.rest.BillingResource;
import org.vfeeg.eegfaktura.billing.rest.TestTokens;
import org.vfeeg.eegfaktura.billing.rest.WebSliceTest;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.service.FileDataService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The request bodies the callers send (M6, fixtures in {@code contracts/web/} and {@code contracts/v3/})
 * pass the real web layer — security chain, Jackson as {@link JacksonConfig} configures it, {@code @Valid}
 * — and arrive in the DTOs with the values the caller meant. Unknown JSON fields are <b>tolerated</b>:
 * {@code JacksonConfig} disables {@code FAIL_ON_UNKNOWN_PROPERTIES} (pinned below, v3 relies on it).
 */
@WebMvcTest(controllers = {BillingResource.class, BillingConfigResource.class})
@Import(JacksonConfig.class)
class CallerRequestContractTests extends WebSliceTest {

    static final UUID CONFIG_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    static final UUID CREATED_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Autowired
    MockMvc mvc;
    @MockitoBean
    BillingService billingService;
    @MockitoBean
    BillingConfigService billingConfigService;
    @MockitoBean
    FileDataService fileDataService;

    private DoBillingParams postBilling(JsonNode fixture, JsonNode body) throws Exception {
        DoBillingResults results = new DoBillingResults();
        results.setAbstractText("Vorschau: erfolgreich abgeschlossen.");
        when(billingService.doBilling(any())).thenReturn(results);
        mvc.perform(ContractFixtures.request(fixture, body)).andExpect(status().isOk());
        ArgumentCaptor<DoBillingParams> params = ArgumentCaptor.forClass(DoBillingParams.class);
        verify(billingService).doBilling(params.capture());
        return params.getValue();
    }

    @Test
    void webPreviewRunArrivesAsPreview() throws Exception {
        JsonNode fixture = ContractFixtures.read("web/post-billing.json");
        DoBillingParams params = postBilling(fixture, fixture.get("body"));
        assertThat(params.getTenantId(), is("RC100001"));
        assertThat(params.getClearingPeriodType(), is("YM"));
        assertThat(params.getClearingPeriodIdentifier(), is("Abr_YM-2024-6"));
        assertThat(params.getClearingDocumentDate(), is(LocalDate.of(2024, 6, 28)));
        assertThat(params.isPreview(), is(true));
        assertThat(params.getBillingConfig(), nullValue());
        assertThat(params.getAllocations(), arrayWithSize(2));
        Allocation first = params.getAllocations()[0];
        assertThat(first.getParticipantId(), is("00000000-0000-0000-0000-000000000101"));
        assertThat(first.getMeteringPoint(), is("AT0099990000000000000000000000001"));
        assertThat(first.getAllocationKWh(), comparesEqualTo(new BigDecimal("123.456")));
        assertThat(params.getAllocations()[1].getAllocationKWh(), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    void v3FinalRunArrivesAsFinal() throws Exception {
        JsonNode fixture = ContractFixtures.read("v3/post-billing.json");
        DoBillingParams params = postBilling(fixture, fixture.get("body"));
        assertThat(params.isPreview(), is(false));
        assertThat(params.getClearingPeriodType(), is("YQ"));
        assertThat(params.getClearingPeriodIdentifier(), is("Abr_YQ-2024-2"));
        assertThat(params.getAllocations(), arrayWithSize(1));
        assertThat(params.getAllocations()[0].getAllocationKWh(), comparesEqualTo(new BigDecimal("351.12")));
    }

    /**
     * The JSON key is {@code preview} (Lombok's {@code isPreview} field). A caller sending {@code
     * isPreview} is not refused: the key is ignored and the run is FINAL — the trap v3 documents in
     * {@code LegacyDoBillingParams}. Pinned as the code decides today.
     */
    @Test
    void keyIsPreviewIsIgnoredAndTheRunIsFinal() throws Exception {
        JsonNode fixture = ContractFixtures.read("v3/post-billing.json");
        ObjectNode body = fixture.get("body").deepCopy();
        body.remove("preview");
        body.put("isPreview", true);
        assertThat(postBilling(fixture, body).isPreview(), is(false));
    }

    @Test
    void unknownFieldsAreTolerated() throws Exception {
        JsonNode fixture = ContractFixtures.read("web/post-billing.json");
        ObjectNode body = fixture.get("body").deepCopy();
        body.put("someFieldBillingDoesNotKnow", "x");
        assertThat(postBilling(fixture, body).isPreview(), is(true));
    }

    private BillingConfigDTO createConfig(String file) throws Exception {
        when(billingConfigService.create(any())).thenReturn(CREATED_ID);
        mvc.perform(ContractFixtures.request(ContractFixtures.read(file)))
                .andExpect(status().isCreated())
                // both callers read the new id as a JSON string
                .andExpect(content().json("\"" + CREATED_ID + "\""));
        ArgumentCaptor<BillingConfigDTO> dto = ArgumentCaptor.forClass(BillingConfigDTO.class);
        verify(billingConfigService).create(dto.capture());
        return dto.getValue();
    }

    @Test
    void webCreatesTheConfigWithItsDefaults() throws Exception {
        BillingConfigDTO dto = createConfig("web/post-billing-configs.json");
        assertThat(dto.getTenantId(), is("RC100001"));
        assertThat(dto.isCreateCreditNotesForAllProducers(), is(true));
        assertThat(dto.getDocumentNumberSequenceLength(), is(5));
        assertThat(dto.getInvoiceNumberStart(), is(0L));
        assertThat(dto.getCreditNoteNumberStart(), is(0L));
        assertThat(dto.getId(), nullValue());
    }

    /** v3 sends every key, nulls included ({@code @JsonInclude(ALWAYS)}); the nulls stay null. */
    @Test
    void v3CreatesTheConfigWithExplicitNulls() throws Exception {
        BillingConfigDTO dto = createConfig("v3/post-billing-configs.json");
        assertThat(dto.getTenantId(), is("RC100001"));
        assertThat(dto.isCreateCreditNotesForAllProducers(), is(true));
        assertThat(dto.getDocumentNumberSequenceLength(), is(5));
        assertThat(dto.getInvoiceNumberStart(), nullValue());
        assertThat(dto.getInvoiceNumberPrefix(), nullValue());
        assertThat(dto.getHeaderImageFileDataId(), nullValue());
    }

    private BillingConfigDTO updateConfig(String file) throws Exception {
        mvc.perform(ContractFixtures.request(ContractFixtures.read(file))).andExpect(status().isOk());
        ArgumentCaptor<BillingConfigDTO> dto = ArgumentCaptor.forClass(BillingConfigDTO.class);
        verify(billingConfigService).update(eq(CONFIG_ID), dto.capture());
        return dto.getValue();
    }

    @Test
    void webUpdateIsAFullOverwrite() throws Exception {
        BillingConfigDTO dto = updateConfig("web/put-billing-config.json");
        assertThat(dto.getId(), is(CONFIG_ID));
        assertThat(dto.getTenantId(), is("RC100001"));
        assertThat(dto.getHeaderImageFileDataId(), is(UUID.fromString("00000000-0000-0000-0000-00000000f001")));
        assertThat(dto.getFooterImageFileDataId(), nullValue());
        assertThat(dto.isCreateCreditNotesForAllProducers(), is(true));
        assertThat(dto.getBeforeItemsTextInvoice(), is("Wir erlauben uns, folgende Leistungen zu verrechnen:"));
        assertThat(dto.getTermsTextCreditNote(), is("Der Betrag wird überwiesen."));
        assertThat(dto.getFooterText(), is("EEG Sonnenschein, Hauptstraße 1, 8010 Graz"));
        assertThat(dto.getDocumentNumberSequenceLength(), is(5));
        assertThat(dto.getInvoiceNumberPrefix(), is("RE"));
        assertThat(dto.getInvoiceNumberStart(), is(1L));
        assertThat(dto.getCreditNoteNumberPrefix(), is("GS"));
        assertThat(dto.getCreditNoteNumberStart(), is(1L));
    }

    static List<ContractFixtures.Call> imageUploads() {
        return ContractFixtures.calls().stream().filter(c -> c.multipart() != null).toList();
    }

    /** Both callers upload the image as the multipart part {@code file} with an image content type. */
    @ParameterizedTest
    @MethodSource("imageUploads")
    void imageUploadArrivesAsThePartFile(ContractFixtures.Call call) throws Exception {
        BillingConfigDTO config = new BillingConfigDTO();
        config.setId(CONFIG_ID);
        config.setTenantId("RC100001");
        when(billingConfigService.get(CONFIG_ID)).thenReturn(config);
        mvc.perform(multipart(call.path())
                        .file(new MockMultipartFile(call.multipart(), "logo.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .header("Tenant", "RC100001").header(HttpHeaders.AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isOk());
        BillingConfigImageType type = call.path().endsWith("/logoImage")
                ? BillingConfigImageType.LOGO_IMAGE : BillingConfigImageType.FOOTER_IMAGE;
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(billingConfigService).storeImage(eq(config), eq(type), file.capture());
        assertThat(file.getValue().getContentType(), is(MediaType.IMAGE_PNG_VALUE));
        assertThat(file.getValue().getBytes(), is(PNG));
    }

    @Test
    void v3UpdateCarriesTheImageIdsAndTheNumbering() throws Exception {
        BillingConfigDTO dto = updateConfig("v3/put-billing-config.json");
        assertThat(dto.getHeaderImageFileDataId(), is(UUID.fromString("00000000-0000-0000-0000-00000000f001")));
        assertThat(dto.getFooterImageFileDataId(), is(UUID.fromString("00000000-0000-0000-0000-00000000f002")));
        assertThat(dto.getDocumentNumberSequenceLength(), is(6));
        assertThat(dto.getInvoiceNumberStart(), is(100L));
        assertThat(dto.getCreditNoteNumberStart(), is(200L));
        assertThat(dto.getBeforeItemsTextCreditNote(), nullValue());
    }
}
