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

insert into base.billing_masterdata (
    participant_id,
    participant_title_before,
    participant_firstname,
    participant_lastname,
    participant_title_after,
    participant_vat_id,
    participant_tax_id,
    participant_company_register_number,
    participant_email,
    participant_number,
    participant_bank_name,
    participant_bank_iban,
    participant_bank_owner,
    participant_sepa_mandate_reference,
    participant_sepa_mandate_issue_date,
    metering_point_id,
    equipment_number,
    metering_equipment_name,
    metering_point_type,
    tenant_id,
    eec_id ,
    eec_name,
    eec_vat_id,
    eec_tax_id,
    eec_company_register_number,
    eec_email,
    eec_phone,
    eec_subject_to_vat,
    eec_street,
    eec_zip_code,
    eec_city,
    eec_bank_name,
    eec_bank_iban,
    eec_bank_owner,
    participant_street,
    participant_zip_code,
    participant_city,
    tariff_type,
    tariff_name,
    tariff_text,
    tariff_billing_period,
    tariff_use_vat,
    tariff_vat_in_percent,
    tariff_participant_fee,
    tariff_participant_fee_name,
    tariff_participant_fee_text,
    tariff_participant_fee_use_vat,
    tariff_participant_fee_vat_in_percent,
    tariff_participant_fee_discount,
    tariff_basic_fee,
    tariff_discount,
    tariff_working_fee_per_consumedkwh,
    tariff_credit_amount_per_producedkwh,
    tariff_metering_point_vat,
    tariff_freekwh                        
) values (
    '8126ab63-3f5d-42a4-b6f5-8df17aa68158', --participant_id
    'Mag.', -- participant_title_before
    'Felix', --participant_firstname
    'Glück', --participant_lastname
    'Msc', --participant_title_after
    'UST12345', --participant_vat_id
    'STR12345', --participant_tax_id
    'FN12312A', --participant_company_register_number
    'harald.lacherstorfer@gmail.com', --participant_email
    '', --participant_number
    'Raiffeisen Landesbank', --participant_bank_name
    'AT01-1234-1234-1234', --participant_bank_iban
    'Felix Glück', --participant_bank_owner
    'REF1234', --participant_sepa_mandate_reference
    '2022-01-01', --participant_sepa_mandate_issue_date
    'C0000000000000000000001234', --metering_point_id
    'Anlagenr 1234', --equipment_number
    'Anlage Foo-Bar', --metering_equipment_name
    '1', --metering_point_type
    'TE100100', --tenant_id
    'TE100100', -- eec_id 
    'Energiegemeinschaft Holy Grail', --eec_name
    'UST4321', --eec_vat_id
    'STR4321', --eec_tax_id
    'FN4321A', --eec_company_register_number
    'eeg-holy-grail@gmx.at', --eec_email
    '+43 555 123456', --eec_phone
    false, --eec_subject_to_vat,
    'Feldweg 12', -- eec_street
    '1234', -- eec_zip_code
    'Fuxholzen', -- eec_city
    'Sparkasse OÖ', --eec_bank_name
    'AT01-4321-4321-4321', -- eec_bank_iban
    'Energiegemeinschaft Holy Grail', --eec_bank_owner
    'Glücksweg 13', -- participant_street
    '1234', -- participant_zip_code
    'Fuxholzen', --participant_city
    'Verbraucher', --tariff_type
    'Standard', --tariff_name
    'Text zu Tarif Standard', --tariff_text
    'Q', -- tariff_billing_period
    false, -- tariff_use_vat,
    0.0, -- tariff_vat_in_percent,
    10.0, -- tariff_participant_fee,
    'Mitgliedsgebühr', --tariff_participant_fee_name
    'Text zu Tarif Mitgliedsgebühr', --tariff_participant_fee_text
    false, -- tariff_participant_fee_use_vat,
    0.0, -- tariff_participant_fee_vat_in_percent,
    0.0, -- tariff_participant_fee_discount,
    0.0, -- tariff_basic_fee,
    0.0, -- tariff_discount,
    15, -- tariff_working_fee_per_consumedkwh,
    19, -- tariff_credit_amount_per_producedkwh,
    0, -- tariff_metering_point_vat
    0 -- tariff_freekwh
);

