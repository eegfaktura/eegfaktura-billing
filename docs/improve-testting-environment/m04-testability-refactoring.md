# M4 — Refactoring for testability (blocked, optional)

**Concept:** phase 4 · **Status:** **blocked and optional** — postponed on 2026-10-01
**Production code:** **yes** — the only milestone that changes production code.
**Depends on:** M3 complete (the scenarios are the safety net); M5 recommended before 4b (the
concurrency tests prove the lock fix); B-21 (decided 2026-10-01: **not now**, M4 stays blocked); the maintainer's explicit go. Nothing else in the plan depends
on this milestone. **Effort:** 5 – 6.5 working days (AI-assisted work; maintainer review extra,
concept §7).

## Goal

Make the calculation testable without Spring and a database, inject time, move the lock out of the
controller, and bring `BillingService` (622 lines) under the size limit (`open-points.md` B-6).

## Scope (when unblocked)

| Step | Content | Effort |
|---|---|---|
| 4a | `java.time.Clock` bean; replace the 8 `LocalDate.now()` / `LocalDateTime.now()` calls (`BillingService` 4, `BillingDocumentService` 1, `BillingDocumentMailService` 1, `InMemoryLockRepository` 2); fixed clock in tests (F14 = #25, T7); the 3 `OffsetDateTime.now()` calls (`DomainConfig` auditing, `BillingDocumentMailService` 2) only if cheap | 0.5 day |
| 4b | Lock into the service behind `LockRepository`: `BillingResource` and `BillingRunResource.sendAllBillingDocuments` lose the lock logic; fix F3 = #14 (release only the own entry, no expiry during a run), remove dead code F18 = #29; expiry becomes testable through the clock | 1 day |
| 4c | Extract `BillingCalculator` (items, VAT sums, document type) from `BillingService`: `createBillingDocumentItem`, `createCustomBillingDocumentItem`, `createMeteringPointFeeDocumentItem`, `calculateGrossValues`; no repositories in it | 2 – 3 days |
| 4d | Fix F1 = #12 / F2 = #13 on the new structure (narrow the `catch`, roll back, assign the run before the first save) | 0.5 – 1 day |
| 4e | Unit tests of the calculator (S3 – S7, S11 as fast tests), PIT first run, `loc-check` | 1 day |

Optional, not included: further split of `BillingService` (B-6, +1 – 2 days), Tenant as request
attribute and precompiled `.jasper` (+1 day each; concept §5, §7).

## What stays out of reach while blocked

Fast calculation unit tests, a PIT score for the calculation, deterministic year-change tests
(F14), coverage above the M3 targets (≥ 85 % lines / ≥ 80 % branches), the size limit of
`BillingService`, lock expiry tests. See concept §7.1.

## Entry conditions (all must hold before work starts)

- [ ] M3 done: S1 – S12 green or disabled-with-reason, thresholds raised; M5 done or consciously skipped
- [ ] Answers to B-13 (rounding) and B-20 (producer sign, F13 = #24; a 2-line fix needs no M4 — see the proposal in `open-points.md`), or the decision to keep today's behaviour and mark the tests as such
- [ ] B-21: decided "not now" on 2026-10-01; a new go for M4 itself is needed
- [ ] Agreement that a failed run leaves nothing behind (4d) — visible to callers; `CHANGELOG.md` note
- [ ] Migration concept check (AGENTS.md §8.1): no schema change expected; if one appears, stop and write `docs/migrations/V1_<N>-<slug>.md` first
- [ ] PIT (`pitest-maven` 1.30.0, `pitest-junit5-plugin` 1.2.3, Apache-2.0, approved B-12) added to the `pom.xml` in 4e with exact versions ≥ 7 days old and an `EXTERNAL_SOURCES.md` row

## Working rules

- Small steps; after **each** step `./mvnw test` stays green with unchanged expected amounts. A changed expected amount in a pure refactoring step is a stop signal.
- Pure move first (no behaviour change), fixes second, in separate commits. New tests (4e) are written at the end of the step, against the surviving shape (AGENTS.md §10.3).
- Enable the disabled defect tests of F1, F2, F3, F14 in the commit that fixes them; update M5's concurrency tests if the lock moves (they go through the resource today).
- Each step updates `known-errors.md` (fixed on date), `CHANGELOG.md` and `AGENT_LOG.md`.

## Acceptance criteria

- `grep -rE 'import org.springframework|Repository' src/main/.../BillingCalculator.java` finds nothing; its unit tests run in < 1 s together.
- `bash scripts/dev/loc-check.sh` shows `BillingService.java` ≤ 450 lines (target ≤ 300); B-6 updated.
- `grep -rn 'LocalDate.now()\|LocalDateTime.now()' src/main` finds nothing outside the clock bean.
- JaCoCo CSV of the clean full run (targets from concept §8, confirm by measurement): total lines ≥ 85 %, branches ≥ 80 %; PIT first run on the calculator reported (threshold set afterwards).
- All M3 scenarios unchanged and green; the previously disabled F1, F2, F3 tests enabled and green.

## Risks

See concept §7: business questions, visible behaviour changes in 4b/4d, order 3 → 4 must not be swapped. Additionally: Spring bean wiring changes (4b) can break the `@WebMvcTest` slices of M2 — rerun the full suite after 4b.
