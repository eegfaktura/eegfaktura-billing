# Changelog

All notable changes to **eegfaktura-billing (Java invoicing/billing service)** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and
versioning follows the deployment release tags. Detailed diffs stay in the `git log`;
this changelog highlights the changes relevant for overview and operations.

## [Unreleased]

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
