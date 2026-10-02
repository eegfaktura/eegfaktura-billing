# `legacy-base/` — the real `base.billing_masterdata` view for the M6 contract tests

Billing reads master data through the view `base.billing_masterdata`, which the Go backend
(`eegfaktura-backend`) owns (AGENTS.md section 8, `known-errors.md` #10). These files build the
**real** view in the own database of `support/LegacyBaseDatabase` (never the M3 database, which
holds a plain table of the same name). Used by `contract/LegacyMasterdataViewContractTests` and
`contract/LegacyMasterdataEntityReadTests`.

## Provenance (AGPL-3.0, decision B-16)

Copied **byte-identical** (checked with `cmp`) from eegfaktura-v3
`docker/legacy-base/` at commit `ad120d1d712765f37c418b770e7c099f8bb4fd10` (2026-09-29, the last
commit touching that directory; v3 HEAD at copy time `0b785d2`, branch `bugfixes-poc`), copied
2026-10-02. v3 itself copied them unchanged (plus a two-line header) from
`eegfaktura-backend/migrations/*.up.sql` at `da3d505a5e6182436deb0ef612ffd803629ea3eb`. Licence
AGPL-3.0, same as billing; row in `EXTERNAL_SOURCES.md`.

| File | Backend migration | sha256 |
|---|---|---|
| `01_20250603103206_activeMeterView.up.sql` | base tables `eeg`, `participant`, `tariff`, `address`, `bankaccount`, `contactdetail`, `meteringpoint`, … | `8dd321dd994763f0702078bdad6c7aa6ee554f5dde5ef049664af9915d01422c` |
| `02_20250603150318_create_views.up.sql` | views `activeMeteringPartition`, `activeTariff`, first `billing_masterdata` | `0c38063b20306713031632a2bb3040faa0f778c3cd188ab08d2ae35520852d44` |
| `04_20250604182344_add_bank_fields.up.sql` | `bankaccount.mandate_*`, `sepa_direct_debit`, `eeg.creditor_id` | `d82172801b0b3f24e3b2e836e9d367043ecac771639054b93912edd8dadd7ebb` |
| `05_20250605091807_add_account_info_to_billing_view.up.sql` | replaces `billing_masterdata` (64 columns) | `699253d230b57cc7be72416a92d6634ca38a311f5ae0565fed0e46e709812113` |

The sha256 values are also in `LegacyMasterdataViewContractTests.COPIED`; a local edit fails
`copiedFilesAreUnchanged`.

**Minimal subset** (inventory 2026-10-02): `03_…init_grid_operators` only inserts grid operators,
`06_…last_process_state`, `07_…eeg_bic_field` and `08_…add_bank_purpose` only add table columns the
view does not select — none of them changes the view, so they are not copied. `down` scripts are
never run. The files need no role and only the `uuid-ossp` extension, which the PostgreSQL image has.

The v3 view after a cutover (`billing_masterdata_v3`) is compared by name only, from
`../contracts/v3view/V190__billing_masterdata_v3.sql` (see `../contracts/README.md`).

## Refresh

Nothing is fetched by the build: CI never reads a sibling checkout.

1. In eegfaktura-v3: `git log -1 --format=%H -- docker/legacy-base` — if newer than the commit above,
   look at what changed.
2. Compare with the owner: `ls eegfaktura-backend/migrations/*.up.sql` — v3's copies lag the backend
   (pinned at `da3d505`). Every backend migration that touches `billing_masterdata` or a column it
   selects matters. Known on 2026-10-02: branch `feat/zvt-time-tariff` of the backend
   (`20260711120000_zvt_time_tariff.up.sql`, commit `81ab6f8`) recreates the view with time-tariff
   columns; it is not on the backend's main line and not in v3's copies.
3. Copy the new or changed files unchanged (`cp`, then `cmp`), add them to
   `LegacyBaseDatabase.SCRIPTS` in order, update the table above, the sha256 in
   `LegacyMasterdataViewContractTests.COPIED`, the commit id here and the row in `EXTERNAL_SOURCES.md`.
4. Run `-Dtest='LegacyMasterdata*Tests'`. A failure names the column; a column billing reads that
   disappears is a contract break to be agreed with the backend first (AGENTS.md section 8).
