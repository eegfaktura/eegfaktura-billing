# `v3world/` — billing on the eegfaktura-v3 world (M3 optional item 1, B-16)

A **snapshot**, read by `scenario/BillingV3WorldTests`; the generator never runs in billing's build, there is
no network and no sibling checkout in CI. Fictional data (names, IBANs, meter points are generated).

| File | Content |
|---|---|
| `masterdata.json` | per community the rows of the **real legacy view** `base.billing_masterdata` (null columns left out; the test inserts them with `json_populate_record`) |
| `allocations.json` | per community one settlement period (`clearingPeriodType`, `clearingPeriodIdentifier`) and its allocations exactly as the legacy web builds them (`ParticipantPane.functions.ts`: energystore `/eeg/v2/<ecId>/report`, consumer → `utilization`, producer → `production − allocation`), plus `variant`, `settlementInterval`, `allocationMode` |

Twelve communities, 192 meter rows: eegfaktura-v3's legacy demo world (RC100401, RC100402, GC100403, RC100404;
variant `v3-demo`) and eight variety communities RC1005nn/GC1005nn — `fees` (participant fee 24 €, base fee,
5 % discount, quarterly), `free-kwh` (50 kWh, annual → billed half-yearly), `no-vat` (small business),
`meter-fee` (1.50 € + 20 %, half-yearly), `producer-vat` (feed-in with 20 % VAT, ten producers),
`three-prices` (three tariff versions), `vat-10` (10 %, members without SEPA mandate or bank account). The
sub-community GC100403-001 is missing: the legacy energystore refuses its code (tenant longer than 8 characters).

## Provenance

Chain (2026-10-08): eegfaktura-v3 (AGPL-3.0, same organisation) commit `0b785d2` —
`tools/demo/make-legacy-world.py` (seed 7) plus the dev workspace's variety communities → `tools/bench/load-base.sql`
into the legacy `base.*` (migrated by eegfaktura-backend `a1b5b18`) → `energy-mock backfill --channel mqtt`
(2026-01-01 … 2026-10-07) → the legacy energystore `2631ea3` ingests over MQTT → its `/report` and the view were read
by the dev workspace's `scripts/dev/world/export-billing-snapshot.py`. sha256 `masterdata.json` `c79de5ab74a858d4…`,
`allocations.json` `64732b09623d842a…`.

**Refresh** (by hand): seed the dev stack (`scripts/dev/seed-world.sh`), run the export script, review the
diff (one row per line), run `BillingV3WorldTests`, update the commit ids and checksums here and in
`EXTERNAL_SOURCES.md`. The expected energy lines are recomputed by the test from these rows (concept
arithmetic, `HALF_UP`), never taken from billing's output.