insert into base.billing_masterdata (
    participant_id,
    participant_title_before,
    participant_firstname,
    participant_lastname,
    participant_title_after,
    participant_vat_id,
    participant_tax_id,
    participant_company_register_number,
    participant_email,
    participant_number,
    participant_bank_name,
    participant_bank_iban,
    participant_bank_owner,
    participant_sepa_mandate_reference,
    participant_sepa_mandate_issue_date,
    metering_point_id,
    equipment_number,
    metering_equipment_name,
    metering_point_type,
    tenant_id,
    eec_id ,
    eec_name,
    eec_vat_id,
    eec_tax_id,
    eec_company_register_number,
    eec_email,
    eec_phone,
    eec_subject_to_vat,
    eec_street,
    eec_zip_code,
    eec_city,
    eec_bank_name,
    eec_bank_iban,
    eec_bank_owner,
    participant_street,
    participant_zip_code,
    participant_city,
    tariff_type,
    tariff_name,
    tariff_text,
    tariff_billing_period,
    tariff_use_vat,
    tariff_vat_in_percent,
    tariff_participant_fee,
    tariff_participant_fee_name,
    tariff_participant_fee_text,
    tariff_participant_fee_use_vat,
    tariff_participant_fee_vat_in_percent,
    tariff_participant_fee_discount,
    tariff_basic_fee,
    tariff_discount,
    tariff_working_fee_per_consumedkwh,
    tariff_credit_amount_per_producedkwh,
    tariff_metering_point_vat,
    tariff_freekwh
) values (
             '8126ab63-3f5d-42a4-b6f5-8df17aa68158', --participant_id
             'Mag.', -- participant_title_before
             'Felix', --participant_firstname
             'Glück', --participant_lastname
             'Msc', --participant_title_after
             'UST12345', --participant_vat_id
             'STR12345', --participant_tax_id
             'FN12312A', --participant_company_register_number
             'harald.lacherstorfer@gmail.com', --participant_email
             '', --participant_number
             'Raiffeisen Landesbank', --participant_bank_name
             'AT01-1234-1234-1234', --participant_bank_iban
             'Felix Glück', --participant_bank_owner
             'REF1234', --participant_sepa_mandate_reference
             '2022-01-01', --participant_sepa_mandate_issue_date
             'C0000000000000000000002234', --metering_point_id
             'Anlagenr 2234', --equipment_number
             'Anlage Fix-Foxi', --metering_equipment_name
             '1', --metering_point_type
             'TE100100', --tenant_id
             'TE100100', -- eec_id
             'Energiegemeinschaft Holy Grail', --eec_name
             'UST4321', --eec_vat_id
             'STR4321', --eec_tax_id
             'FN4321A', --eec_company_register_number
             'eeg-holy-grail@gmx.at', --eec_email
             '+43 555 123456', --eec_phone
             false, --eec_subject_to_vat,
             'Feldweg 12', -- eec_street
             '1234', -- eec_zip_code
             'Fuxholzen', -- eec_city
             'Sparkasse OÖ', --eec_bank_name
             'AT01-4321-4321-4321', -- eec_bank_iban
             'Energiegemeinschaft Holy Grail', --eec_bank_owner
             'Meisenweg 15', -- participant_street
             '1234', -- participant_zip_code
             'Fuxholzen', --participant_city
             'Verbraucher', --tariff_type
             'Standard', --tariff_name
             'Text zu Tarif Standard', --tariff_text
             'Q', -- tariff_billing_period
             false, -- tariff_use_vat,
             0.0, -- tariff_vat_in_percent,
             10.0, -- tariff_participant_fee,
             'Mitgliedsgebühr', --tariff_participant_fee_name
             'Text zu Tarif Mitgliedsgebühr', --tariff_text
             false, -- tariff_participant_fee_use_vat,
             0.0, -- tariff_participant_fee_vat_in_percent,
             0.0, -- tariff_participant_fee_discount,
             0.0, -- tariff_basic_fee,
             0.0, -- tariff_discount,
             15, -- tariff_working_fee_per_consumedkwh,
             19, -- tariff_credit_amount_per_producedkwh,
             12.5, -- tariff_metering_point_vat
             0 -- tariff_freekwh

         );
