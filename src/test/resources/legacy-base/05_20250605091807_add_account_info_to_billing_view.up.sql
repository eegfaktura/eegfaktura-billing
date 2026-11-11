-- Copied unchanged from eegfaktura-backend/migrations/20250605091807_add_account_info_to_billing_view.up.sql at da3d505a5e6182436deb0ef612ffd803629ea3eb (AGPL-3.0).
-- The legacy base schema for v3's local/E2E stacks and the ITs (m02 T1); applied by docker/postgres-init/40-legacy-base.sh.
-- create "billing_masterdata" view
DROP VIEW IF EXISTS base.billing_masterdata;
CREATE OR REPLACE VIEW
    base.billing_masterdata AS
SELECT p.id                                                    AS participant_id,
       p."titleBefore"                                         AS participant_title_before,
       p.firstname                                             AS participant_firstname,
       p."participantNumber"                                   AS participant_number,
       p.lastname                                              AS participant_lastname,
       p."titleAfter"                                          AS participant_title_after,
       p."vatNumber"                                           AS participant_vat_id,
       p."taxNumber"                                           AS participant_tax_id,
       p."companyRegisterNumber"                               AS participant_company_register_number,
       COALESCE(b."mandate_reference", p."participantNumber")  AS participant_sepa_mandate_reference,
       COALESCE(b."mandate_date", p."participantSince")        AS participant_sepa_mandate_issue_date,
       b.sepa_direct_debit                                     AS participant_sepa_direct_debit,
       pm.metering_point_id,
       pm."equipmentNumber"                                    AS equipment_number,
       pm."equipmentName"                                      AS metering_equipment_name,
       CASE
           WHEN pm.direction = 'GENERATION'::text THEN 0
           ELSE 1
           END                                                 AS metering_point_type,
       c.tenant                                                AS tenant_id,
       c."rcNumber"                                            AS eec_id,
       c.description                                           AS eec_name,
       c."vatNumber"                                           AS eec_vat_id,
       c."taxNumber"                                           AS eec_tax_id,
       c."businessNr"                                          AS eec_company_register_number,
       c.subjecttovat                                          AS eec_subject_to_vat,
       c.phone                                                 AS eec_phone,
       c.email                                                 AS eec_email,
       c.website                                               AS eec_website,
       concat(c.street, ' ', c."streetNumber")                 AS eec_street,
       c.zip                                                   AS eec_zip_code,
       c.city                                                  AS eec_city,
       concat(p_address.street, ' ', p_address."streetNumber") AS participant_street,
       p_address.zip                                           AS participant_zip_code,
       p_address.city                                          AS participant_city,
       t.type                                                  AS tariff_type,
       t.name                                                  AS tariff_name,
       t."billingPeriod"                                       AS tariff_billing_period,
       t."useVat"                                              AS tariff_use_vat,
       t."vatSupplementaryText"                                AS tariff_text,
       t."vatInPercent"                                        AS tariff_vat_in_percent,
       t."useMeteringPointFee"                                 AS tariff_use_metering_point_fee,
       t."meteringPointFee"                                    AS tariff_metering_point_fee,
       t."meteringPointVat"                                    AS tariff_metering_point_vat,
       ''::text                                                AS tariff_metering_point_fee_text,
       ''::text                                                AS tariff_participant_fee_text,
       COALESCE(tp.version, 0)                                 AS tariff_participant_version,
       COALESCE(tp."participantFee", 0::double precision)      AS tariff_participant_fee,
       COALESCE(tp.name, ''::text)                             AS tariff_participant_fee_name,
       COALESCE(tp."useVat", false)                            AS tariff_participant_fee_use_vat,
       COALESCE(tp."vatInPercent", 0::numeric)                 AS tariff_participant_fee_vat_in_percent,
       COALESCE(tp.discount, 0)                                AS tariff_participant_fee_discount,
       t."baseFee"                                             AS tariff_basic_fee,
       t.discount                                              AS tariff_discount,
       t."centPerKWh"                                          AS tariff_working_fee_per_consumedkwh,
       t."centPerKWh"                                          AS tariff_credit_amount_per_producedkwh,
       t."freeKWh"                                             AS tariff_freekwh,
       t.version                                               AS tariff_version,
       t.id                                                    AS tariff_id,
       COALESCE(b."bankName", ''::text)                        AS participant_bank_name,
       b.iban                                                  AS participant_bank_iban,
       b.owner                                                 AS participant_bank_owner,
       o.email                                                 AS participant_email,
       COALESCE(c."bankName", ''::text)                        AS eec_bank_name,
       c.iban                                                  AS eec_bank_iban,
       c.owner                                                 AS eec_bank_owner,
       c.creditor_id                                           AS eec_bank_creditor_id
FROM base.participant p
         LEFT JOIN base.eeg c ON c.tenant::text = p.tenant::text
         LEFT JOIN base.meteringpoint pm ON pm.participant_id = p.id
         LEFT JOIN base.address p_address ON p.id = p_address.participant_id AND p_address.type = 'BILLING'::text
         LEFT JOIN base.activetariff t ON t.id = pm.tariff_id
         LEFT JOIN base.activetariff tp ON tp.id = p."tariffId" AND tp.type::text = 'EEG'::text
         LEFT JOIN base.bankaccount b ON b.participant_id = p.id
         LEFT JOIN base.contactdetail o ON o.participant_id = p.id