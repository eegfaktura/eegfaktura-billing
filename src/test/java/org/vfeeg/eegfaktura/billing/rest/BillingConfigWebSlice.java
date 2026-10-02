package org.vfeeg.eegfaktura.billing.rest;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.model.FileDataDTO;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.FileDataService;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;

/**
 * Mocks and stubs shared by the two slices of {@code BillingConfigResource} (CRUD and images); the
 * subclasses carry the {@code @WebMvcTest}.
 */
abstract class BillingConfigWebSlice extends EndpointMatrix {

    static final UUID LOGO_ID = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    static final UUID FOOTER_ID = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    static final UUID CREATED_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @MockitoBean
    BillingConfigService billingConfigService;
    @MockitoBean
    FileDataService fileDataService;

    @Override
    protected Object[] services() {
        return new Object[]{billingConfigService, fileDataService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        when(billingConfigService.get(ID)).thenReturn(config(tenant));
        when(billingConfigService.getByTenantId(tenant)).thenReturn(config(tenant));
        when(billingConfigService.create(any())).thenReturn(CREATED_ID);
        when(fileDataService.get(LOGO_ID)).thenReturn(image(tenant, LOGO_ID));
        when(fileDataService.get(FOOTER_ID)).thenReturn(image(tenant, FOOTER_ID));
    }

    @Override
    protected void stubUnknownIds() {
        when(billingConfigService.get(any())).thenThrow(new NotFoundException());
        doThrow(new NotFoundException()).when(billingConfigService).update(any(), any());
    }

    static BillingConfigDTO config(String tenant) {
        BillingConfigDTO config = new BillingConfigDTO();
        config.setId(ID);
        config.setTenantId(tenant);
        config.setHeaderImageFileDataId(LOGO_ID);
        config.setFooterImageFileDataId(FOOTER_ID);
        config.setDocumentNumberSequenceLength(5);
        return config;
    }

    static FileDataDTO image(String tenant, UUID id) {
        FileDataDTO image = new FileDataDTO();
        image.setId(id);
        image.setTenantId(tenant);
        image.setName("logo.png");
        image.setMimeType("image/png");
        image.setData(Endpoint.PNG);
        return image;
    }
}
