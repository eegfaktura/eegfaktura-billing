# Changelog

All notable changes to **eegfaktura-billing (Java invoicing/billing service)** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and
versioning follows the deployment release tags. Detailed diffs stay in the `git log`;
this changelog highlights the changes relevant for overview and operations.

## [Unreleased]

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
