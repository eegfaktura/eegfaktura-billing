# Concept: improving the test coverage of the billing service

**Date:** 2026-10-01 · **Branch:** `improve-testing-environment` · **Kind:** concept, no code change

This document describes how the test coverage of eegfaktura-billing is raised systematically.
It changes **no code**. Defects found during the analysis are recorded in section 4 and **not
fixed**; they are also in `known-errors.md` (#12 – #29).

**Decisions taken (2026-10-01):** JaCoCo, PDFBox and GreenMail are approved (section 6, B-11/B-12).
PIT (mutation tests) is approved because its licence is compatible (section 9). **Phase 4 is
blocked and optional; it will be done later.** All other measures stand as proposed. The effort of
phase 4 is estimated in section 7, its limits in section 7.1.

## 1. Goals

1. Every business-critical piece of logic — amounts, VAT, rounding, document numbers, status of a
   billing run — is covered by tests that turn red as soon as it changes.
2. The separation of communities (tenants) is tested for **every** endpoint.
3. Tests run on every change in CI; coverage is measured and must not drop.
4. Known defects are first made visible by a test, then fixed in a separate change (AGENTS.md
   §10.1: no test that pins wrong behaviour).

## 2. Starting point (measured 2026-10-01)

### 2.1 Tests

| Test class | Kind | Tests | What it checks |
|---|---|---:|---|
| `BillingIntegrationTests` | `@SpringBootTest` + Testcontainers PostgreSQL, SQL fixture | 9 | Preview and final billing of one run with exact amounts (5 documents), document date, reverse-charge credit notes, saving/reading the configuration, master data of the fixture; XLSX without any check |
| `DocumentNumbersTests` | `@SpringBootTest` + Testcontainers | 7 | Format and sequence of document numbers (start 0/1, digit overflow, ten in a row, year change) |
| `ClearingPeriodIdentifierToolTests` | unit | 5 | Texts of the billing periods (year, half year, quarter, month, leap years) |
| `EmailAddressUtilTest` | unit | 2 | Normalising and validating email addresses |
| **Total** | | **23** | |

All 23 tests are green (JDK 21, Docker 29, after the test-environment repairs in `ec80530`).

### 2.2 Coverage

Measured with JaCoCo 0.8.13 from the command line, **without** changing the `pom.xml`
(section 8.1):

| Package | Lines | Lines % | Branches % |
|---|---:|---:|---:|
| `service` | 842 / 1344 | 62.6 % | 54.2 % |
| `util` | 69 / 78 | 88.5 % | 84.9 % |
| `domain` | 16 / 18 | 88.9 % | 75.0 % |
| `model` | 16 / 26 | 61.5 % | – |
| `repos` | 19 / 47 | 40.4 % | 50.0 % |
| `config` | 9 / 42 | 21.4 % | – |
| `security` | 20 / 101 | 19.8 % | **0.0 %** |
| `rest` | 33 / 184 | **17.9 %** | – |
| **Total** | **1026 / 1845** | **55.6 %** | **56.1 %** (192 / 342) |

The total hides the distribution: the calculation logic is reached well through the integration
tests, almost everything around it is not.

| Class | Lines % | Remark |
|---|---:|---|
| `BillingDocumentXlsxService` | 99.0 % | executed, but **not a single assertion** on the content |
| `BillingPdfService` | 93.0 % | executed; the PDF content is never checked |
| `BillingService` | 87.7 % | one scenario with one VAT rate; error paths, second VAT rate, free kWh not covered |
| `ParticipantAmountService` | 63.6 % | only via the preview |
| `BillingConfigService` | 57.3 % | only save/read; images not |
| `BillingDocumentService` | 43.4 % | `findByTenantIdAndYear` never |
| `BillingRunService` | 38.5 % | delete never |
| `InMemoryLockRepository` | 16.0 % | the per-tenant lock never |
| `BillingRunResource` | 14.7 % | no endpoint tested |
| `JwtTokenService` / `JwtRequestFilter` | 12.9 % / 21.1 % | token and tenant check never (which is why `known-errors.md` #1 went unnoticed) |
| `EmailService` / `BillingDocumentMailService` | 11.1 % / 9.4 % | sending mail never |
| `BillingDocumentNumberService` | 8.6 % | |
| `BillingConfigResource` | 8.2 % | |
| `BillingDocumentItemService` | 6.1 % | |
| `RestExceptionHandler` | 3.3 % | error format never |

**Coverage is a signpost here, not a quality measure:** the two best "covered" classes (XLSX, PDF)
are only executed, not verified.

### 2.3 CI

`rolling-release.yml` builds and publishes the image without running tests (`known-errors.md` #6,
`open-points.md` B-2). `snyk.yml` scans only the own code and never fails the build.

## 3. Weaknesses of the existing tests

| # | Weakness | Consequence |
|---|---|---|
| T1 | `contextLoads` and `testContainer` in both integration classes check nothing functional | count as tests, protect nothing |
| T2 | `testBillingXlsxService` has no assertion | 99 % coverage without any statement |
| T3 | The result of `doBilling` (`abstractText`) is never checked | a swallowed error (section 4, F1) goes unnoticed if the partial data happen to fit |
| T4 | Assumptions about the order of items (`get(0)`, `get(1)`) without `ORDER BY` | can flip with the database plan |
| T5 | One fixture (649 lines of SQL), one VAT rate (10 %), one scenario | variants (2 rates, free kWh, discount, producers only, zero document) untested |
| T6 | Allocation amounts via `Double.parseDouble` | rounding errors possible in the test set-up itself |
| T7 | `LocalDate.now()` in tests and code next to fixed 2022/2023 | tests can break at the year change |
| T8 | Commented-out assertions (`numberOfInvoices`), TODOs for archive and XLSX | gaps are known but not tracked |
| T9 | `spring.jpa.show-sql=true` in the test | noisy output, failures drown in it |
| T10 | The id of a participant in `TEST_ALLOCATIONS` has a faulty last group (`…-0c31aa53a49`) | unintentionally tests only the "not found" path |
| T11 | `MassDataGenerator` lives under `src/test` but is a `main()` program, not a test | belongs in `tools/` or `massdatatest/` |
| T12 | `target/` is not cleaned between branches: reports (and compiled classes) of another branch stay | always measure with `mvn clean` |

## 4. Defects found (recorded only, not fixed)

F*n* is in `known-errors.md` as #(*n* + 11), so F1 = #12 … F18 = #29. "Read" = traced in the code;
"suspicion" = plausible but not proven by a run. Each row names the test that should expose the
defect first (section 6).

| # | Severity | Finding | Evidence | Status | Test that shows it |
|---|---|---|---|---|---|
| F1 | high | **`doBilling` catches every exception inside `@Transactional` and returns normally.** Errors from the own code (document date in the future, run already completed, more than two VAT rates, NPE) roll nothing back: documents, items, PDFs and **consumed document numbers** created so far stay saved. Errors from repository proxies instead mark the transaction rollback-only (suspicion: `UnexpectedRollbackException` on commit). | `BillingService.java:59-60, 165-169` | read | Integration test: run with a third VAT rate → no documents, no numbers |
| F2 | high | **Documents with amount 0 are saved without a billing run.** `createBillingDocument` saves the document before the amounts are known; it receives the run only for an amount ≠ 0. `deleteByBillingRunId` never deletes such documents, and `/api/billingDocuments/tenant/{id}/{year}` fails on them with an NPE (`getBillingRun().getId()`). The existing fixture produces such documents. | `BillingService.java:218-226, 349`; `BillingDocumentService.java:134` | read | Integration test: after the run no documents without a run; year list returns 200 |
| F3 | high | **The per-tenant lock does not exclude parallel runs.** `releaseLock` removes the entry while other threads still wait on the old object; a later thread gets a new object and runs in parallel. The lock also expires after 15 minutes even if the run is still going; between `compute` and `get` a `releaseLock` can cause an NPE. | `InMemoryLockRepository.java:29-45`; `BillingResource.java:31-38` | read | Unit test with three threads on the same tenant |
| F4 | medium | **Document numbers are assigned without a lock:** read the highest number, then save. Two simultaneous runs (see F3) fail on the uniqueness constraint. Prefix `" R"` and `"R"` have separate sequences but produce the same formatted number. | `BillingDocumentNumberGeneratorImpl.java:43-48`; `V1_0__init_schema.sql:7` | read | Unit test (prefix); integration test with two threads |
| F5 | medium | **VAT rates are compared with `BigDecimal.equals`**, which respects the scale: 20 and 20.00 count as different rates → two sums or "More than 2 VAT rates". Whether the master-data view delivers different scales is open. | `BillingService.java:598-610` | read (impact: suspicion) | Unit test of the VAT sums with 20 and 20.00 |
| F6 | medium | **Participant fee stores the VAT rate unchecked** (`vatPercent` instead of the null-safe value); if it is empty the summation fails with an NPE that F1 swallows. | `BillingService.java:510, 587` | read | Integration test: tariff without a VAT rate for the fee |
| F7 | medium | **Mail status:** check-then-set without a lock; an exception after "IN PROGRESS" leaves the status stuck and blocks re-sending; "SENT" is set even if every mail failed. | `BillingDocumentMailService.java:106-114, 144` | read | Unit test with a mock `JavaMailSender` that throws |
| F8 | medium | **`PUT /api/billingConfigs/{id}` checks only the tenant in the body**, not the tenant of the stored record. | `BillingConfigResource.java:127-131` | read; fixed on `fix-tenant-claim` (89725e8, not merged) | WebMvc test |
| F9 | medium | **A foreign id gives 500 instead of 403:** `AccessDeniedException` from `validateTenant` ends in `handleThrowable` (500 with the class name); an unknown id gives 404 — so the existence of foreign records is distinguishable. | `RestExceptionHandler.java:43-50` | read | WebMvc test: own / foreign / unknown id |
| F10 | medium | **`GET /{id}/footerImage` requires a file upload** (`@RequestParam MultipartFile`) and is unusable; `GET …/logoImage` without an image → 500. | `BillingConfigResource.java:94, 103-108` | read | WebMvc test |
| F11 | medium | **Deleting a billing run fails** as soon as it has documents (foreign key without cascade) → 500. | `BillingRunService.java:67`; `V1_0__init_schema.sql:8` | read | Integration test |
| F12 | low | **Rounding:** kWh are rounded to 2 places before pricing; VAT is rounded per item and then summed (not per rate on the net sum). To be clarified with the business (`open-points.md` B-13). | `BillingService.java:417, 451, 605` | read | Unit tests with boundary values, after clarification |
| F13 | low | **`ParticipantAmountService`:** amounts of the meter points are negative for producers, the sum however comes from positive gross values. | `ParticipantAmountService.java:50-52` | suspicion | Unit test, after clarification with the frontend |
| F14 | low | **Time zone:** `LocalDate.now()` in the JVM zone determines the document year and so the number sequence; at the year change this depends on the server time zone. | `BillingService.java:70, 274`; `BillingDocumentService.java:42` | suspicion | Test with a fixed clock (needs a code change, phase 4) |
| F15 | low | `BillingConfigService.DEFAULT` is a public, mutable static object handed into every run without a configuration. | `BillingConfigService`; `BillingService.java:83` | suspicion | Unit test: two runs without a configuration |
| F16 | low | Replacing an image deletes the old one before the update; a wrong file type ends as 500. | `BillingConfigService.java:75-88` | read | WebMvc test |
| F17 | low | `ZipOutputStream` and `XSSFWorkbook` not in try-with-resources (in memory, small impact); `handleThrowable` uses `printStackTrace()` instead of a logger. | `BillingDocumentArchiveService.java:50`; `BillingDocumentXlsxService.java:233`; `RestExceptionHandler.java` | read | – |
| F18 | low | `startCleanupTask` of the lock is never called (dead code); many commented-out endpoints. | `InMemoryLockRepository.java:50` | read | – |

## 5. Obstacles to testing

| Obstacle | Where | Workaround without code change | Solution with code change (phase 4) |
|---|---|---|---|
| Calculation logic reachable only through `doBilling` (622 lines, 10 dependencies, private methods) | `BillingService` | Integration tests with fixture variants | Extract the calculation (`BillingCalculator`) as a separate, pure class |
| Time not injectable (`LocalDate.now()`) | service, lock, domain | Tests with relative dates | `java.time.Clock` as a bean |
| Static state (`TenantContext`, `BillingPdfService.defaultReport`, `BillingConfigService.DEFAULT`) | | set in the test, clear in `@AfterEach` | Tenant as request attribute; report as a bean |
| Lock in the controller instead of the service, concrete class instead of interface | `BillingResource` | WebMvc test or unit test of the lock alone | Move the lock into the service, inject `LockRepository` |
| Jasper compiles the template on first use (slow) | `BillingPdfService` | once per test context (Spring context cache) | precompiled `.jasper` |
| Master data from a view of another service | `base.billing_masterdata` | the SQL fixture (`billing_master_data.sql`, loaded per test with `@Sql`) creates a plain **table** of that name, not the view | Contract test against the backend migration (phase 6) |

## 6. Measures in phases

Each phase is its own change with its own review. Phases 1 – 3 need **no** change to production
code, only new tests.

### Phase 0 — Foundation

| Measure | Kind | Decision |
|---|---|---|
| Tests in CI before the image build and on pull requests (`rolling-release.yml` triggers only on push today), with Docker for Testcontainers | CI | `open-points.md` B-2, B-17 |
| **JaCoCo** plugin in the `pom.xml`, report as CI artefact | build, new source | **approved** (B-11) |
| Minimum coverage as a threshold that may only rise (start: today's value per package) | build | B-11 |
| Test-data builders (`BillingMasterdataBuilder`, `AllocationBuilder`) instead of one big SQL fixture per variant | test code | – |
| `show-sql` off in tests; direct assertions (`assertThat(x, is(…))` instead of `boolean` helpers) | test code | – |
| Always measure with `mvn clean` (T12) | docs | – |

### Phase 1 — Cheap unit tests (no Spring, no Docker)

| Target | What is checked |
|---|---|
| `BigDecimalTools` | `makeZeroIfNull`, `isNullOrZero`, `makeGermanString` (rounding mode, thousands separator, unit) |
| `StringTools.nullSafeJoin` | null, empty, mixed |
| `ClearingPeriodIdentifierTool` | additionally the production form `Abr_YQ-2023-3` |
| `BillingDocumentNumberGeneratorImpl` (repository mocked) | digit limit, prefix null/empty/with blank (F4), start value |
| `InMemoryLockRepository` | lock, release, expiry; F3 with three threads (red until fixed) |
| `EmailService` (`JavaMailSender` mocked) | rejected addresses, embedded image vs. attachment |
| `BillingDocumentMailService` (mocks) | status sequence, failure while sending (F7) |
| `ParticipantAmountService` | producer/consumer, participant fee (F13 after clarification) |
| `BillingDocument.getDocumentTypeName` | all document types |

### Phase 2 — Web layer (`@WebMvcTest` with the real security configuration)

One test class per resource, with `JwtSecurityConfig`, `JwtRequestFilter` and `TenantFilter` (the
`dev` profile and `DevSecurityConfig` are never used). Mandatory matrix for **every** endpoint (31
mapped handlers under `/api/**`, counted 2026-10-01; `GET /` of `HomeController` is public and
excluded). A case applies only where it makes sense: "unknown id" for the 25 endpoints that look up
a record, "invalid body" for the 3 with a validated body:

| Case | Expectation |
|---|---|
| no token | 403 today (`JwtSecurityConfig` sets no authentication entry point; to be confirmed by the first test; 401 would be a contract change, `open-points.md` B-14) |
| token without role `EEG_ADMIN` | 403 |
| own tenant | 200 / 201 / 204 |
| foreign tenant in header or record | 403 (today 500, F9) |
| missing `Tenant` header | 403 (today 500, F9) |
| unknown id | 404 |
| invalid body | 400 with `fieldErrors` |

Plus F8, F10, F16 and the error format of `RestExceptionHandler`. The tests for the tenant check
already exist on `fix-tenant-claim` (`JwtRequestFilterTests`, `JwtTokenServiceTests`,
`BillingConfigResourceTests`); they arrive with the merge.

### Phase 3 — Billing scenarios (integration, Testcontainers)

One scenario matrix, each scenario with a small data set built by the phase-0 builders. Checked are
document types, net/VAT/gross per document, sums per rate, document numbers, run status **and** the
result text.

| # | Scenario | Covers |
|---|---|---|
| S1 | consumers only, one VAT rate | base case |
| S2 | consumers and producers, reverse charge vs. info credit note | choice of document type |
| S3 | two VAT rates (10 % / 20 %) | second VAT sum |
| S4 | three VAT rates | error path; **F1** (nothing may stay saved) |
| S5 | same rates with different scale (20 / 20.00) | **F5** |
| S6 | free kWh larger than, equal to, smaller than the consumption | free kWh |
| S7 | discount, participant fee, meter-point fee, fee without a VAT rate | **F6** |
| S8 | participant with amount 0 | **F2** |
| S9 | document date in the future; run already completed | error paths, **F1** |
| S10 | preview, then final billing, then preview again | deleting old documents, numbers only when final |
| S11 | rounding boundaries (x.xx5 €, very small quantities) | **F12**, after business clarification |
| S12 | delete a run after final billing | **F11** |

Outputs: extract PDF text with **PDFBox** and check the key values (**approved**, B-12); read the XLSX
with POI and check sums (T2).

### Phase 4 — Refactoring for testability (code change, own decision)

**Status: blocked and optional (decision 2026-10-01).** Phase 4 is postponed; phases 0 – 3, 5 and 6
do not depend on it. What stays out of reach without it is listed in section 7.1.

- Extract the calculation from `BillingService` into a pure class (items, VAT sums, document type);
  afterwards scenarios S3 – S7, S11 additionally as fast unit tests.
- `Clock` as a bean (F14, T7); move the lock into the service and behind the interface (F3).
- This brings `BillingService` below the size limit (`open-points.md` B-6).

Effort: section 7.

### Phase 5 — Concurrency and robustness

- Two simultaneous runs of the same tenant (F3, F4); two runs of different tenants in parallel
  (must not block each other).
- Mail sending with partly invalid addresses and an SMTP error (F7) against a test SMTP server
  (**GreenMail**, **approved**, B-12).

### Phase 6 — Contracts with the neighbours

- **Master-data view:** a test that builds the view `base.billing_masterdata` from the migrations of
  `eegfaktura-backend` and checks that every column `BillingMasterdata` reads exists
  (`known-errors.md` #10).
- **Callers:** the DTOs that `eegfaktura-web` and eegfaktura-v3 send, as JSON fixtures; one test per
  endpoint that they are accepted.

## 7. Effort of phase 4

The effort is stated for the AI-assisted work in this repository (implementation, tests and
verification by the agent; **review and decisions by the maintainer are extra** and usually the
limiting factor). It assumes phases 0 – 3 are done, because the refactoring is only safe with the
scenario tests as a safety net: they pin the amounts of today's behaviour, so every extraction can
be checked by "all scenarios still green".

| Step | Content | Size | Effort |
|---|---|---|---|
| 4a | **`Clock` bean.** 8 calls of `LocalDate.now()` / `LocalDateTime.now()` replaced; tests get a fixed clock; year-change tests for F14 | small, mechanical | 0.5 day |
| 4b | **Lock into the service, via interface.** `BillingResource` loses the lock logic; `BillingService` (or a thin `BillingRunCoordinator`) takes `LockRepository`; fix F3 (release only own entry, no expiry during a run) and F18 | small–medium, behaviour changes | 1 day |
| 4c | **Extract `BillingCalculator`.** Move item creation (`createBillingDocumentItem`, `createCustomBillingDocumentItem`, `createMeteringPointFeeDocumentItem`, ~200 lines) and `calculateGrossValues` (~45 lines, VAT sums, the F5/F6/F12 spots) into a pure class without repositories; `BillingService` keeps orchestration, persistence and numbering | medium; the risky part, protected by S1 – S12 | 2 – 3 days |
| 4d | **Fix F1 / F2 on top.** Narrow the `catch`, let the transaction roll back, assign the run before the first save. Strictly speaking a bug fix, not a refactoring; it is listed here because the new structure makes it a few lines | small, but the behaviour (failed run = nothing saved) must be agreed | 0.5 – 1 day |
| 4e | **Unit tests of the calculator** (S3 – S7, S11 as fast tests with plain objects) and size check (`loc-check.sh`) | small | 1 day |
| | **Total** | | **5 – 6.5 working days** |

Optional and not included: splitting `BillingService` further into run / documents / numbering as
proposed in B-6 (+1 – 2 days), and the Tenant-as-request-attribute and precompiled-`.jasper`
changes from section 5 (+1 day each).

Risks that move the figure:

- **Business questions** (rounding F12/B-13, producer sign F13) are not part of the effort. If the
  calculator is extracted before they are answered, it keeps today's behaviour and the tests are
  marked as such.
- **Behaviour changes in 4b/4d** (a failed run now leaves nothing behind) are visible to callers;
  they need the maintainer's agreement and a note in `CHANGELOG.md`.
- Without phase 3 the effort is not smaller but the risk is much higher; the order 3 → 4 should
  not be swapped.

### 7.1 What cannot be done without phase 4

Everything in phases 0 – 3, 5 and 6 works without the extraction. Limited or out of reach:

| Item | Without phase 4 | Consequence |
|---|---|---|
| Fast unit tests of the calculation (S3 – S7, S11) | only as integration tests (Spring + PostgreSQL); the context is cached, so it works, but each scenario needs a DB fixture and takes seconds instead of milliseconds | slower feedback; fine-grained rounding tables (F12) are cumbersome |
| PIT on the calculation | PIT re-runs the tests per mutant; with Testcontainers tests on `BillingService` this is impractical | PIT only for `util`, `ParticipantAmountService`, the number generator; **no mutation score for the core calculation** |
| Deterministic year-change tests (F14, T7) | no injectable clock; only relative dates, or a fragile `mockStatic(LocalDate.class)` | the year-change behaviour stays unproven; F14 remains a suspicion |
| Coverage targets of the "after phase 4" column (≥ 85 % lines, ≥ 80 % branches) | not reachable; realistic ceiling is the phase-3 column (≥ 75 % / ≥ 70 %) | targets are capped at phase 3 |
| Size limit of `BillingService` (B-6, 622 lines) | stays red | accepted until phase 4 |
| Tests for F3 (lock) | the class `InMemoryLockRepository` is testable alone (phase 1); the lock in the controller only by calling the resource bean (M5) or via WebMvc | fixing F3 is possible without extraction, but the lock stays in the controller |
| Static state (`TenantContext`, `DEFAULT`, `defaultReport`) | set and cleared in the test | works, but tests must not run in parallel |

Not affected: all tenant/endpoint tests, the document-number tests, the PDF/XLSX checks, mail with
GreenMail, concurrency tests of two runs (F3/F4) and the contract tests. The defects F1, F2, F5,
F6, F11 can be exposed by integration tests and fixed in the existing structure; only the clean
layout of the fix needs phase 4.

## 8. Targets and metrics

| Metric | today | after phase 2 | after phase 3 | after phase 4 (blocked, optional) |
|---|---:|---:|---:|---:|
| Lines total | 55.6 % | ≥ 65 % | ≥ 75 % | ≥ 85 % |
| Branches total | 56.1 % | ≥ 60 % | ≥ 70 % | ≥ 80 % |
| `rest` lines | 17.9 % | ≥ 85 % | ≥ 85 % | ≥ 90 % |
| `security` branches | 0 % | 100 % | 100 % | 100 % |
| Endpoints with the tenant matrix | 0 of 31 | all | all | all |
| Billing scenarios | 1 | 1 | 12 | 12 + unit |
| Tests in CI | no | yes | yes | yes |
| Mutation score of the calculator (PIT) | – | – | – | measured, then a threshold |

The percentages are estimates, not derived from the code: **targets to be confirmed by measurement**
at the end of each milestone (the milestone report states the measured value; a target is lowered
with a reason, never met by excluding code). They are guide values, not goals in themselves: a test
without a functional assertion does not count (T1, T2). Defect tests are `@Disabled("known-errors
#NN")` until the fix (one convention, `open-points.md` B-18).

### Mutation tests (PIT) — approved

Mutation testing measures whether the tests notice a change in the code, which coverage cannot.
Scope: **only** pure classes — after phase 4 the `BillingCalculator`; until then `util`,
`ParticipantAmountService` and the number generator — not the whole service, because PIT runs the tests once per mutant and
the Testcontainers tests are too slow for that. The unit tests of phase 4e are the input. PIT runs
as a separate Maven profile / manual CI job, not on every push; the first run only reports, a
threshold is set after the first result (see "measure, then move on").

## 9. Licences of the new test tools

The service is AGPL-3.0; AGENTS.md §12 requires a licence that allows redistribution and is
compatible with it. All four tools are used **only in the build and the tests** and are not part of
the shipped jar or image. Licences read from the POMs on Maven Central (2026-10-01); the versions
are the newest ones and have to be at least 7 days old when added (AGENTS.md §12.1):

| Tool | Artifact | Newest version (Central) | Licence | Compatible with AGPL-3.0 |
|---|---|---|---|---|
| JaCoCo | `org.jacoco:jacoco-maven-plugin` | 0.8.13 (2025-04-02) | EPL-2.0 | yes as a build tool; it is not linked into the product (its agent only instruments the test JVM) |
| PDFBox | `org.apache.pdfbox:pdfbox` | 3.0.8 (2026-07-15) | Apache-2.0 | yes (Apache-2.0 is one-way compatible with GPL-3.0 / AGPL-3.0) |
| GreenMail | `com.icegreen:greenmail-junit5` | 2.1.14 (2026-09-19) | Apache-2.0 | yes |
| PIT | `org.pitest:pitest-maven` + `pitest-junit5-plugin` | 1.30.0 (2026-08-27) / 1.2.3 | Apache-2.0 | yes; build plugin only |

PIT is therefore **compatible**. Each tool is added to `EXTERNAL_SOURCES.md` in the change that
introduces it. Exact versions are re-checked then.

## 10. Appendix

### 10.1 Reproduce the measurement

Without changing the `pom.xml`, JDK 21 in the builder image:

```bash
docker run --rm -v "$PWD":/src -w /src -v ~/.m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --add-host=host.docker.internal:host-gateway \
  maven:3-eclipse-temurin-21 \
  mvn -B clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report
# report: target/site/jacoco/index.html, raw data target/site/jacoco/jacoco.csv
```

JaCoCo 0.8.13 was released on 2025-04-02 (Maven Central), licence EPL-2.0. It was loaded only for
this measurement and is not part of the build (`open-points.md` B-11).

### 10.2 Sources

Code review of `src/main` and `src/test` on 2026-10-01; the security review of the same day
(`known-errors.md` #1); `eegfaktura-analyze-it/state-of-testing.md` (2026-09-01).
