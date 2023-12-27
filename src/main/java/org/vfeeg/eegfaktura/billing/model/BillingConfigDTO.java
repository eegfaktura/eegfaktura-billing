package org.vfeeg.eegfaktura.billing.model;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class BillingConfigDTO {

    private UUID id;
    @NotNull
    private String tenantId;
    private UUID headerImageFileDataId;
    private UUID footerImageFileDataId;
    private String beforeItemsTextInvoice;
    private String beforeItemsTextCreditNote;
    private String beforeItemsTextInfo;
    private String afterItemsTextInvoice;
    private String afterItemsTextCreditNote;
    private String afterItemsTextInfo;
    private String termsTextInvoice;
    private String termsTextCreditNote;
    private String termsTextInfo;
    private String footerText;
    private int documentNumberSequenceLength;
    private UUID customTemplateFileDataId;
    private String invoiceNumberPrefix;
    private Long invoiceNumberStart;
    private String creditNoteNumberPrefix;
    private Long creditNoteNumberStart;
}
