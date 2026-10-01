# M6 — Contracts with the neighbours

**Concept:** phase 6 · **Status:** open · **Production code:** none
**Depends on:** M0 (CI, thresholds); read access to the sibling repository `eegfaktura-v3`
(B-16 decided: SQL and data come from v3, AGPL-3.0, same organisation). Independent of M3: the contract tests start their **own** PostgreSQL
container (a second holder class modelled on M0's `PostgresContainerHolder`).
**Effort:** about 2 days (v3 hosts the complete, ordered base schema and both views, which removes the
research part of the old estimate; the column/type check and the DTO fixtures remain).

## Goal

Changes in a neighbour that would break billing are caught by a test in billing, not in production
(`known-errors.md` #10, AGENTS.md §8: `base.billing_masterdata` is a contract).

## Scope

1. **Master-data view contract.** Today `src/test/resources/billing_master_data.sql` creates a plain
   *table* `base.billing_masterdata`, so nothing checks the real view. The test builds the **real
   legacy view** and asserts that every column the `BillingMasterdata` entity maps exists with a
   compatible type (billing reads it through `@Subselect … select … m.* from base.billing_masterdata m`,
   which `ddl-auto=validate` does not check). **Source (paths verified 2026-10-01, v3 commit
   `b43e864`):** `/mnt/src/eegfaktura-v3/docker/legacy-base/` — `01_20250603103206_activeMeterView.up.sql`
   (creates the base tables `eeg`, `participant`, `meteringpoint`, `address`, `tariff`, `bankaccount`,
   `contactdetail`, …), `02_20250603150318_create_views.up.sql` (the union view `base.billing_masterdata`),
   `04_…add_bank_fields`, `05_20250605091807_add_account_info_to_billing_view.up.sql` (replaces the view),
   `06`–`08` (further columns). Per v3's own `docker/legacy-base/README.md` these files are copied
   unchanged from `eegfaktura-backend/migrations` at `da3d505a5e6182436deb0ef612ffd803629ea3eb`
   (AGPL-3.0), so billing copies them **from v3** (AGPL-3.0) into `src/test/resources/legacy-base/`,
   byte-identical, with the v3 commit id and the refresh steps in a `README` next to them; a row in
   `EXTERNAL_SOURCES.md`. Apply in file order; the minimal subset needed is found in the first task
   (do not run `down` scripts). The test runs in its own container/schema, never in the M3 database
   (which holds the table of the same name).
   **Second contract, optional:** v3's own view `billing_masterdata_v3`
   (`backend/src/main/resources/db/migration/V190__billing_masterdata_v3.sql`, 64 columns with the
   same names and types as the legacy view per the maintainer's inspection — to be counted in the
   first task) is what billing sees after a community's cutover. It needs v3's schema (`v3.cutover`,
   `v3.legacy_uuid`, V1..V190), so it is only worth testing if the migrations can be applied as a
   set; otherwise record the column list from the file and compare the names only.
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

- [ ] Inventory (list in `AGENT_LOG.md`): the minimal ordered subset of `legacy-base/01..08` that builds the view; the column list of `BillingMasterdata` vs. the view (and the 64 columns of `billing_masterdata_v3`); endpoints and DTOs the web app and v3 use (grep both repos)
- [ ] Copy the SQL files byte-identical from v3 (B-16, AGPL-3.0) with commit id, README and `EXTERNAL_SOURCES.md` row; never read by sibling path in CI (no network, no sibling checkout in the build)
- [ ] View contract test (Testcontainers, own database): entity columns ⊆ view columns, type check for numbers/dates; optional second check against `billing_masterdata_v3`
- [ ] DTO fixtures + one test per used endpoint; response snapshots if time permits
- [ ] README next to the fixtures: how to refresh when a neighbour changes
- [ ] Full suite once; thresholds; `AGENT_LOG.md`; `known-errors.md` #10 status

## Acceptance criteria

- Renaming or dropping a column in a copy of the view script (shown once on a throw-away change) makes the view-contract test fail and the message names the column.
- Every endpoint with a known caller has a fixture test; `grep -c` of fixtures per endpoint is listed in `AGENT_LOG.md`; each fixture states source repository, commit/date and refresh steps.
- Copied SQL files are byte-identical to the v3 files at the recorded commit (`cmp`); the licence (AGPL-3.0, same as billing) is recorded in `EXTERNAL_SOURCES.md`.
- `git diff --stat src/main pom.xml` is empty.

## Risks

- The legacy files may need roles or extensions the Testcontainers database lacks (v3 runs them as the legacy role at initdb); keep the set minimal and documented.
- Copied fixtures go stale silently; the commit id makes the age visible; a CI job that diffs them against the neighbour is future work.
- v3's copies lag the Go backend (pinned at `da3d505`, a newer backend migration would not show up); the README states how to compare against `eegfaktura-backend/migrations` when a column changes.
- After a community's cutover billing reads `billing_masterdata_v3`; a contract written only against the legacy view misses that (hence the optional second check).
