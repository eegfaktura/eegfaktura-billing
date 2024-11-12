-- Damit wir nicht das ganze Backend in der DB hochziehen müssen erstellen wir nur die
-- Tabelle billing_masterdata in einem "base" Schema. Production handelt es sich bei
-- billing_masterdata um eine View
create schema if not exists base;

create table if not exists base.billing_masterdata
(
    participant_id                        varchar,
    participant_title_before              varchar,
    participant_firstname                 varchar,
    participant_lastname                  varchar,
    participant_title_after               varchar,
    participant_vat_id                    varchar,
    participant_tax_id                    varchar,
    participant_company_register_number   varchar,
    participant_email                     varchar,
    participant_number                    varchar,
    participant_bank_name                 varchar,
    participant_bank_iban                 varchar,
    participant_bank_owner                varchar,
    participant_sepa_mandate_reference    varchar,
    participant_sepa_mandate_issue_date   varchar,
    metering_point_id                     varchar,
    equipment_number                     varchar,
    metering_equipment_name               varchar,
    metering_point_type                   varchar,
    tenant_id                             varchar,
    eec_id                                varchar,
    eec_name                              varchar,
    eec_vat_id                            varchar,
    eec_tax_id                            varchar,
    eec_company_register_number           varchar,
    eec_email                             varchar,
    eec_phone                             varchar,
    eec_subject_to_vat                    boolean,
    eec_street                            varchar,
    eec_zip_code                          varchar,
    eec_city                              varchar,
    eec_bank_name                         varchar,
    eec_bank_iban                         varchar,
    eec_bank_owner                        varchar,
    participant_street                    varchar,
    participant_zip_code                  varchar,
    participant_city                      varchar,
    tariff_type                           varchar,
    tariff_name                           varchar,
    tariff_text                           varchar,
    tariff_billing_period                 varchar,
    tariff_use_vat                        boolean,
    tariff_vat_in_percent                 numeric,
    tariff_participant_fee                numeric,
    tariff_participant_fee_name           varchar,
    tariff_participant_fee_text           varchar,
    tariff_participant_fee_use_vat        boolean,
    tariff_participant_fee_vat_in_percent numeric,
    tariff_participant_fee_discount       numeric,
    tariff_basic_fee                      numeric,
    tariff_discount                       numeric,
    tariff_working_fee_per_consumedkwh    numeric,
    tariff_credit_amount_per_producedkwh  numeric,
    tariff_freekwh                        numeric,
    tariff_metering_point_fee             numeric,
    tariff_metering_point_fee_text        varchar,
    tariff_metering_point_vat             numeric,
    tariff_use_metering_point_fee         boolean default false
);


-- mit folgenden Befehlen können (generierte) Massdaten in die DB eingespielt werden (zB mittels PSQL):

\COPY base.billing_masterdata (
    tenant_id,eec_name,eec_id,eec_bank_iban,eec_bank_name,eec_bank_owner,eec_city,
    eec_zip_code,eec_street,eec_phone,eec_company_register_number,eec_tax_id,
    eec_vat_id,eec_subject_to_vat,tariff_type,tariff_name,tariff_text,
    tariff_billing_period,tariff_use_vat,tariff_vat_in_percent,tariff_participant_fee,
    tariff_participant_fee_name,tariff_participant_fee_text,tariff_participant_fee_use_vat,
    tariff_participant_fee_vat_in_percent,tariff_participant_fee_discount,
    tariff_basic_fee,tariff_discount,tariff_working_fee_per_consumedkwh,
    tariff_credit_amount_per_producedkwh,tariff_freekwh,tariff_use_metering_point_fee,
    tariff_metering_point_fee,tariff_metering_point_fee_text,participant_id,
    participant_title_before,participant_firstname,participant_lastname,
    participant_title_after,participant_company_register_number,participant_tax_id,
    participant_vat_id,participant_email,participant_street,participant_zip_code,
    participant_city,participant_number,participant_bank_name,participant_bank_iban,
    participant_bank_owner,participant_sepa_mandate_reference,
    participant_sepa_mandate_issue_date,metering_point_type,metering_point_id,
    equipment_number,metering_equipment_name
)
    FROM '/billing_masterdata.csv'
    DELIMITER ','
    CSV HEADER
    QUOTE '"'
    NULL 'null'

\COPY billingj.billing_config (
    id,tenant_id,is_create_credit_notes_for_all_producers,before_items_text_invoice,
    before_items_text_credit_note,before_items_text_info,after_items_text_invoice,
    after_items_text_credit_note,after_items_text_info,terms_text_invoice,
    terms_text_credit_note,terms_text_info,footer_text,document_number_sequence_length,
    invoice_number_prefix,invoice_number_start,
    credit_note_number_prefix,credit_note_number_start,
    date_created, last_updated
)
FROM '/billing_config.csv'
    DELIMITER ','
    CSV HEADER
    QUOTE '"'
    NULL 'null'

\COPY billingj.billing_run (
    id,clearing_period_type,clearing_period_identifier,tenant_id,run_status,
    run_status_date_time,mail_status,mail_status_date_time,sepa_status,sepa_status_date_time,
    number_of_invoices,number_of_credit_notes,date_created,last_updated
)
FROM '/billing_run.csv'
    DELIMITER ','
    CSV HEADER
    QUOTE '"'
    NULL 'null'

\COPY billingj.billing_document (
    id,document_number,document_date,status,clearing_period_type,before_items_text,
    after_items_text,terms_text,footer_text,tenant_id,participant_id,recipient_name,
    recipient_participant_number,recipient_address_line1,recipient_address_line2,recipient_address_line3,
    recipient_bank_name,recipient_bank_iban,recipient_bank_owner,recipient_sepa_mandate_reference,
    recipient_sepa_mandate_issue_date,recipient_email,recipient_tax_id,recipient_vat_id,issuer_name,
    issuer_address_line1,issuer_address_line2,issuer_address_line3,issuer_phone,issuer_mail,
    issuer_website,issuer_tax_id,issuer_vat_id,issuer_company_register_number,issuer_bank_name,
    issuer_bankiban,issuer_bank_owner,vat1percent,vat1sum_in_euro,vat2percent,vat2sum_in_euro,
    net_amount_in_euro,gross_amount_in_euro,clearing_period_identifier,billing_document_type,
    date_created,last_updated
)
FROM '/billing_document.csv'
    DELIMITER ','
    CSV HEADER
    QUOTE '"'
    NULL 'null'

\COPY billingj.billing_document_item (
    id,tenant_id,clearing_period_type,clearing_period_identifier,amount,metering_point_id,
    metering_point_type,text,document_text,unit,price_per_unit,ppu_unit,net_value,discount_percent,
    vat_percent,vat_value_in_euro,gross_value,date_created,last_updated
)
FROM '/billing_document_item.csv'
    DELIMITER ','
    CSV HEADER
    QUOTE '"'
    NULL 'null'