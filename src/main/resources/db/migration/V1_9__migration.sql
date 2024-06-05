create index if not exists billing_document_idx1 on billing_document (billing_run_id);
create index if not exists billing_document_item_idx1 on billing_document_item (billing_document_id);
create index if not exists billing_document_file_idx1 on billing_document_file (billing_document_id);
