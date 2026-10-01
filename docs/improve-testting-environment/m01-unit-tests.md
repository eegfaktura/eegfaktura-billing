# M1 — Cheap unit tests

**Concept:** phase 1 · **Status:** open · **Production code:** none
**Depends on:** M0 (builders, JaCoCo, CI); B-18 (decided: plain `@Disabled`). **Effort:** about 2 days.

## Goal

Fast tests (no Spring, no Docker, milliseconds) for helpers and services that today have none, and
the first red tests that expose defects without touching production code. Tests are written once,
at the end of the step (AGENTS.md §10.3), against the shape that survived.

## Scope — one test class per target

Package `org.vfeeg.eegfaktura.billing` + the sub-package of the class under test (`util`, `repos`,
`service`, `domain`).

| Target | Test class | What is checked | Defect |
|---|---|---|---|
| `BigDecimalTools` | `util.BigDecimalToolsTests` | `makeZeroIfNull`, `isNullOrZero`, both `makeGermanString` overloads (rounding mode, thousands separator, unit, negative, zero, null) | – |
| `StringTools.nullSafeJoin` | `util.StringToolsTests` | null, empty, mixed | – |
| `ClearingPeriodIdentifierTool` | extend `ClearingPeriodIdentifierToolTests` | production form `Abr_YQ-2023-3` | – |
| `BillingDocumentNumberGeneratorImpl.getNext` (`BillingDocumentNumberRepository` mocked) | `repos.BillingDocumentNumberGeneratorTests` | length outside 1 – 10 falls back to 5, prefix null / empty / with blank, start null → 0, next = max + 1, formatted number | F4 (prefix `" R"` vs `"R"`) |
| `InMemoryLockRepository` | `repos.InMemoryLockRepositoryTests` | same tenant returns the same lock, two tenants two locks, release removes; three threads on one tenant using the controller's pattern (`getLock` → `synchronized` → `releaseLock`) must never overlap | F3 |
| `EmailService` (`JavaMailSender` mocked) | `service.EmailServiceTests` | rejected addresses returned, embedded image vs. attachment | – |
| `BillingDocumentMailService.sendAllBillingDocuments` (mocks) | `service.BillingDocumentMailServiceTests` | status sequence, status after a throwing sender | F7 |
| `ParticipantAmountService` (repositories mocked) | `service.ParticipantAmountServiceTests` | consumer amounts and participant fee only; one-line comment: producer sign not tested, accepted, known-errors #24 | – |
| `BillingDocument.getDocumentTypeName` (static) | `domain.BillingDocumentTests` | every `BillingDocumentType` | – |

Not testable in M1 without a code change: the 15-minute **expiry** of the lock
(`EXPIRATION_MINUTES` is a private constant, no clock) and `startCleanupTask` (private, never
called, F18) — both wait for M4 (4a/4b); no reflection hacks.

## Rules for defect tests

A test that shows a defect asserts the **correct** behaviour. Because production code may not
change in this milestone it is annotated `@Disabled("known-errors #NN")` (the one convention of all
milestones, README "Rules"; JUnit 5 needs no setup for it) and listed in the milestone report; it
is enabled in the change that fixes the defect (AGENTS.md §10.1; B-18 records the skip as the
documented exception to §15).
Here: F3 = #14, F4 = #15, F7 = #18. F13 (#24) is not tested (accepted, not changed in billing; B-20 decided).
Everything else is expected green.

## Tasks

- [ ] Write the classes above (builders from M0 where useful)
- [ ] Disabled-with-reason tests for F3, F4, F7; list them in `AGENT_LOG.md`
- [ ] Run each class alone (`./mvnw test -Dtest='<Name>Tests'`), then the full suite once
- [ ] Raise the JaCoCo thresholds to the new floor (rounded down)
- [ ] `AGENT_LOG.md`; `known-errors.md` if a new defect appears

## Acceptance criteria

- All new tests are green or `@Disabled("known-errors #NN")`; none without a functional assertion; exactly 3 disabled tests (F3, F4, F7) unless a new defect is added to `known-errors.md`; `grep -rn '@Disabled' src/test | grep -v 'known-errors #[0-9]'` prints nothing.
- Line coverage in the JaCoCo CSV of the clean full run (**targets, confirm by measurement; lower them in the report with a reason if a branch needs production code**): `BigDecimalTools`, `StringTools` ≥ 95 %; `InMemoryLockRepository`, `EmailService`, `BillingDocumentMailService`, `BillingDocumentNumberGeneratorImpl` ≥ 80 % (the lock stays below 100 % because of F18/expiry).
- The new unit classes together run in under 10 seconds without a Spring context (`mvn` surefire report).
- `git diff --stat src/main pom.xml` is empty.

## Risks

- The threaded F3 test can be flaky → `CountDownLatch`, no sleeps (AGENTS.md §10.1); it stays disabled until the fix, so it cannot redden the suite.
- Mocking `JavaMailSender` proves interactions only; assertions stay on status and arguments, GreenMail covers real SMTP in M5.
- The mocked number generator cannot prove uniqueness under concurrency (F4 integration part is M5).
