-- V190 (m07): the legacy billing contract for CUTOVER communities, and the cutover tenant list (docs/poc/m07-cutover.md §6).
-- legacy_uuid(kind, id) exists since m04's V160__legacy_uuid.sql — do not create it again.

CREATE VIEW cutover_tenant WITH (security_barrier) AS
  SELECT c.eeg_id, c.tenant FROM v3.cutover c WHERE c.state = 'CUTOVER';

CREATE VIEW billing_masterdata_v3 WITH (security_barrier) AS
SELECT coalesce(p.legacy_id, v3.legacy_uuid('participant', p.id))        AS participant_id,
       p.title_before                                                   AS participant_title_before,
       p.first_name                                                     AS participant_firstname,
       p.participant_number                                             AS participant_number,
       p.last_name                                                      AS participant_lastname,
       p.title_after                                                    AS participant_title_after,
       p.vat_number                                                     AS participant_vat_id,
       p.tax_number                                                     AS participant_tax_id,
       p.company_register_number                                        AS participant_company_register_number,
       coalesce(b.mandate_reference, p.participant_number)              AS participant_sepa_mandate_reference,
       coalesce(b.mandate_date, p.participant_since)                    AS participant_sepa_mandate_issue_date,
       b.sepa_direct_debit                                              AS participant_sepa_direct_debit,
       m.metering_point_id                                              AS metering_point_id,
       m.equipment_number                                               AS equipment_number,
       m.equipment_name                                                 AS metering_equipment_name,
       CASE WHEN m.direction = 'GENERATION' THEN 0 ELSE 1 END           AS metering_point_type,
       ct.tenant                                                        AS tenant_id,
       e.code                                                           AS eec_id,
       md.description                                                   AS eec_name,
       md.vat_number                                                    AS eec_vat_id,
       md.tax_number                                                    AS eec_tax_id,
       md.business_number                                               AS eec_company_register_number,
       md.subject_to_vat                                                AS eec_subject_to_vat,
       md.phone                                                         AS eec_phone,
       md.email                                                         AS eec_email,
       md.website                                                       AS eec_website,
       concat(md.street, ' ', md.street_number)                         AS eec_street,
       md.zip                                                           AS eec_zip_code,
       md.city                                                          AS eec_city,
       concat(a.street, ' ', a.street_number)                           AS participant_street,
       a.zip                                                            AS participant_zip_code,
       a.city                                                           AS participant_city,
       t.type                                                           AS tariff_type,
       t.name                                                           AS tariff_name,
       CASE WHEN t.id IS NOT NULL THEN lower(md.settlement_interval) END AS tariff_billing_period,
       tv.use_vat                                                       AS tariff_use_vat,
       tv.vat_supplementary_text                                        AS tariff_text,
       tv.vat_in_percent                                                AS tariff_vat_in_percent,
       tv.use_metering_point_fee                                        AS tariff_use_metering_point_fee,
       tv.metering_point_fee                                            AS tariff_metering_point_fee,
       tv.metering_point_vat                                            AS tariff_metering_point_vat,
       ''::text                                                         AS tariff_metering_point_fee_text,
       ''::text                                                         AS tariff_participant_fee_text,
       coalesce(tpv.version, 0)                                         AS tariff_participant_version,
       coalesce(tpv.participant_fee::double precision, 0)               AS tariff_participant_fee,
       coalesce(tp.name, '')                                            AS tariff_participant_fee_name,
       coalesce(tpv.use_vat, false)                                     AS tariff_participant_fee_use_vat,
       coalesce(tpv.vat_in_percent, 0)                                  AS tariff_participant_fee_vat_in_percent,
       coalesce(tpv.discount, 0)                                        AS tariff_participant_fee_discount,
       tv.base_fee                                                      AS tariff_basic_fee,
       tv.discount                                                      AS tariff_discount,
       tv.cent_per_kwh                                                  AS tariff_working_fee_per_consumedkwh,
       tv.cent_per_kwh                                                  AS tariff_credit_amount_per_producedkwh,
       tv.free_kwh                                                      AS tariff_freekwh,
       tv.version                                                       AS tariff_version,
       CASE WHEN t.id IS NOT NULL THEN coalesce(t.legacy_id, v3.legacy_uuid('tariff', t.id)) END AS tariff_id,
       coalesce(b.bank_name, '')                                        AS participant_bank_name,
       b.iban                                                           AS participant_bank_iban,
       b.account_owner                                                  AS participant_bank_owner,
       p.email                                                          AS participant_email,
       coalesce(md.bank_name, '')                                       AS eec_bank_name,
       md.iban                                                          AS eec_bank_iban,
       md.account_owner                                                 AS eec_bank_owner,
       md.creditor_id                                                   AS eec_bank_creditor_id,
       p.eeg_id                                                         AS eeg_id
  FROM v3.participant p
  JOIN v3.cutover_tenant ct ON ct.eeg_id = p.eeg_id
  JOIN v3.eeg e ON e.id = p.eeg_id
  LEFT JOIN v3.eeg_masterdata md ON md.eeg_id = p.eeg_id
  LEFT JOIN v3.participant_bank_account b ON b.eeg_id = p.eeg_id AND b.participant_id = p.id
  LEFT JOIN v3.participant_address a ON a.eeg_id = p.eeg_id AND a.participant_id = p.id AND a.type = 'BILLING'
  LEFT JOIN v3.metering_point m ON m.eeg_id = p.eeg_id AND m.participant_id = p.id
  LEFT JOIN v3.tariff t ON t.eeg_id = m.eeg_id AND t.id = m.tariff_id AND t.status <> 'ARCHIVED'
  LEFT JOIN LATERAL (SELECT v.* FROM v3.tariff_version v
                      WHERE v.eeg_id = t.eeg_id AND v.tariff_id = t.id
                      ORDER BY v.version DESC LIMIT 1) tv ON t.id IS NOT NULL
  LEFT JOIN v3.tariff tp ON tp.eeg_id = p.eeg_id AND tp.id = p.tariff_id AND tp.type = 'EEG' AND tp.status <> 'ARCHIVED'
  LEFT JOIN LATERAL (SELECT v.* FROM v3.tariff_version v
                      WHERE v.eeg_id = tp.eeg_id AND v.tariff_id = tp.id
                      ORDER BY v.version DESC LIMIT 1) tpv ON tp.id IS NOT NULL;
COMMENT ON VIEW billing_masterdata_v3 IS
  'The legacy billing contract (base.billing_masterdata, 64 columns) for CUTOVER communities; owner exempt from RLS; read by the legacy role through the UNION (m07)';
