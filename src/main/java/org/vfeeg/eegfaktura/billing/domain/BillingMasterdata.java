package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Immutable
@Subselect("select md5(random()::text || clock_timestamp()::text)::uuid as id, m.* from base.billing_masterdata m")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BillingMasterdata {

    @Id
    private String id;
    private String participantId;
    private String participantTitleBefore;
    private String participantFirstname;
    private String participantLastname;
    private String participantTitleAfter;
    private String participantVatId;
    private String participantTaxId;
    private String participantCompanyRegisterNumber;
    private String participantEmail;
    private String participantNumber; // Mitgliedsnummer
    private String participantBankName;
    private String participantBankIban;
    private String participantBankOwner;
    private String participantSepaMandateReference;
    private LocalDate participantSepaMandateIssueDate;
    private String meteringPointId;
    private String equipmentNumber; // Anlagennummer
    private String meteringEquipmentName;
    private MeteringPointType meteringPointType;
    private String tenantId;
    private String eecId;
    private String eecName;
    private String eecVatId;
    private String eecTaxId;
    private String eecCompanyRegisterNumber;
    private String eecEmail;
    private String eecPhone;
    private Boolean eecSubjectToVat;
    private String eecStreet;
    private String eecZipCode;
    private String eecCity;
    private String eecBankName;
    private String eecBankIban;
    private String eecBankOwner;

    private String participantStreet;
    private String participantZipCode;
    private String participantCity;
    private String tariffType;
    private String tariffName;
    private String tariffText;
    private String tariffBillingPeriod;
    private Boolean tariffUseVat;
    private BigDecimal tariffVatInPercent;
    private BigDecimal tariffParticipantFee;
    private String tariffParticipantFeeName;
    private String tariffParticipantFeeText;
    private Boolean tariffParticipantFeeUseVat;
    private BigDecimal tariffParticipantFeeVatInPercent;
    private BigDecimal tariffParticipantFeeDiscount;
    private BigDecimal tariffBasicFee;
    private BigDecimal tariffDiscount;
    private BigDecimal tariffWorkingFeePerConsumedkwh;
    private BigDecimal tariffCreditAmountPerProducedkwh;
    private BigDecimal tariffFreekwh;
    private Boolean tariffUseMeteringPointFee;
    private BigDecimal tariffMeteringPointFee;
    private String tariffMeteringPointFeeText;

    public Boolean getTariffUseVat() {
        return Objects.requireNonNullElse(tariffUseVat, Boolean.FALSE);
    }

    public Boolean getTariffParticipantFeeUseVat() {
        return Objects.requireNonNullElse(tariffParticipantFeeUseVat, Boolean.FALSE);
    }

    public Boolean getEecSubjectToVat() {
        return Objects.requireNonNullElse(eecSubjectToVat, Boolean.FALSE);
    }

    public Boolean getTariffUseMeteringPointFee() {
        return Objects.requireNonNullElse(tariffUseMeteringPointFee, Boolean.FALSE);
    }
}