insert into base.billing_masterdata (
    participant_id,
    participant_title_before,
    participant_firstname,
    participant_lastname,
    participant_title_after,
    participant_vat_id,
    participant_tax_id,
    participant_company_register_number,
    participant_email,
    participant_number,
    participant_bank_name,
    participant_bank_iban,
    participant_bank_owner,
    participant_sepa_mandate_reference,
    participant_sepa_mandate_issue_date,
    metering_point_id,
    equipment_number,
    metering_equipment_name,
    metering_point_type,
    tenant_id,
    eec_id ,
    eec_name,
    eec_vat_id,
    eec_tax_id,
    eec_company_register_number,
    eec_email,
    eec_phone,
    eec_subject_to_vat,
    eec_street,
    eec_zip_code,
    eec_city,
    eec_bank_name,
    eec_bank_iban,
    eec_bank_owner,
    participant_street,
    participant_zip_code,
    participant_city,
    tariff_type,
    tariff_name,
    tariff_billing_period,
    tariff_use_vat,
    tariff_vat_in_percent,
    tariff_participant_fee,
    tariff_participant_fee_name,
    tariff_participant_fee_use_vat,
    tariff_participant_fee_vat_in_percent,
    tariff_participant_fee_discount,
    tariff_basic_fee,
    tariff_discount,
    tariff_working_fee_per_consumedkwh,
    tariff_credit_amount_per_producedkwh,
    tariff_metering_point_vat,
    tariff_freekwh
) values (
             '039e8d60-b6ba-459c-b5a1-0c31aa53a49', --participant_id
             null, -- participant_title_before
             'Fridolin', --participant_firstname
             'Fröhlich', --participant_lastname
             'MBA', --participant_title_after
             null, --participant_vat_id
             null, --participant_tax_id
             null, --participant_company_register_number
             'harald.lacherstorfer@gmail.com', --participant_email
             '', --participant_number
             'Postsparkasse', --participant_bank_name
             'AT01-2222-2222-2222', --participant_bank_iban
             'Fridolin Fröhlich', --participant_bank_owner
             'REF2222', --participant_sepa_mandate_reference
             '2022-12-31', --participant_sepa_mandate_issue_date
             'P0000000000000000000002222', --metering_point_id
             'Anlagenr 2222', --equipment_number
             'PV Oberweg', --metering_equipment_name
             '0', --metering_point_type
             'TE100100', --tenant_id
             'TE100100', -- eec_id
             'Energiegemeinschaft Holy Grail', --eec_name
             'UST4321', --eec_vat_id
             'STR4321', --eec_tax_id
             'FN4321A', --eec_company_register_number
             'eeg-holy-grail@gmx.at', --eec_email
             '+43 555 123456', --eec_phone
             false, --eec_subject_to_vat,
             'Feldweg 12', -- eec_street
             '1234', -- eec_zip_code
             'Fuxholzen', -- eec_city
             'Sparkasse OÖ', --eec_bank_name
             'AT01-4321-4321-4321', -- eec_bank_iban
             'Energiegemeinschaft Holy Grail', --eec_bank_owner
             'Fröhlichweg 15', -- participant_street
             '1234', -- participant_zip_code
             'Fuxholzen', --participant_city
             'Erzeuger', --tariff_type
             'Standard', --tariff_name
             'Q', -- tariff_billing_period
             false, -- tariff_use_vat,
             0.0, -- tariff_vat_in_percent,
             10.0, -- tariff_participant_fee,
             'Mitgliedsgebühr', --tariff_participant_fee_name
             false, -- tariff_participant_fee_use_vat,
             0.0, -- tariff_participant_fee_vat_in_percent,
             0.0, -- tariff_participant_fee_discount,
             0.0, -- tariff_basic_fee,
             0.0, -- tariff_discount,
             15, -- tariff_working_fee_per_consumedkwh,
             19, -- tariff_credit_amount_per_producedkwh,
             0, -- tariff_metering_point_vat
             0 -- tariff_freekwh

         );
