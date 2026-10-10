-- Copied unchanged from eegfaktura-backend/migrations/20250604182344_add_bank_fields.up.sql at da3d505a5e6182436deb0ef612ffd803629ea3eb (AGPL-3.0).
-- The legacy base schema for v3's local/E2E stacks and the ITs (m02 T1); applied by docker/postgres-init/40-legacy-base.sh.
-- modify "bankaccount" table
ALTER TABLE "base"."bankaccount" ADD COLUMN IF NOT EXISTS "mandate_reference" VARCHAR NULL,
    ADD COLUMN IF NOT EXISTS "mandate_date" DATE NULL DEFAULT now()::DATE,
    ADD COLUMN IF NOT EXISTS "sepa_direct_debit" VARCHAR NULL;
-- modify "eeg" table
ALTER TABLE "base"."eeg" ADD COLUMN IF NOT EXISTS "creditor_id" text NULL;
