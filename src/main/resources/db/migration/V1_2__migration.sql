alter table if exists billing_document add column recipient_bank_name varchar;
alter table if exists billing_document add column recipient_bank_iban varchar;
alter table if exists billing_document add column recipient_bank_owner varchar;
alter table if exists billing_document add column recipient_sepa_mandate_reference varchar;
alter table if exists billing_document add column issuer_bank_owner varchar;
alter table if exists billing_document add column recipient_email varchar;
