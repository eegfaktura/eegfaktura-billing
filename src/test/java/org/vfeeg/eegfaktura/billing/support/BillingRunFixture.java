package org.vfeeg.eegfaktura.billing.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.MimeTypeUtils;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Master data rows and allocations of one community, ready for one billing run. Runs inside the
 * test transaction (JdbcTemplate and the services join it).
 */
public final class BillingRunFixture {

    private final String tenantId;
    private final List<BillingMasterdataBuilder> masters = new ArrayList<>();
    private final List<Allocation> allocations = new ArrayList<>();

    private BillingRunFixture(String tenantId) {
        this.tenantId = tenantId;
    }

    public static BillingRunFixture forTenant(String tenantId) {
        return new BillingRunFixture(tenantId);
    }

    /**
     * The five metering points of {@code billing_master_data.sql} (community {@code TE100100}) with
     * the allocations of {@code BillingIntegrationTests.TEST_ALLOCATIONS}.
     */
    public static BillingRunFixture legacyWorld() {
        String glueck = "8126ab63-3f5d-42a4-b6f5-8df17aa68158";
        String froehlich = "039e8d60-b6ba-459c-b5a1-0c31aa53a49";
        String sonne = "bf6c5e6c-a7f2-4499-b2bb-02bb6587b951";
        return forTenant(BillingMasterdataBuilder.DEFAULT_TENANT)
                .master(BillingMasterdataBuilder.consumer(glueck, "C0000000000000000000001234")
                        .name("Mag.", "Felix", "Glück", "Msc")
                        .column("participant_vat_id", "UST12345").column("participant_tax_id", "STR12345")
                        .column("participant_company_register_number", "FN12312A")
                        .column("participant_bank_name", "Raiffeisen Landesbank")
                        .column("participant_bank_iban", "AT01-1234-1234-1234")
                        .column("participant_bank_owner", "Felix Glück")
                        .column("participant_sepa_mandate_reference", "REF1234")
                        .column("participant_street", "Glücksweg 13")
                        .column("equipment_number", "Anlagenr 1234").column("metering_equipment_name", "Anlage Foo-Bar"),
                        "120.3489")
                .master(BillingMasterdataBuilder.consumer(glueck, "C0000000000000000000002234")
                        .name("Mag.", "Felix", "Glück", "Msc")
                        .column("participant_vat_id", "UST12345").column("participant_tax_id", "STR12345")
                        .column("participant_company_register_number", "FN12312A")
                        .column("participant_bank_name", "Raiffeisen Landesbank")
                        .column("participant_bank_iban", "AT01-1234-1234-1234")
                        .column("participant_bank_owner", "Felix Glück")
                        .column("participant_sepa_mandate_reference", "REF1234")
                        .column("participant_street", "Meisenweg 15")
                        .column("equipment_number", "Anlagenr 2234").column("metering_equipment_name", "Anlage Fix-Foxi")
                        .column("tariff_metering_point_vat", new java.math.BigDecimal("12.5")),
                        "777.5976")
                .master(BillingMasterdataBuilder.producer(froehlich, "P0000000000000000000002222")
                        .name(null, "Fridolin", "Fröhlich", "MBA")
                        .column("participant_bank_name", "Postsparkasse")
                        .column("participant_bank_iban", "AT01-2222-2222-2222")
                        .column("participant_bank_owner", "Fridolin Fröhlich")
                        .column("participant_sepa_mandate_reference", "REF2222")
                        .column("participant_sepa_mandate_issue_date", "2022-12-31")
                        .column("participant_street", "Fröhlichweg 15")
                        .column("equipment_number", "Anlagenr 2222").column("metering_equipment_name", "PV Oberweg")
                        .creditAmount("19.33"),
                        "2233.2209")
                .master(sonneRow(sonne, "P0000000000000000000003333", "Anlagenr 3333").creditAmount("7.77"),
                        "3355.3323")
                .master(sonneRow(sonne, "P0000000000000000000004444", "Anlagenr 4444").creditAmount("9.66")
                        .column("tariff_type", "Erzeuger mit ZP Gebühr").communitySubjectToVat(true)
                        .meteringPointFee("19.9", "Zählpunktgebühr", "20.0"),
                        "4477.4499");
    }

    private static BillingMasterdataBuilder sonneRow(String participantId, String mp, String equipment) {
        return BillingMasterdataBuilder.producer(participantId, mp)
                .name(null, null, "Sonne GmbH", null)
                .column("participant_vat_id", "UST3333").column("participant_tax_id", "STR3333")
                .column("participant_company_register_number", "FN3333A")
                .column("participant_bank_name", "Postsparkasse")
                .column("participant_bank_iban", "AT01-3333-3333-333")
                .column("participant_bank_owner", "Sonne GmbH")
                .column("participant_sepa_mandate_reference", "REF3333")
                .column("participant_sepa_mandate_issue_date", "2023-04-01")
                .column("participant_street", "Sonnenweg 42")
                .column("equipment_number", equipment).column("metering_equipment_name", "PV Sonne GmbH")
                .tariffVat("10.0").participantFee("10", "20.0");
    }

    /** Adds a master row and, when {@code kwh} is not null, the allocation for its metering point. */
    public BillingRunFixture master(BillingMasterdataBuilder master, String kwh) {
        master.tenant(tenantId);
        masters.add(master);
        if (kwh != null) {
            allocations.add(AllocationBuilder.of(master.participantId(), master.meteringPointId(), kwh));
        }
        return this;
    }

    public List<Allocation> allocations() {
        return List.copyOf(allocations);
    }

    public int masterCount() {
        return masters.size();
    }

    /** Creates {@code base.billing_masterdata} (DDL of the test resources) and inserts all rows. */
    public BillingRunFixture insertInto(JdbcTemplate jdbc) {
        try {
            String ddl = new ClassPathResource("base_masterdata_ddl.sql")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            jdbc.execute(ddl);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        masters.forEach(m -> m.insert(jdbc));
        return this;
    }

    /** Billing configuration of the tenant with the logo, prefixes TRECH/TGUT like the legacy test. */
    public UUID createConfig(BillingConfigService configService, FileDataRepository fileDataRepository,
                             boolean creditNotesForAllProducers) {
        try {
            FileData logo = new FileData();
            logo.setName("eeg-faktura-logo.png");
            logo.setMimeType(MimeTypeUtils.IMAGE_PNG.toString());
            logo.setTenantId(tenantId);
            logo.setData(new ClassPathResource("eeg-faktura-logo.png").getContentAsByteArray());
            BillingConfigDTO dto = new BillingConfigDTO();
            dto.setTenantId(tenantId);
            dto.setHeaderImageFileDataId(fileDataRepository.save(logo).getId());
            dto.setCreateCreditNotesForAllProducers(creditNotesForAllProducers);
            dto.setDocumentNumberSequenceLength(5);
            dto.setInvoiceNumberPrefix("TRECH");
            dto.setInvoiceNumberStart(42L);
            dto.setCreditNoteNumberPrefix("TGUT");
            dto.setCreditNoteNumberStart(73L);
            return configService.create(dto);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public DoBillingParams params(String clearingPeriodType, String clearingPeriodIdentifier, boolean preview) {
        DoBillingParams params = new DoBillingParams();
        params.setTenantId(tenantId);
        params.setClearingPeriodType(clearingPeriodType);
        params.setClearingPeriodIdentifier(clearingPeriodIdentifier);
        params.setPreview(preview);
        params.setAllocations(allocations.toArray(new Allocation[0]));
        return params;
    }
}
