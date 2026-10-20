# Changelog

All notable changes to **eegfaktura-billing (Java invoicing/billing service)** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and
versioning follows the deployment release tags. Detailed diffs stay in the `git log`;
this changelog highlights the changes relevant for overview and operations.

## [Unreleased]

> **Branch `env/billing`:** the entries from here to the next note are only on this branch (feature environment), not on the default branch yet.

### Fixed
- **PDF: blank line below the metering point header** (pilot feedback 20.10.2026). Jasper measured text with DejaVu
  Sans, the only font in the image, but the PDF is written in Helvetica, which is narrower: „Zählpunkt AT…“ (233 pt in
  DejaVu Sans Bold, cell 194 pt) got two lines reserved and printed in one. The image now installs
  `fonts-liberation` and fontconfig prefers Liberation Sans (Helvetica's glyph widths, 189 pt) for sans-serif, the
  font Jasper measures with. The PDF fonts stay Helvetica/Helvetica-Bold.
- **PDF: bold text lost** (only on env/billing since #64): setting Jasper's default font to „Liberation Sans“ made the
  PDF export use plain Helvetica for bold text too. The default font stays SansSerif again (`jasperreports.properties`
  removed); the measuring font comes from fontconfig instead.

### Changed
- **PDF: no „Zwischensumme Zählpunkt“ for a single position** (pilot feedback 20.10.2026). A metering point block
  with one position (simple tariff without metering point fee) repeated it as subtotal. The subtotal now appears
  only when the block has two or more positions (time-based tariff, metering point fee).

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
- **ZVT: all tariff options are shown, even at 0 kWh**: for a time-based
  tariff every configured position (`Tarif: Basis` / the active windows) is
  rendered even when its quantity is 0 kWh (0,00 €), so no option appears to
  be missing to the reader. The general "no zero position" suppression still
  applies to simple tariffs.
- **PDF layout: items are now grouped per metering point on ALL documents**
  (invoice, credit note, RC credit note, info): a bold block header
  `Zählpunkt <nr> - <name> (Rabatt xx %)`, the item rows without the
  repeated metering point id, the metering point fee inside its block, and
  a bold `Zwischensumme Zählpunkt <nr>` (net/VAT/gross) per block.
  Participant-level positions (membership fee) follow after the blocks.
  Metering point fee items now carry the `metering_point_id` (grouping
  key); per-metering-point amounts in the API aggregate multiple positions
  per metering point into one entry. The `Zwischensumme Zählpunkt` row is
  framed by a faint separator line above and below (no line between the
  individual tariff-variant rows, no grey background); the subtotal label
  reads `Zwischensumme Zählpunkt` without the metering point id and also
  shows the summed energy amount (kWh) of the block's energy positions.
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

### Changed
- Mail deliverability: document mails now go out as **multipart/alternative** (a plain-text part
  derived from the rendered HTML — a lone `text/html` body is a spam signal), carry an
  `Auto-Submitted: auto-generated` header, and use a **Message-ID with the `eegfaktura.at` domain**
  instead of the pod hostname (`spring.mail.properties.mail.from`). The document mail subject now
  includes the EEG name; the default mail template declares `lang="de"`.

> **End of the `env/billing`-only entries.**

### Added
- CI: PR checks and security scan. Every pull request runs unit tests and the full suite with
  Testcontainers (`pr-checks.yml`); `security-scan.yml` looks for leaked secrets in new commits,
  vulnerable dependencies and Dockerfile/Kubernetes misconfigurations. The dependency and
  misconfiguration scans only report for now and do not block; a leaked secret fails the run. Image
  build and deploy (`rolling-release.yml`) are unchanged.
- CI: every run keeps its scan and test results as artifacts for 30 days (`scan-*`,
  `test-results-<job>`, `known-errors-<job>`) for a CI report collector; the secret scan keeps
  outcomes and counts only.

### Removed
- Unused WebFlux starter (`spring-boot-starter-webflux`). With it netty and reactor-netty leave the
  image; known CVEs in these libraries no longer apply. The one leftover netty import
  (`StringUtil.isNullOrEmpty` in `BigDecimalTools`) is replaced by a plain null/empty check.

## [1.0.6] – 2026-10-17

### Security
- Embedded Tomcat raised to 10.1.60 (from 10.1.55, managed by Spring Boot 3.5.16) to fix known
  vulnerabilities in the web server that serves every request (CVE-2026-68525 incorrect
  authorization, fixed in 10.1.58; CVE-2026-68569 authentication bypass). Affects
  `tomcat-embed-core`, `-el` and `-websocket`.

## [1.0.5] – 2026-10-05

### Fixed
- **Super-admins could no longer load another community's billing settings.** Since the tenant
  check in `JwtRequestFilter` took effect (1.0.4), a token with the realm role `superuser` was
  refused for every community not in its own `tenant` claim — unlike backend and energystore,
  which let that role through. The filter now does the same; the `EEG_ADMIN` role and the
  per-record tenant checks still apply. A token without a `tenant` or `access_groups` claim
  is no longer rejected as a whole; those lists are simply empty.

## [1.0.4] – 2026-10-04

### Security
- **A billing configuration could be taken over by another community.**
  `PUT /api/billingConfigs/{id}` only checked the tenant in the request body, never the tenant
  of the stored record. An EEG admin who knew the id of another community's configuration could
  send it with their own tenant and so move it over; the other community then billed silently
  with the default texts and document-number settings. The stored record's tenant is now checked
  first, and an update never changes the tenant.
- **Any stored file was readable through one's own logo.** The file ids for logo, footer image
  and custom template were taken from the request body unchecked, and `GET …/logoImage` returns
  the referenced file without a tenant check. Setting the id of a known file — an invoice PDF of
  another community, for instance — as one's own logo made it downloadable. These ids are now set
  only by the upload and delete endpoints, never from the body.
  Both reported by a project member in a review of the billing code.
- Spring Boot 3.5.3 → 3.5.16, which brings patched Tomcat and Spring Security releases, and
  FreeMarker 2.3.34 → 2.3.35 (template path traversal, the only critical alert on this repo).
- The database login no longer falls back to `postgres`/`postgres` when
  `JDBC_DATABASE_USERNAME` or `JDBC_DATABASE_PASSWORD` is missing; the start fails instead. All
  deployments set both.

- **The tenant check never ran — in any direction.** `JwtRequestFilter` was meant to reject a
  request whose tenant is not in the caller's token. Three separate defects made it inert:
  the condition was inverted (`contains` instead of `!contains`); `Authority` overrode no
  `equals`/`hashCode`, so `contains` compared object identity and never matched anything; and
  the tenant was read from `TenantContext`, which a servlet filter at `@Order(1)` fills — while
  the Spring Security chain runs at order −100, i.e. before it. Thirty days of production logs
  contain the corresponding message exactly zero times.
  The filter now reads the `Tenant` header itself and checks it against the token's tenant
  list, which removes the ordering dependency entirely. `Authority` compares its tenant
  case-insensitively, as everywhere else in the suite.
  What remained in effect until now was `TenantContext.validateTenant()`, and that compares the
  tenant from the path against the one from the header — both supplied by the caller. It is
  only a boundary in combination with the filter check. A request without a tenant header no
  longer raises a `NullPointerException` (and with it a 500) but is rejected.

- The runtime image now starts from `eclipse-temurin:21-jre-jammy` instead of the full JDK
  image, and runs as a non-root user (UID/GID 1001). The builder stage still carries the JDK
  for `mvn package`, so the build is unchanged — the shipped image simply no longer contains a
  compiler toolchain it never uses. The UID is numeric because kubelet can only verify
  `runAsNonRoot` against a numeric `USER` directive; a named user yields
  `CreateContainerConfigError`. Prerequisite for the Pod-Security-Admission `restricted`
  profile. (#34)

### Fixed
- **A document date of "today" was rejected as post-dated during the night.** The container runs
  in UTC, so between midnight and 01:00 (02:00 in summer) Vienna time `LocalDate.now()` was still
  the previous day. The calendar day is now taken in `Europe/Vienna` — also for the default
  document date, which at the turn of the year decided the year and with it the number sequence.
- A participant fee or metering-point fee without a VAT rate stored the raw `null` and made the
  whole run fail with a `NullPointerException` in the VAT totals; it now counts as 0 %.
- `GET /api/billingConfigs/{id}/footerImage` required a multipart file parameter and could not be
  called as a plain GET; both image downloads now answer 404 instead of 500 when no image is set.

### Added
- OCI image labels (title, description, vendor, licenses) for registry and SBOM consumers. (#34)
- CI builds `env/**` branches and deploys the resulting image into the matching feature
  environment (ADR-0008): a push to `env/billing` pins `eegfaktura-billing` in namespace
  `env-billing` to that branch's `sha-…` image. Until now only `master`/`main`, tags and
  `preview/**` produced an image at all, so a feature branch had to be built and rolled out
  by hand — that is how the ZVT end-to-end test ran in July. The environment itself is still
  provisioned manually; this only deploys into an existing one.

## [1.0.3] – 2026-09-07

### Docs
- Add `docs/konzept-zeitvariable-tarife.md` — the ZVT (zeitvariabler Tarif) feature concept
  + tech design + acceptance criteria. billing is the lead component for this cross-component
  feature (backend/energystore/web); the concept lives here as the canonical spec.

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
