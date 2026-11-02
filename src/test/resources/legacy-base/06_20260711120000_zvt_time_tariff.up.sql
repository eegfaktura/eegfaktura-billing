-- ZVT (zeitvariabler Tarif): Basispreis (= bestehendes "centPerKWh") + bis zu
-- zwei benannte Zeitfenster je Tarif (15-min-Raster, From > To = Mitternachts-
-- ueberlauf). Bestand bleibt unveraendert ("useTimeTariff" = false).
ALTER TABLE base.tariff
    ADD COLUMN IF NOT EXISTS "useTimeTariff"         boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS "timeTariff1Active"     boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS "timeTariff1Name"       character varying NULL,
    ADD COLUMN IF NOT EXISTS "timeTariff1From"       time NULL,
    ADD COLUMN IF NOT EXISTS "timeTariff1To"         time NULL,
    ADD COLUMN IF NOT EXISTS "timeTariff1CentPerKWh" double precision NULL,
    ADD COLUMN IF NOT EXISTS "timeTariff2Active"     boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS "timeTariff2Name"       character varying NULL,
    ADD COLUMN IF NOT EXISTS "timeTariff2From"       time NULL,
    ADD COLUMN IF NOT EXISTS "timeTariff2To"         time NULL,
    ADD COLUMN IF NOT EXISTS "timeTariff2CentPerKWh" double precision NULL;

-- Beide Views neu aufbauen (billing_masterdata joint activeTariff).
DROP VIEW IF EXISTS base.billing_masterdata;
DROP VIEW IF EXISTS base.activeTariff;

CREATE OR REPLACE VIEW base.activeTariff AS
SELECT tariff.id,
       tariff.name,
       tariff.tenant,
       tariff."billingPeriod",
       tariff."useVat",
       tariff."vatInPercent",
       tariff."vatSupplementaryText",
       tariff."accountNetAmount",
       tariff."accountGrossAmount",
       tariff."participantFee",
       tariff."baseFee",
       tariff."businessNr",
       tariff.version,
       tariff.type,
       tariff."centPerKWh",
       tariff.discount,
       tariff."freeKWh",
       tariff."meteringPointFee",
       tariff."meteringPointVat",
       tariff."useMeteringPointFee",
       tariff."useTimeTariff",
       tariff."timeTariff1Active",
       tariff."timeTariff1Name",
       to_char(tariff."timeTariff1From", 'HH24:MI') AS "timeTariff1From",
       to_char(tariff."timeTariff1To", 'HH24:MI')   AS "timeTariff1To",
       tariff."timeTariff1CentPerKWh",
       tariff."timeTariff2Active",
       tariff."timeTariff2Name",
       to_char(tariff."timeTariff2From", 'HH24:MI') AS "timeTariff2From",
       to_char(tariff."timeTariff2To", 'HH24:MI')   AS "timeTariff2To",
       tariff."timeTariff2CentPerKWh"
FROM base.tariff,
     (SELECT tariff_1.id           AS tid,
             max(tariff_1.version) AS tversion
      FROM base.tariff tariff_1
      GROUP BY tariff_1.id) x
WHERE tariff.id = x.tid
  AND tariff.version = x.tversion
  AND tariff.status <> 'ARCHIVED'::text;

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
       COALESCE(b.mandate_reference, p."participantNumber")    AS participant_sepa_mandate_reference,
       COALESCE(b.mandate_date, p."participantSince")          AS participant_sepa_mandate_issue_date,
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
       t."useTimeTariff"                                       AS tariff_use_time_tariff,
       t."timeTariff1Active"                                   AS tariff_time1_active,
       t."timeTariff1Name"                                     AS tariff_time1_name,
       t."timeTariff1From"                                     AS tariff_time1_from,
       t."timeTariff1To"                                       AS tariff_time1_to,
       t."timeTariff1CentPerKWh"                               AS tariff_time1_cent_per_kwh,
       t."timeTariff2Active"                                   AS tariff_time2_active,
       t."timeTariff2Name"                                     AS tariff_time2_name,
       t."timeTariff2From"                                     AS tariff_time2_from,
       t."timeTariff2To"                                       AS tariff_time2_to,
       t."timeTariff2CentPerKWh"                               AS tariff_time2_cent_per_kwh,
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
