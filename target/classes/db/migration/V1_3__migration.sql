alter table if exists billing_run rename column billing_period_type to clearing_period_type;
alter table if exists billing_run rename column billing_period_identifier to clearing_period_identifier;
