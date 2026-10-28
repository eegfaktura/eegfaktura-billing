-- Joining fee per member (konzept-beitrittsgebuehr.md, extension 27.10.2026): charge requested for the next final
-- run, or paid outside eegfaktura (date, optional note). One row per (tenant, member); additive, no backfill.
-- An older image ignores the table (requests are then not charged, members paid externally get the automatic fee).
create table if not exists billing_joining_fee_record (
    id             uuid         not null,
    tenant_id      varchar      not null,
    participant_id varchar      not null,
    kind           varchar      not null,
    paid_on        date,
    note           varchar,
    created_by     varchar,
    created_at     timestamp(6) not null,
    primary key (id),
    constraint billing_joining_fee_record_tenant_participant_uk unique (tenant_id, participant_id),
    constraint billing_joining_fee_record_kind_ck check (kind in ('CHARGE_REQUESTED', 'PAID_EXTERNALLY'))
);
