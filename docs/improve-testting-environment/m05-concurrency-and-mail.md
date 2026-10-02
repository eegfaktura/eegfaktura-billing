# M5 — Concurrency and mail

**Concept:** phase 5 · **Status:** open · **Production code:** none
**Depends on:** M0, M1 (lock and mail unit tests), M3 (scenario base class `support/BillingScenarioBase` and builders for run
data); GreenMail approved (`open-points.md` B-12). Independent of M2. **Effort:** 2 – 3 days.

## Goal

Prove behaviour under parallel runs and with a real SMTP server, which unit tests with mocks cannot.

## Scope

The lock sits in the controllers, not in the service: `BillingResource.getAllInvoices` and, in
`BillingRunResource`, the xlsx, archive, sendmail and delete handlers take
`InMemoryLockRepository.getLock(tenant)`, `synchronized`, `releaseLock` (5 handlers, verified). Tests therefore call the **resource bean**
from a `@SpringBootTest` (M3's base class on `PostgresContainerHolder`; the GreenMail port makes the context differ from M3's, so one more context is built) with `TenantContext` set in each thread;
no HTTP and no tokens needed. These tests are **not** `@Transactional` (the threads need their own
transactions) and clean up in `@AfterEach`; each test uses its own tenant id.

1. **Concurrent runs, same tenant (F3 = #14, F4 = #15).** Deterministic with a latch: a
   `@MockitoSpyBean` on `BillingService` blocks inside `doBilling`. Run A holds the lock; B waits;
   A finishes (`releaseLock` removes the entry); B enters and is held; run D now starts and, with
   the defect, gets a *new* lock object and enters **concurrently** → the test asserts that at most
   one run is inside `doBilling` at any time (correct behaviour; red today). Second part: two real
   runs back to back on one tenant produce distinct document numbers and no unique-constraint error
   at the caller.
2. **Concurrent runs, different tenants:** the second run finishes while the first is held (must not
   block). Expected green today.
3. **Mail against GreenMail** (`greenmail-junit5`, test scope, `ServerSetupTest.SMTP` on a dynamic
   port set via `@DynamicPropertySource` for `spring.mail.host/port`; the test properties demand
   SMTP auth, so create a GreenMail user): successful send of a document with PDF attachment
   (content type, file name, recipients, embedded `attachment-logo.png`); partly invalid addresses
   (valid ones delivered, invalid ones reported in the protocol); SMTP failure mid-batch (F7 = #18:
   status must not stay at "IN PROGRESS", "SENT" not set if every mail failed).
4. **Mail status races (F7):** two simultaneous `BillingDocumentMailService.sendAllBillingDocuments`
   calls for one run, called on the **service** (through the resource the per-tenant lock serialises
   them and hides the race); only one may send.

## Defect tests

The tests in 1 (part one), 3 (SMTP failure) and 4 assert the correct behaviour and are
`@Disabled("known-errors #14")` / `#15` / `#18` until fixed; enable them with the fix. Different
tenants and the mail happy path / invalid-address tests are expected green.

## Tasks

- [ ] `com.icegreen:greenmail-junit5` 2.1.14 (test scope, exact, ≥ 7 days old, Apache-2.0), `EXTERNAL_SOURCES.md` row
- [ ] Harness: executor, latches, `assertTimeoutPreemptively`, tenant-per-test cleanup
- [ ] Concurrent-runs tests (same / different tenant)
- [ ] Mail tests (success, invalid addresses, SMTP down, status, race)
- [ ] Run the new classes 20 times locally once (`for i in $(seq 20); do ./mvnw -q test -Dtest='…'; done`); record the result
- [ ] Full suite once; raise thresholds; `AGENT_LOG.md`; `known-errors.md` if a new defect appears

## Acceptance criteria

- Different-tenant and mail happy-path tests are green; every disabled test carries a `known-errors.md` number (#14, #15, #18).
- `grep -rn 'Thread.sleep' <new test files>` finds nothing; every wait has a timeout and a failure message.
- 20 consecutive runs of the new classes: 0 failures (including the disabled ones being skipped consistently).
- JaCoCo CSV of the clean full run: `EmailService` and `BillingDocumentMailService` ≥ 85 % lines (target, confirm by measurement).
- `git diff --stat src/main` is empty; only `pom.xml` gains GreenMail.

## Risks

- Flaky concurrency tests destroy trust; latches only, or exclude with a clearly named JUnit tag and say so in the log.
- GreenMail port collisions in CI → dynamic ports; Jakarta Mail version of Spring Boot 3.5 against GreenMail 2.1.x must be checked once.
- If M4 4b moves the lock into the service, these tests must be rewired to the service entry point.
- The spy approach needs the real `BillingService` bean to stay the proxy target; if `@MockitoSpyBean` conflicts with `@Transactional` proxying, fall back to a latch in a test-only `BillingMasterdataRepository` wrapper.
