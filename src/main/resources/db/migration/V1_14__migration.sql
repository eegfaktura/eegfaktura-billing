alter table if exists billing_document add column recipient_sepa_direct_debit varchar;
alter table if exists billing_document add column issuer_bank_creditor_id varchar;
alter table if exists billing_document_item add column tariff_id varchar;
alter table if exists billing_document_item add column tariff_version integer;