package org.vfeeg.eegfaktura.billing.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds one row of the table {@code base.billing_masterdata} (one metering point of one participant
 * with its tariff) and inserts it with a {@link JdbcTemplate}, so it takes part in the test
 * transaction. The table must exist: run {@code /base_masterdata_ddl.sql} first
 * ({@code @Sql("/base_masterdata_ddl.sql")}).
 *
 * <p>Defaults: a consumer of community {@code TE100100} without VAT and a participant fee of 10.00.
 * Every column of the table can be set with {@link #column(String, Object)}; amounts as
 * {@link BigDecimal} (no {@code double}).
 */
public final class BillingMasterdataBuilder {

    public static final String DEFAULT_TENANT = "TE100100";

    private final Map<String, Object> columns = new LinkedHashMap<>();

    private BillingMasterdataBuilder() {
    }

    /** Consumer row with the defaults of the legacy fixture. */
    public static BillingMasterdataBuilder consumer(String participantId, String meteringPointId) {
        return base(participantId, meteringPointId)
                .column("metering_point_type", "1")
                .column("tariff_type", "Verbraucher")
                .column("tariff_working_fee_per_consumedkwh", bd("12.83"))
                .column("tariff_credit_amount_per_producedkwh", bd("0"));
    }

    /** Producer row with the defaults of the legacy fixture. */
    public static BillingMasterdataBuilder producer(String participantId, String meteringPointId) {
        return base(participantId, meteringPointId)
                .column("metering_point_type", "0")
                .column("tariff_type", "Erzeuger")
                .column("tariff_working_fee_per_consumedkwh", bd("15"))
                .column("tariff_credit_amount_per_producedkwh", bd("19"));
    }

    private static BillingMasterdataBuilder base(String participantId, String meteringPointId) {
        return new BillingMasterdataBuilder()
                .column("participant_id", participantId)
                .column("participant_firstname", "Vorname")
                .column("participant_lastname", "Nachname")
                .column("participant_email", "member@example.invalid")
                .column("participant_number", "")
                .column("participant_bank_name", "Testbank")
                .column("participant_bank_iban", "AT01-0000-0000-0000")
                .column("participant_bank_owner", "Vorname Nachname")
                .column("participant_sepa_mandate_reference", "REF0000")
                .column("participant_sepa_mandate_issue_date", "2022-01-01")
                .column("participant_street", "Testweg 1")
                .column("participant_zip_code", "1234")
                .column("participant_city", "Fuxholzen")
                .column("metering_point_id", meteringPointId)
                .column("equipment_number", "Anlagenr " + meteringPointId)
                .column("metering_equipment_name", "Anlage " + meteringPointId)
                .column("tenant_id", DEFAULT_TENANT)
                .column("eec_id", DEFAULT_TENANT)
                .column("eec_name", "Energiegemeinschaft Holy Grail")
                .column("eec_vat_id", "UST4321")
                .column("eec_tax_id", "STR4321")
                .column("eec_company_register_number", "FN4321A")
                .column("eec_email", "eeg-holy-grail@gmx.at")
                .column("eec_phone", "+43 555 123456")
                .column("eec_subject_to_vat", false)
                .column("eec_street", "Feldweg 12")
                .column("eec_zip_code", "1234")
                .column("eec_city", "Fuxholzen")
                .column("eec_bank_name", "Sparkasse OÖ")
                .column("eec_bank_iban", "AT01-4321-4321-4321")
                .column("eec_bank_owner", "Energiegemeinschaft Holy Grail")
                .column("tariff_name", "Standard")
                .column("tariff_text", "Text zu Tarif Standard")
                .column("tariff_id", "75d44a4f-35ef-11ef-9d95-b657056770ae")
                .column("tariff_version", 13)
                .column("tariff_billing_period", "Q")
                .column("tariff_use_vat", false)
                .column("tariff_vat_in_percent", bd("0"))
                .column("tariff_participant_fee", bd("10"))
                .column("tariff_participant_fee_name", "Mitgliedsgebühr")
                .column("tariff_participant_fee_text", "Text zu Tarif Mitgliedsgebühr")
                .column("tariff_participant_fee_use_vat", false)
                .column("tariff_participant_fee_vat_in_percent", bd("0"))
                .column("tariff_participant_fee_discount", bd("0"))
                .column("tariff_basic_fee", bd("0"))
                .column("tariff_discount", bd("0"))
                .column("tariff_metering_point_vat", bd("0"))
                .column("tariff_freekwh", bd("0"));
    }

    /** Sets any column of {@code base.billing_masterdata}; the column name is a fixed identifier. */
    public BillingMasterdataBuilder column(String name, Object value) {
        columns.put(name, value);
        return this;
    }

    public BillingMasterdataBuilder tenant(String tenantId) {
        return column("tenant_id", tenantId).column("eec_id", tenantId);
    }

    public BillingMasterdataBuilder name(String title, String first, String last, String titleAfter) {
        return column("participant_title_before", title).column("participant_firstname", first)
                .column("participant_lastname", last).column("participant_title_after", titleAfter);
    }

    public BillingMasterdataBuilder communitySubjectToVat(boolean subject) {
        return column("eec_subject_to_vat", subject);
    }

    /** Percent as text, e.g. {@code "10.0"}; switches VAT on for the working fee. */
    public BillingMasterdataBuilder tariffVat(String percent) {
        return column("tariff_use_vat", true).column("tariff_vat_in_percent", bd(percent));
    }

    /** Participant fee with its own VAT setting. */
    public BillingMasterdataBuilder participantFee(String amount, String vatPercentOrNull) {
        column("tariff_participant_fee", bd(amount));
        column("tariff_participant_fee_use_vat", vatPercentOrNull != null);
        return column("tariff_participant_fee_vat_in_percent", bd(vatPercentOrNull == null ? "0" : vatPercentOrNull));
    }

    public BillingMasterdataBuilder meteringPointFee(String amount, String text, String vatPercent) {
        return column("tariff_use_metering_point_fee", true).column("tariff_metering_point_fee", bd(amount))
                .column("tariff_metering_point_fee_text", text).column("tariff_metering_point_vat", bd(vatPercent));
    }

    public BillingMasterdataBuilder workingFee(String perKwh) {
        return column("tariff_working_fee_per_consumedkwh", bd(perKwh));
    }

    public BillingMasterdataBuilder creditAmount(String perKwh) {
        return column("tariff_credit_amount_per_producedkwh", bd(perKwh));
    }

    public BillingMasterdataBuilder freeKwh(String kwh) {
        return column("tariff_freekwh", bd(kwh));
    }

    public Map<String, Object> columns() {
        return new LinkedHashMap<>(columns);
    }

    public String participantId() {
        return (String) columns.get("participant_id");
    }

    public String meteringPointId() {
        return (String) columns.get("metering_point_id");
    }

    public void insert(JdbcTemplate jdbc) {
        String names = String.join(", ", columns.keySet());
        String marks = String.join(", ", columns.keySet().stream().map(k -> "?").toList());
        jdbc.update("insert into base.billing_masterdata (" + names + ") values (" + marks + ")",
                columns.values().toArray());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
