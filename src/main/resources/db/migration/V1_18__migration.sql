-- Joining fee (konzept-beitrittsgebuehr.md, platform#217): kind of an item (ENERGY, PARTICIPANT_FEE,
-- METERING_POINT_FEE, JOINING_FEE). Empty for items written before; no backfill - the history check
-- recognises their energy items by metering point and text. An older image ignores the column.
alter table if exists billing_document_item add column if not exists item_type varchar;

-- History per member of a community (due check of the joining fee, endpoint joiningFeeState).
create index if not exists billing_document_tenant_participant_idx on billing_document (tenant_id, participant_id);
