# AGENTS.md

Authoritative working agreement for every AI agent and every developer in this repository.
`CLAUDE.md` only points here. Keep this file as the single source.

## 1. Purpose

- Prefer verified repository facts over aspirational architecture.
- If the codebase and a requested target architecture differ, call out the mismatch and avoid silent large-scale rewrites.
- Treat architectural migrations (Java or Spring Boot major version, migration tool, auth model, report engine) as explicit tasks.
- Every task ends with: code, tests, docs and the tracking files (section 14) updated.

## 2. Verified Baseline

This repository is **eegfaktura-billing**, the invoicing service of the eegfaktura platform
(`/cash` behind the proxy): billing runs, invoices and credit notes as PDF (JasperReports),
document numbers, SEPA/XLSX exports and the invoice mail. It is called by the customer web
(`eegfaktura-web`) and by eegfaktura-v3, always with the user's Keycloak token.

- Java 21 (`<java.version>` in `pom.xml`), Spring Boot 3.5.x (parent POM), Maven via the wrapper (`./mvnw`, Maven 3.8.7). Lombok generates accessors — **the build needs JDK 21**; under JDK 25 Lombok fails (`known-errors.md` #4).
- Database: PostgreSQL, schema `billingj` in the shared database `eegfaktura`, Flyway (`src/main/resources/db/migration`), `ddl-auto=validate`. Billing reads master data through the view `base.billing_masterdata`, which the Go backend (`eegfaktura-backend`) owns (section 8).
- Authentication: bearer JWT from Keycloak, verified against a static certificate (`JWT_PUBLICKEYFILE`), stateless. The community is the `Tenant` request header (section 6).
- Reports: JasperReports (`BillingDocumentDefaultTemplate.jrxml`), mail body FreeMarker (`BillingEmailDefaultTemplate.ftl`), Apache POI for XLSX.
- API base path: `/api` (springdoc on `/api/**`).
- Deployment: one Docker image (`Dockerfile`, multi-stage Maven → Temurin JRE, non-root UID 1001); CI `rolling-release.yml` builds and pushes it after the reusable `test.yml` (`./mvnw -B clean verify`, JaCoCo gate) is green (`known-errors.md` #6).
- Licence: AGPL-3.0 (`LICENSE`).

## 3. Project Layout

- `src/main/java/org/vfeeg/eegfaktura/billing/`
  - `rest/` REST resources (controllers), `controller/` the home page
  - `service/` business logic and transactions, `repos/` Spring Data repositories
  - `domain/` JPA entities, `model/` DTOs
  - `security/` JWT filter, tenant context, security config
  - `config/` Spring configuration and `RestExceptionHandler`, `util/` helpers and `AppProperties`
- `src/main/resources/`: `application.properties`, Flyway migrations in `db/migration/`, report and mail templates
- `src/test/java/` mirrors the main packages; `src/test/resources/` test configuration and SQL fixtures
- Documentation: `docs/`; migration concepts in `docs/migrations/`
- Tracking files (section 14): `AGENT_LOG.md`, `known-errors.md`, `open-points.md`, `EXTERNAL_SOURCES.md`

Keep new code close to the feature it belongs to. Do not create parallel layers beside the existing packages.

## 4. Backend Standards

- Constructor injection; one responsibility per class; cohesive services.
- Resources (`rest/`): thin, map to `/api/...`, validate DTOs with Jakarta annotations, never expose JPA entities, return DTOs.
- Services own transactions (`@Transactional` in `service/`, never in resources). Read-only queries use `@Transactional(readOnly = true)`.
- Errors: throw a specific exception, map it once in `RestExceptionHandler` to the existing `ErrorResponse` shape. No ad-hoc error bodies in resources. (A move to RFC 9457 `ProblemDetail` is `open-points.md` B-9, not done silently.)
- Time: `java.time` only.
- Configuration: `@ConfigurationProperties` (`AppProperties`), environment-driven with defaults in `application.properties`; **secrets never in the repository**, and no default that logs into a database (`known-errors.md` #8).
- Money: `BigDecimal` with explicit scale and rounding; never `double` for amounts.
- Dependencies: reuse what the POM already has. Adding a dependency is an external source (section 12).

## 5. Report and mail templates

- The JasperReports template and the FreeMarker mail template are code: a change needs a test that renders it (the integration tests do) and a look at the generated PDF (`TEST_STORE_DOCUMENTS_PATH`, section 9).
- A change that alters a printed document (wording, numbers, layout) is a user decision: note it in `open-points.md` and in `CHANGELOG.md` — communities keep these documents for years.

## 6. Security Standards

- Every request under `/api` needs a valid bearer token and the role `EEG_ADMIN` (`JwtSecurityConfig`); deny by default.
- **Tenant isolation is the first rule.** The `Tenant` header must be one of the communities in the token's `tenant` claim, and every resource compares the tenant of the record it reads or writes with that header (`TenantContext.validateTenant`). A new endpoint without that comparison is a defect. For an update, the **stored** record's tenant decides, not the one in the body.
- Tokens: signature, expiry, issuer and client are checked; a token of another client of the realm is not a billing caller.
- **State on `master` (2026-10-01): the header-against-claim check and the issuer/client check are not in force** — the old check never matched (`known-errors.md` #1). The fix is on branch `fix-tenant-claim`; until it is merged these two rules describe the target, the per-record comparison is what holds.
- `DevSecurityConfig` (profile `dev`) switches authorization off — never in a deployed environment (`known-errors.md` #7).
- Never log tokens, IBANs, full request bodies or personal data; sanitize request values before they are logged (no line breaks, bounded length).

When editing auth or security-sensitive code: report critical security issues before writing tests that would normalize insecure behaviour.

## 7. API Conventions

- All REST endpoints under `/api`; JSON camelCase; DTOs for every request and response.
- The community is the `Tenant` header; a tenant in the path or body must equal it.
- Validation in DTOs and at the service boundary.
- The callers are `eegfaktura-web` and eegfaktura-v3 (`BillingClient`): a change to a DTO or a path is a contract change — say so in `CHANGELOG.md` and tell the callers.

## 8. Database, Flyway and the Migration Concept

- Flyway migrations in `src/main/resources/db/migration/`, named `V1_<N>__<snake_case>.sql` (the series so far: `V1_0` … `V1_14`), schema `billingj` (`spring.flyway.default-schema`).
- Applied migrations are immutable; never rename or edit them. Schema constraints live in migrations, not only in entities. A schema change without a migration does not exist (`ddl-auto=validate`).
- No rollback in the file: the rollback lives in the concept's section 4 and is run by hand (Flyway Community has no undo).
- **`base.billing_masterdata` is a contract, not billing's table.** The Go backend creates and changes it with its own migrations; eegfaktura-v3 replaces it with a `UNION` view per community at its cutover. Billing reads it through `@Subselect` (`BillingMasterdata`), which `validate` does not check. A column billing starts to read must exist in the view, and the change must be agreed with the backend (and v3) first.

### 8.1 Migration concept is mandatory

Every change that touches persisted data ships with a migration concept in
`docs/migrations/V1_<N>-<slug>.md` (template: `docs/migrations/TEMPLATE.md`) that answers:

1. **What changes** (tables, columns, constraints, indexes) and why.
2. **Data migration**: how existing rows are transformed, backfilled or defaulted; expected volume and duration.
3. **Compatibility**: whether the previous application version still runs against the new schema; what must be deployed together.
4. **Rollback**: the statements to run by hand and the data that cannot be restored.
5. **Verification**: the SQL or test that proves the migration on a copy of production data.

Document the main process only — the one path used to migrate the production system.

## 9. Local Runtime and Build

- Build and test need **JDK 21**: `./mvnw test`, `./mvnw package`.
- Without a local JDK 21, run Maven in the builder image of the `Dockerfile`; the Testcontainers suites need the Docker socket:
  ```bash
  docker run --rm -v "$PWD":/src -w /src -v ~/.m2:/root/.m2 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --add-host=host.docker.internal:host-gateway \
    maven:3-eclipse-temurin-21 mvn -B test
  ```
- `src/test/resources/docker-java.properties` sets the Docker API version for Testcontainers (Docker 29 refuses the default, `known-errors.md` #2).
- To look at the PDFs and XLSX the integration tests produce: `TEST_STORE_DOCUMENTS_PATH=<dir>` (empty = not written).
- Run the service: the variables of `README.md` (`JDBC_DATABASE_*`, `MAIL_*`, `JWT_PUBLICKEYFILE`, …); the whole platform runs from `eegfaktura-docker-compose`.

## 10. Testing Standards — unit tests are mandatory

No change without tests. A change that adds or alters behaviour without a test for that behaviour is incomplete.

### 10.1 Common rules

- Review the implementation before writing tests. If code is broken or insecure, report it first and fix it (or ask) before writing tests. Never write tests that bless broken behaviour.
- Prefer high-signal tests that protect API-visible behaviour and printed results; avoid tests that only prove framework behaviour or mock interactions.
- Deterministic data, no sleeps, no real network.
- A fixed test blesses the current contract: when a test fails because the contract changed, update the test to the new contract, never weaken the assertion.

### 10.2 Levels

- Unit tests (JUnit 5, Mockito, Hamcrest) for services, security and helpers.
- `@WebMvcTest` with the real security configuration for resources and authorization.
- `@SpringBootTest` + Testcontainers PostgreSQL (`BillingIntegrationTests`, `DocumentNumbersTests`) for billing runs, document numbers and the migrations.
- Cover: happy path, tenant isolation (own, foreign and missing tenant), validation errors, not-found, amounts and rounding, document numbering, and the PDF/XLSX output.

### 10.3 Test efficiently

- While iterating: one class, `./mvnw test -Dtest='<Name>Tests'`.
- **Once per step**, before the merge: the whole suite, `./mvnw test`. A change that only passes in the narrow loop is not green.
- Tests are written once, at the end of a step, against the shape that survived — not against a draft.
- No measurement is a test: a mass-data run (`massdatatest/`, `MassDataGenerator`) runs only when the user says so.

## 11. Logging

The log must let a human or an AI reconstruct what happened without access to the running system.

- Message convention for new and changed code: `event=<domain.action.outcome>` followed by `key=value` pairs with stable keys, ids instead of names. Example: `event=billing.run.finished runId=… tenant=RC100001 documents=42 durationMs=1830`.
- `ERROR` only with a stack trace and once per failure; `WARN` for handled anomalies; `DEBUG` for decisions and counts.
- Never log tokens, IBANs, request bodies or personal data.
- Structured (ECS JSON) output is `open-points.md` B-5; until decided the console format stays.

## 12. External sources — trusted only

- Every external source the build, the tests or the running service depends on is listed in `EXTERNAL_SOURCES.md`: registries, base images, libraries with their licence, runtime services.
- Adding a new external source — a library, a base image, a service the code calls — requires an explicit decision: record the question in `open-points.md`, wait for the answer, then add the row to `EXTERNAL_SOURCES.md` in the same change.
- Licences: the service is AGPL-3.0; every dependency must carry an open-source licence that allows redistribution and is compatible with it (permissive, LGPL, GPL-3.0, AGPL-3.0; not GPL-2.0-only). An unknown licence is a blocker. Check the package's own metadata (POM), not a summary site.

### 12.1 Strict rule: fixed versions, nothing younger than 7 days

1. **Every version is fixed.** Exact versions in the POM (transitive ones from the Spring Boot parent), full tags in the `Dockerfile` and in CI actions — no ranges, no `latest`, no floating major tags. The current floating tags are `known-errors.md` #9.
2. **Nothing younger than 7 days.** A version enters this repository only when it was published at least 7 days before. For a manual change, check the publish date (Maven Central `last-modified`, Docker Hub, release page) and record it in the commit or `AGENT_LOG.md`.
3. Dependency updates go through reviewed pull requests with a green test run.

## 13. Size limits — lines per file

| Lines | Status | Rule |
|---|---|---|
| ≤ 300 | green | fine |
| 301 – 450 | yellow | allowed; plan a split and note it in `open-points.md` if it does not happen in the same change |
| 451 – 600 | red | split before the change is merged unless the user explicitly accepts it (record in `open-points.md`) |
| > 600 | blocked | not accepted for new code; existing files above it are `open-points.md` B-6 |

Applies to Java, SQL and template files including tests. `bash scripts/dev/loc-check.sh` prints the status. Split by responsibility, never by arbitrary cut.

## 14. Tracking files — keep them current in every task

- `AGENT_LOG.md` — every AI session appends one entry: date, task, what was changed, decisions, verification, what is still open. Newest at the top. Write it before the final summary.
- `known-errors.md` — every known defect, flaky test, workaround or gap, with status (open, mitigated, fixed on date). Add the entry the moment the problem is found, even when it is fixed in the same change.
- `open-points.md` — every decision that belongs to the user or the maintainer: one line with date, question and current assumption; answered points move to "Decided".
- `EXTERNAL_SOURCES.md` — see section 12.
- `docs/migrations/` — see section 8.1.
- `CHANGELOG.md` — every change relevant for operation or the callers, under `[Unreleased]`.

## 15. Do Not

- Do not silently upgrade Java, Spring Boot or JasperReports major versions.
- Do not edit an applied migration, and do not change `base.billing_masterdata`'s use without the backend's owners.
- Do not add an endpoint without the tenant comparison of section 6.
- Do not add an external source without a decision, and never one whose licence forbids redistribution.
- Do not use a version range, a floating tag, `latest`, or a release younger than 7 days.
- Do not silence a failure to make a run green: no swallowed errors, weakened assertions, skipped tests or deleted checks.

## 16. AI Behavior Rules

- Prefer small, targeted changes; keep documentation aligned with the repository state.
- When requirements are ambiguous, keep the existing conventions, state the assumption, and record the question in `open-points.md`.
- If docs and code disagree, fix the docs or call out the mismatch — never assume the docs are right.
- Fix pre-existing errors you meet, even outside the task, and say so in the summary; verify a "pre-existing" failure against a clean tree first, then fix the cause, not the symptom. Where the fix is a decision (a version jump, a changed document), record it in `open-points.md` instead.
- This is an upstream repository of the eegfaktura project: changes reach production through a pull request the maintainer reviews. Commit in small steps with descriptive messages; never push without being asked.
- End every task with the checklist: build green, tests green, migration concept written if data changed, logging events added, `CHANGELOG.md`, `AGENT_LOG.md`, `known-errors.md`, `open-points.md`, `EXTERNAL_SOURCES.md` updated.

## 17. Quick Reference

- Build: `./mvnw package` — tests: `./mvnw test` — one class: `./mvnw test -Dtest='<Name>Tests'` (JDK 21; container command in section 9)
- Size check: `bash scripts/dev/loc-check.sh`
- Image: `docker build -t eegfaktura-billing .`
