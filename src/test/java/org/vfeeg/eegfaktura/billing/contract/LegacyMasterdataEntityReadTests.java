package org.vfeeg.eegfaktura.billing.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.vfeeg.eegfaktura.billing.domain.BillingMasterdata;
import org.vfeeg.eegfaktura.billing.domain.MeteringPointType;
import org.vfeeg.eegfaktura.billing.repos.BillingMasterdataRepository;
import org.vfeeg.eegfaktura.billing.support.LegacyBaseDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * The entity read end to end (M6): Hibernate maps {@code BillingMasterdata} through its {@code
 * @Subselect} onto the <b>real</b> legacy view of {@link LegacyBaseDatabase}, with the rows of
 * {@code contracts/legacy-masterdata-rows.sql} in the base tables. This proves what the column check
 * cannot: the values arrive in the fields billing uses (ordinal meter type, dates, numbers, the
 * newest tariff version, the billing address, the SEPA mandate from the bank account).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
@Sql("/contracts/legacy-masterdata-rows.sql")
class LegacyMasterdataEntityReadTests {

    static final String CONSUMER_METER = "AT0099990000000000000000000000001";
    static final String PRODUCER_METER = "AT0099990000000000000000000000002";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        LegacyBaseDatabase.register(registry);
    }

    @Autowired
    BillingMasterdataRepository repository;

    private Map<String, BillingMasterdata> rowsByMeter() {
        List<BillingMasterdata> rows = repository.findByTenantId("RC100001");
        assertThat(rows, hasSize(2));
        return rows.stream().collect(Collectors.toMap(BillingMasterdata::getMeteringPointId, Function.identity()));
    }

    @Test
    void participantAndCommunityColumnsArriveInTheEntity() {
        for (BillingMasterdata row : rowsByMeter().values()) {
            assertThat(row.getParticipantId(), is("00000000-0000-0000-0000-000000000101"));
            assertThat(row.getParticipantTitleBefore(), is("Dr."));
            assertThat(row.getParticipantFirstname(), is("Anna"));
            assertThat(row.getParticipantLastname(), is("Muster"));
            assertThat(row.getParticipantNumber(), is("0001"));
            assertThat(row.getParticipantVatId(), is("ATU87654321"));
            assertThat(row.getParticipantEmail(), is("anna.muster@example.test"));
            assertThat(row.getParticipantStreet(), is("Gartenweg 5"));
            assertThat(row.getParticipantZipCode(), is("8020"));
            assertThat(row.getParticipantCity(), is("Graz"));
            assertThat(row.getTenantId(), is("RC100001"));
            assertThat(row.getEecId(), is("RC100001"));
            assertThat(row.getEecName(), is("Erneuerbare-Energie-Gemeinschaft Sonnenschein"));
            assertThat(row.getEecStreet(), is("Hauptstraße 1"));
            assertThat(row.getEecZipCode(), is("8010"));
            assertThat(row.getEecCompanyRegisterNumber(), is("FN 123456a"));
            assertThat(row.getEecSubjectToVat(), is(true));
            assertThat(row.getEecBankIban(), is("AT611904300234573201"));
            assertThat(row.getEecBankCreditorId(), is("AT12ZZZ00000000001"));
        }
    }

    @Test
    void sepaMandateComesFromTheBankAccount() {
        for (BillingMasterdata row : rowsByMeter().values()) {
            assertThat(row.getParticipantBankIban(), is("AT483200000012345864"));
            assertThat(row.getParticipantBankOwner(), is("Anna Muster"));
            assertThat(row.getParticipantBankName(), is("Raiffeisen Test"));
            assertThat(row.getParticipantSepaMandateReference(), is("MR-0001"));
            assertThat(row.getParticipantSepaMandateIssueDate(), is(LocalDate.of(2024, 2, 1)));
            assertThat(row.getParticipantSepaDirectDebit(), is("CORE"));
        }
    }

    /** The view says 0 for GENERATION, 1 otherwise; the entity reads it by ordinal. */
    @Test
    void meterDirectionMapsToTheOrdinalMeteringPointType() {
        Map<String, BillingMasterdata> rows = rowsByMeter();
        assertThat(rows.get(CONSUMER_METER).getMeteringPointType(), is(MeteringPointType.CONSUMER));
        assertThat(rows.get(PRODUCER_METER).getMeteringPointType(), is(MeteringPointType.PRODUCER));
        assertThat(rows.get(CONSUMER_METER).getMeteringEquipmentName(), is("Haushalt"));
        assertThat(rows.get(PRODUCER_METER).getEquipmentNumber(), is("A-2"));
        assertThat(rows.get(CONSUMER_METER).getId(), not(rows.get(PRODUCER_METER).getId()));
    }

    @Test
    void consumerTariffIsTheNewestVersion() {
        BillingMasterdata consumer = rowsByMeter().get(CONSUMER_METER);
        assertThat(consumer.getTariffId(), is("00000000-0000-0000-0000-0000000000c1"));
        assertThat(consumer.getTariffVersion(), is(2));
        assertThat(consumer.getTariffName(), is("Bezug"));
        assertThat(consumer.getTariffType(), is("VZP"));
        assertThat(consumer.getTariffBillingPeriod(), is("monthly"));
        assertThat(consumer.getTariffUseVat(), is(true));
        assertThat(consumer.getTariffVatInPercent(), comparesEqualTo(new BigDecimal("20")));
        assertThat(consumer.getTariffWorkingFeePerConsumedkwh(), comparesEqualTo(new BigDecimal("12.5")));
        assertThat(consumer.getTariffCreditAmountPerProducedkwh(), comparesEqualTo(new BigDecimal("12.5")));
        assertThat(consumer.getTariffDiscount(), comparesEqualTo(new BigDecimal("5")));
        assertThat(consumer.getTariffFreekwh(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(consumer.getTariffBasicFee(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(consumer.getTariffUseMeteringPointFee(), is(false));
    }

    @Test
    void producerTariffCarriesTheMeteringPointFee() {
        BillingMasterdata producer = rowsByMeter().get(PRODUCER_METER);
        assertThat(producer.getTariffType(), is("EZP"));
        assertThat(producer.getTariffUseVat(), is(false));
        assertThat(producer.getTariffUseMeteringPointFee(), is(true));
        assertThat(producer.getTariffMeteringPointFee(), comparesEqualTo(new BigDecimal("3.5")));
        assertThat(producer.getTariffMeteringPointVat(), comparesEqualTo(new BigDecimal("20")));
        assertThat(producer.getTariffMeteringPointFeeText(), is(""));
    }

    @Test
    void memberFeeComesFromTheParticipantsEegTariff() {
        for (BillingMasterdata row : rowsByMeter().values()) {
            assertThat(row.getTariffParticipantFee(), comparesEqualTo(new BigDecimal("12.5")));
            assertThat(row.getTariffParticipantFeeName(), is("Mitgliedsbeitrag"));
            assertThat(row.getTariffParticipantFeeText(), is(""));
            assertThat(row.getTariffParticipantFeeUseVat(), is(true));
            assertThat(row.getTariffParticipantFeeVatInPercent(), comparesEqualTo(new BigDecimal("20")));
            assertThat(row.getTariffParticipantFeeDiscount(), comparesEqualTo(BigDecimal.ZERO));
        }
    }
}
