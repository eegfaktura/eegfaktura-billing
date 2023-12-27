alter table if exists billing_document add column recipient_participant_number varchar;
alter table if exists billing_document add column footer_text varchar;
alter table if exists file rename to billing_document_file;
alter table if exists billing_document_number rename column eec_prefix to prefix;
alter table if exists billing_document_item add column metering_point_id varchar;
alter table if exists billing_document_item add column metering_point_type smallint;

create table billing_config (id UUID not null, tenant_id varchar not null, header_image_file_data_id UUID, footer_image_file_data_id UUID, before_items_text_invoice varchar, before_items_text_credit_note varchar, before_items_text_info varchar, after_items_text_invoice varchar, after_items_text_credit_note varchar, after_items_text_info varchar, terms_text_invoice varchar, terms_text_credit_note varchar, terms_text_info varchar, footer_text varchar, custom_template_file_data_id UUID, document_number_sequence_length integer, invoice_number_prefix varchar, invoice_number_start bigint, credit_note_number_prefix varchar, credit_note_number_start bigint,  date_created timestamp(6) not null, last_updated timestamp(6) not null, primary key (id));
alter table if exists billing_config add constraint FK01H3XWF8N8BPER0YJ2P3EP0KX8 foreign key (header_image_file_data_id) references file_data(id);
alter table if exists billing_config add constraint FK01H3XWJA21V3GWXAKVB9C34T7D foreign key (footer_image_file_data_id) references file_data(id);
alter table if exists billing_config add constraint FK01H3XWKC7THK17BQE07R9JXKWH foreign key (custom_template_file_data_id) references file_data(id);
alter table if exists billing_config add constraint UK01H3XWRJW9XSMPTE9Y7AGQNKG6 unique (tenant_id);