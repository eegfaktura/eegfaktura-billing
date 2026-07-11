alter table if exists billing_run add column error_summary text;
-- Fails visibly if duplicate (tenant, period) rows exist - clean up manually first.
create unique index billing_run_tenant_period_uk on billing_run (tenant_id, clearing_period_type, clearing_period_identifier);
