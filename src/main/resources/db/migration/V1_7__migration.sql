alter table if exists billing_document add column recipient_tax_id varchar default false;
alter table if exists billing_document add column recipient_vat_id varchar default false;