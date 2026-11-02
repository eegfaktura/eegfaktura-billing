package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.service.FileDataService;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

@WebMvcTest(controllers = FileDataResource.class)
class FileDataWebTests extends EndpointMatrix {

    @MockitoBean
    FileDataService fileDataService;

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(FileDataResource.class);
    }

    @Override
    protected Object[] services() {
        return new Object[]{fileDataService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        when(fileDataService.get(ID)).thenReturn(BillingConfigWebSlice.image(tenant, ID));
    }

    @Override
    protected void stubUnknownIds() {
        when(fileDataService.get(any())).thenThrow(new NotFoundException());
    }

    @Test
    void fileIsAnAttachmentWithItsNameAndType() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(GET, "/api/fileData/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"logo.png\""))
                .andExpect(content().bytes(Endpoint.PNG));
    }
}