insert into base.billing_masterdata (
    participant_id,
    participant_title_before,
    participant_firstname,
    participant_lastname,
    participant_title_after,
    participant_vat_id,
    participant_tax_id,
    participant_company_register_number,
    participant_email,
    participant_number,
    participant_bank_name,
    participant_bank_iban,
    participant_bank_owner,
    participant_sepa_mandate_reference,
    participant_sepa_mandate_issue_date,
    metering_point_id,
    equipment_number,
    metering_equipment_name,
    metering_point_type,
    tenant_id,
    eec_id ,
    eec_name,
    eec_vat_id,
    eec_tax_id,
    eec_company_register_number,
    eec_email,
    eec_phone,
    eec_subject_to_vat,
    eec_street,
    eec_zip_code,
    eec_city,
    eec_bank_name,
    eec_bank_iban,
    eec_bank_owner,
    participant_street,
    participant_zip_code,
    participant_city,
    tariff_type,
    tariff_name,
    tariff_billing_period,
    tariff_use_vat,
    tariff_vat_in_percent,
    tariff_participant_fee,
    tariff_participant_fee_name,
    tariff_participant_fee_use_vat,
    tariff_participant_fee_vat_in_percent,
    tariff_participant_fee_discount,
    tariff_basic_fee,
    tariff_discount,
    tariff_working_fee_per_consumedkwh,
    tariff_credit_amount_per_producedkwh,
    tariff_metering_point_vat,
    tariff_freekwh
) values (
             'bf6c5e6c-a7f2-4499-b2bb-02bb6587b951', --participant_id
             null, -- participant_title_before
             null, --participant_firstname
             'Sonne GmbH', --participant_lastname
             null, --participant_title_after
             'UST3333', --participant_vat_id
             'STR3333', --participant_tax_id
             'FN3333A', --participant_company_register_number
             'harald.lacherstorfer@gmail.com', --participant_email
             '', --participant_number
             'Postsparkasse', --participant_bank_name
             'AT01-3333-3333-333', --participant_bank_iban
             'Sonne GmbH', --participant_bank_owner
             'REF3333', --participant_sepa_mandate_reference
             '2023-04-01', --participant_sepa_mandate_issue_date
             'P0000000000000000000003333', --metering_point_id
             'Anlagenr 3333', --equipment_number
             'PV Sonne GmbH', --metering_equipment_name
             '0', --metering_point_type
             'TE100100', --tenant_id
             'TE100100', -- eec_id
             'Energiegemeinschaft Holy Grail', --eec_name
             'UST4321', --eec_vat_id
             'STR4321', --eec_tax_id
             'FN4321A', --eec_company_register_number
             'eeg-holy-grail@gmx.at', --eec_email
             '+43 555 123456', --eec_phone
             false, --eec_subject_to_vat,
             'Feldweg 12', -- eec_street
             '1234', -- eec_zip_code
             'Fuxholzen', -- eec_city
             'Sparkasse OÖ', --eec_bank_name
             'AT01-4321-4321-4321', -- eec_bank_iban
             'Energiegemeinschaft Holy Grail', --eec_bank_owner
             'Sonnenweg 42', -- participant_street
             '1234', -- participant_zip_code
             'Fuxholzen', --participant_city
             'Erzeuger', --tariff_type
             'Standard', --tariff_name
             'Q', -- tariff_billing_period
             true, -- tariff_use_vat,
             10.0, -- tariff_vat_in_percent,
             10.0, -- tariff_participant_fee,
             'Mitgliedsgebühr', --tariff_participant_fee_name
             false, -- tariff_participant_fee_use_vat,
             0.0, -- tariff_participant_fee_vat_in_percent,
             0.0, -- tariff_participant_fee_discount,
             0.0, -- tariff_basic_fee,
             0.0, -- tariff_discount,
             15, -- tariff_working_fee_per_consumedkwh,
             19, -- tariff_credit_amount_per_producedkwh,
             0, -- tariff_metering_point_vat
             0 -- tariff_freekwh
         );
