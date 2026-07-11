# Changelog

All notable changes to **eegfaktura-billing (Java invoicing/billing service)** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and
versioning follows the deployment release tags. Detailed diffs stay in the `git log`;
this changelog highlights the changes relevant for overview and operations.

## [Unreleased]

### Added
- **Time-of-use tariffs (ZVT)**: a time-based tariff prices up to two named
  daytime windows (T1/T2) plus a base price. The caller (web) sends per
  metering point the window sums from energystore (`allocations[].buckets:
  [{key: BASE|T1|T2, kWh}]`) plus the used window definitions
  (`allocations[].timeWindows`) — billing prices each bucket separately
  (1–3 positions per metering point, labels `Tarif: Basis` /
  `Tarif: <Name> (HH:MM - HH:MM)`), discount/VAT/rounding per position as
  before. Fail-loud contract guards: a time-based tariff without buckets, a
  mismatch between the sent windows and the current masterdata windows (the
  view is live, no tariff snapshot) or buckets on a simple tariff abort the
  run with a clear error (`FAILED` + errorSummary in the async path). New
  masterdata fields come from the extended backend view
  `base.billing_masterdata` (no billing-side Flyway). Free kWh remain
  simple-mode-only.

### Changed
- **PDF layout: items are now grouped per metering point on ALL documents**
  (invoice, credit note, RC credit note, info): a bold block header
  `Zählpunkt <nr> - <name> (Rabatt xx %)`, the item rows without the
  repeated metering point id, the metering point fee inside its block, and
  a bold `Zwischensumme Zählpunkt <nr>` (net/VAT/gross) per block.
  Participant-level positions (membership fee) follow after the blocks.
  Metering point fee items now carry the `metering_point_id` (grouping
  key); per-metering-point amounts in the API aggregate multiple positions
  per metering point into one entry.
- **Billing run is now asynchronous** (`POST /api/billing` → `202 Accepted` + `billingRunId`,
  progress via polling `GET /api/billingRuns/{id}`): the synchronous call used to run for
  minutes on large communities, hit proxy timeouts (504 while the run kept going server-side)
  and an impatient second click queued a full second run behind the per-tenant in-memory lock.
  Duplicate starts now get `409 Conflict` with the id of the already-running run; a full
  executor answers `503` and rolls the status claim back. The in-memory lock is replaced by an
  atomic DB status claim (`RUNNING` only from `NEW`/`FAILED`) — safe across replicas.
- `BillingRunStatus` gains `RUNNING` and `FAILED` (appended — ordinal mapping!); a failed run
  persists a short `errorSummary` on the billing run (previously the error only lived in the
  ephemeral HTTP response) and can be restarted (cleanup of partial documents included).
  Preview runs end back at `NEW` (unchanged semantics), final runs at `DONE`;
  `DONE`/`CANCELLED` still reject new starts.

- Flyway `V1_15`: `billing_run.error_summary` column + unique index on
  `(tenant_id, clearing_period_type, clearing_period_identifier)` (closes the first-creation
  race of two parallel starts; fails visibly if pre-existing duplicate rows need manual cleanup).

## [1.0.2] – 2026-07-05

### Fixed
- Mail send no longer hands raw recipient strings to the mail parser ("Illegal address",
  observed in prod for addresses with leading/trailing whitespace): to/cc are normalized per
  `;`-separated part (outer whitespace incl. NBSP stripped) and validated against the shared
  suite-wide address rule before the MimeMessage is built; the normalized values are what
  actually gets sent. Invalid parts are reported instead of failing the whole message —
  no valid recipient at all remains an error.

### Changed
- Billing run send protocol: a failed send now names the member (participant number + name)
  next to the address — a blank/garbage address used to produce an unattributable
  "  FEHLER" entry. Rejected address parts on an otherwise delivered mail (e.g. an invalid
  issuer cc) are reported as a warning on the OK line, NOT as FEHLER — flagging a delivered
  invoice as failed would invite manual re-sends and duplicate invoices.
- CI: Preview-Deployments (ADR-0007) — Push auf `preview/**` baut+deployt on-demand in die Dev-Zone (sha-pinned, kein `:latest`), Auto-Reset bei Branch-Delete.


## [1.0.1] – 2026-06-30

### Changed
- Bump `jasperreports` 7.0.2 -> 7.0.4. (#24)
- CI: Snyk Code (SAST) workflow + SARIF upload to code scanning. (#25, #26)

## [1.0.0] – 2026-06-28

Part of the unified source-build cutover of the eegfaktura suite.

### Changed
- CI: push to the registry's development tier (ADR-0005). (#20)
- Multi-stage Docker build with a version glob in the runtime stage. (#18)
- Added README with service overview and tech stack. (#21)

## Earlier releases

Before the 1.0.0 cutover, billing was versioned independently (`v0.1.9`–`v0.1.23`,
2024-04 to 2025-06). Full list: [GitHub Releases](https://github.com/eegfaktura/eegfaktura-billing/releases).
Selected:

- **v0.1.23** (2025-06-28): JRXML template shows issuer/bank creditorId; among others
  creditorId, SEPA direct debit, tariff ID/version in invoices/XLSX export.
- **v0.1.22** (2025-04-16): additional mail headers (Reply-To, Return-Path).
- **v0.1.21** (2025-04-14) … **v0.1.9** (2024-04-04): see releases.
