package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.FOREIGN;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/** {@code BillingConfigResource}: lookup, create, update, delete (5 handlers). */
@WebMvcTest(controllers = BillingConfigResource.class)
class BillingConfigCrudWebTests extends BillingConfigWebSlice {

    private static final String CONFIGS = "/api/billingConfigs";

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingConfigResource.class).stream()
                .filter(e -> !e.pattern().endsWith("Image"))
                .toList();
    }

    @Test
    void configOfTheOwnTenantIsReturned() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(GET, CONFIGS + "/tenant/{tenantId}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(OWN))
                .andExpect(jsonPath("$.documentNumberSequenceLength").value(5));
    }

    @Test
    void createReturns201WithTheNewId() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(POST, CONFIGS).request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isCreated())
                .andExpect(content().string("\"" + CREATED_ID + "\""));
    }

    @Test
    void updateOfTheOwnConfigPassesTheBodyOn() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(PUT, CONFIGS + "/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk());
        ArgumentCaptor<BillingConfigDTO> body = ArgumentCaptor.forClass(BillingConfigDTO.class);
        verify(billingConfigService).update(eq(ID), body.capture());
        assertThat(body.getValue().getTenantId(), is(OWN));
        assertThat(body.getValue().getDocumentNumberSequenceLength(), is(5));
    }

    /**
     * F8: the body names the own community, the stored record belongs to another one. The stored
     * record's tenant must decide (AGENTS.md section 6); today only the body's is compared.
     */
    @Disabled("known-errors #19")
    @Test
    void updateOfAForeignStoredConfigIsForbidden() throws Exception {
        when(billingConfigService.get(ID)).thenReturn(config(FOREIGN));
        mvc.perform(endpoint(PUT, CONFIGS + "/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isForbidden());
        verify(billingConfigService, never()).update(any(), any());
    }

    @Test
    void deleteRemovesTheConfig() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(DELETE, CONFIGS + "/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNoContent());
        verify(billingConfigService).delete(ID);
    }
}