insert into base.billing_masterdata (
    participant_id,
    participant_title_before,
    participant_firstname,
    participant_lastname,
    participant_title_after,
    participant_vat_id,
    participant_tax_id,
    participant_company_register_number,
    participant_email,
    participant_number,
    participant_bank_name,
    participant_bank_iban,
    participant_bank_owner,
    participant_sepa_mandate_reference,
    participant_sepa_mandate_issue_date,
    metering_point_id,
    equipment_number,
    metering_equipment_name,
    metering_point_type,
    tenant_id,
    eec_id ,
    eec_name,
    eec_vat_id,
    eec_tax_id,
    eec_company_register_number,
    eec_email,
    eec_phone,
    eec_subject_to_vat,
    eec_street,
    eec_zip_code,
    eec_city,
    eec_bank_name,
    eec_bank_iban,
    eec_bank_owner,
    participant_street,
    participant_zip_code,
    participant_city,
    tariff_type,
    tariff_name,
    tariff_billing_period,
    tariff_use_vat,
    tariff_vat_in_percent,
    tariff_participant_fee,
    tariff_participant_fee_name,
    tariff_participant_fee_use_vat,
    tariff_participant_fee_vat_in_percent,
    tariff_participant_fee_discount,
    tariff_basic_fee,
    tariff_discount,
    tariff_working_fee_per_consumedkwh,
    tariff_credit_amount_per_producedkwh,
    tariff_metering_point_fee,
    tariff_metering_point_fee_text,
    tariff_metering_point_vat,
    tariff_use_metering_point_fee,
    tariff_freekwh
) values (
             'bf6c5e6c-a7f2-4499-b2bb-02bb6587b951', --participant_id
             null, -- participant_title_before
             null, --participant_firstname
             'Sonne GmbH', --participant_lastname
             null, --participant_title_after
             'UST3333', --participant_vat_id
             'STR3333', --participant_tax_id
             'FN3333A', --participant_company_register_number
             'harald.lacherstorfer@gmail.com', --participant_email
             '', --participant_number
             'Postsparkasse', --participant_bank_name
             'AT01-3333-3333-333', --participant_bank_iban
             'Sonne GmbH', --participant_bank_owner
             'REF3333', --participant_sepa_mandate_reference
             '2023-04-01', --participant_sepa_mandate_issue_date
             'P0000000000000000000004444', --metering_point_id
             'Anlagenr 4444', --equipment_number
             'PV Sonne GmbH', --metering_equipment_name
             '0', --metering_point_type
             'TE100100', --tenant_id
             'TE100100', -- eec_id
             'Energiegemeinschaft Holy Grail', --eec_name
             'UST4321', --eec_vat_id
             'STR4321', --eec_tax_id
             'FN4321A', --eec_company_register_number
             'eeg-holy-grail@gmx.at', --eec_email
             '+43 555 123456', --eec_phone
             true, --eec_subject_to_vat,
             'Feldweg 12', -- eec_street
             '1234', -- eec_zip_code
             'Fuxholzen', -- eec_city
             'Sparkasse OÖ', --eec_bank_name
             'AT01-4321-4321-4321', -- eec_bank_iban
             'Energiegemeinschaft Holy Grail', --eec_bank_owner
             'Sonnenweg 42', -- participant_street
             '1234', -- participant_zip_code
             'Fuxholzen', --participant_city
             'Erzeuger mit ZP Gebühr', --tariff_type
             'Standard', --tariff_name
             'Q', -- tariff_billing_period
             true, -- tariff_use_vat,
             10.0, -- tariff_vat_in_percent,
             10.0, -- tariff_participant_fee,
             'Mitgliedsgebühr', --tariff_participant_fee_name
             true, -- tariff_participant_fee_use_vat,
             20.0, -- tariff_participant_fee_vat_in_percent,
             0.0, -- tariff_participant_fee_discount,
             0.0, -- tariff_basic_fee,
             0.0, -- tariff_discount,
             15, -- tariff_working_fee_per_consumedkwh,
             19, -- tariff_credit_amount_per_producedkwh,
             2.5, -- tariff_metering_point_fee,
             'Zählpunktgebühr', -- tariff_metering_point_fee_text
             20.0, --tariff_metering_point_vat
             true, -- tariff_use_metering_point_fee
             0 -- tariff_freekwh
         );