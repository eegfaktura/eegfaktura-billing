copy base.billing_masterdata (tenant_id,eec_name,eec_id,eec_bank_iban,eec_bank_name,eec_bank_owner,eec_city,
                              eec_zip_code,eec_street,eec_phone,eec_company_register_number,eec_tax_id,
                              eec_vat_id,eec_subject_to_vat,tariff_type,tariff_name,tariff_text,
                              tariff_billing_period,tariff_use_vat,tariff_vat_in_percent,tariff_participant_fee,
                              tariff_participant_fee_name,tariff_participant_fee_text,tariff_participant_fee_use_vat,
                              tariff_participant_fee_vat_in_percent,tariff_participant_fee_discount,
                              tariff_basic_fee,tariff_discount,tariff_working_fee_per_consumed_kwh,
                              tariff_credit_amount_per_produced_kwh,tariff_free_kwh,tariff_use_metering_point_fee,
                              tariff_metering_point_fee,tariff_metering_point_fee_text,participant_id,
                              participant_title_before,participant_first_name,participant_last_name,
                              participant_title_after,participant_company_register_number,participant_tax_id,
                              participant_vat_id,participant_email,participant_street,participant_zip_code,
                              participant_city,participant_number,participant_bank_name,participant_bank_iban,
                              participant_bank_owner,participant_sepa_mandate_reference,
                              participant_sepa_mandate_issue_date,metering_point_type,metering_point_id,
                              equipment_number,metering_equipment_name)
    FROM '/home/hla/IdeaProjects/eegfaktura-billing/billing_masterdata.csv'
    DELIMITER ','
    CSV QUOTE '\"'
    CSV HEADER
    NULL 'null'