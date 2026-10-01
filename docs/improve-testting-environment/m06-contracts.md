# M6 — Contracts with the neighbours

**Concept:** phase 6 · **Status:** open · **Production code:** none
**Depends on:** M0 (CI, thresholds); read access to the sibling repositories; B-16 (how the backend
SQL may be brought in). Independent of M3: the contract tests start their **own** PostgreSQL
container (a second holder class modelled on M0's `PostgresContainerHolder`).
**Effort:** about 2 days (the view part may take longer, see risks).

## Goal

Changes in a neighbour that would break billing are caught by a test in billing, not in production
(`known-errors.md` #10, AGENTS.md §8: `base.billing_masterdata` is a contract).

## Scope

1. **Master-data view contract.** Today `src/test/resources/billing_master_data.sql` creates a plain
   *table* `base.billing_masterdata`, so nothing checks the real view. The test builds the **real
   view** and asserts that every column the `BillingMasterdata` entity maps exists with a compatible
   type (billing reads it through `@Subselect … select … m.* from base.billing_masterdata m`, which
   `ddl-auto=validate` does not check). Source in `eegfaktura-backend` (paths checked 2026-10-01; `schema.sql` and
   `database/migration/000001_init-setup.up.sql` also mention the name — check them in the inventory): view
   `migrations/20250603150318_create_views.up.sql`, replaced by
   `migrations/20250605091807_add_account_info_to_billing_view.up.sql`. The view reads
   `base.participant`, `eeg`, `meteringpoint`, `address`, `activetariff`, `bankaccount`,
   `contactdetail`, so the test needs the base schema first (`eegfaktura-backend/schema.sql` or
   `eegfaktura-postgresql/docker-entrypoint-initdb.d/11_base_schema.sh` + `21_base_migration.sql`)
   and the later migrations in order. The minimal, ordered script set is found in the first task.
   The test runs in its own container/schema, never in the M3 database (which holds the table of the
   same name). A second, optional source is the `UNION` view of eegfaktura-v3 at its cutover.
2. **Caller DTO contract.** JSON fixtures of the requests that callers send, stored under
   `src/test/resources/contracts/` with source file, repository, commit and date. Known callers:
   `eegfaktura-web` (`src/service/eeg.service.ts`) and eegfaktura-v3
   (`backend/src/main/kotlin/at/eegfaktura/integration/billing/BillingClient.kt`,
   `LegacyBillingDtos.kt`). One test per endpoint a caller uses: the payload deserializes into the
   DTO (`DoBillingParams`, `BillingConfigDTO`, …) and passes validation; whether unknown fields are
   tolerated is pinned as the code decides today (document which).
3. **Response contract (optional).** The field names of the responses the callers read, pinned as
   JSON snapshots; a change needs a conscious update.

## Tasks

- [ ] Inventory (list in `AGENT_LOG.md`): view-creating migrations and their prerequisites in the backend; endpoints and DTOs the web app and v3 use (grep both repos)
- [ ] B-16 — decide how the SQL is brought in (`open-points.md`): copy with commit id (check the licence first — `eegfaktura-backend` has no `LICENSE` file in its root; web and v3 have one) vs. sibling path (the test must then **fail or be reported as skipped in CI**, not silently pass)
- [ ] View contract test (Testcontainers, own database): entity columns ⊆ view columns, type check for numbers/dates
- [ ] DTO fixtures + one test per used endpoint; response snapshots if time permits
- [ ] README next to the fixtures: how to refresh when a neighbour changes
- [ ] Full suite once; thresholds; `AGENT_LOG.md`; `known-errors.md` #10 status

## Acceptance criteria

- Renaming or dropping a column in a copy of the view script (shown once on a throw-away change) makes the view-contract test fail and the message names the column.
- Every endpoint with a known caller has a fixture test; `grep -c` of fixtures per endpoint is listed in `AGENT_LOG.md`; each fixture states source repository, commit/date and refresh steps.
- Copied SQL files are byte-identical to the source at the recorded commit; their licence permits the copy (recorded in `EXTERNAL_SOURCES.md` or the README).
- `git diff --stat src/main pom.xml` is empty.

## Risks

- The view needs a large base schema (and maybe roles/extensions the Testcontainers database lacks); keep the set minimal and documented, or accept a reduced stand-in schema only if column names and types are copied from the real migration.
- Copied fixtures go stale silently; the commit id makes the age visible; a CI job that diffs them against the neighbour is future work.
- The backend uses atlas migrations (`atlas.sum`); the order and `down` scripts must not be executed.
- eegfaktura-v3 replaces the view at its cutover; a contract written only against the Go backend becomes outdated then.
