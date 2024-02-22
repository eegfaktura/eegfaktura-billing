package org.vfeeg.eegfaktura.billing.model;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.vfeeg.eegfaktura.billing.domain.MeteringPointType;

import java.math.BigDecimal;
import java.util.UUID;


@Getter
@Setter
public class BillingDocumentItemDTO {

    private UUID id;

    private String tenantId;

    private String clearingPeriodType;

    private String clearingPeriodIdentifier;

    private BigDecimal amount;

    private String meteringPointId;

    private MeteringPointType meteringPointType;

    private String text;

    private String documentText;

    private String unit;

    private BigDecimal pricePerUnit;

    private String ppuUnit;

    private BigDecimal netValue;

    private BigDecimal discountPercent;

    private BigDecimal vatPercent;

    private BigDecimal vatValueInEuro;

    private BigDecimal grossValue;

    @NotNull
    private UUID billingDocumentId;

}
