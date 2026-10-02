# Test-coverage milestones

Milestones of [test-coverage-concept.md](test-coverage-concept.md), one file each. The numbers
equal the phases of the concept. Order: M0 → M1 → M2 → M3 → M5 → M6 (M2, M3 and M6 only need M0 and
may run in parallel; M5 needs M1 and M3's base class; M6 is independent of M3); M4 is **blocked and
optional** and will be done on the zvt branch `feat/zvt-time-tariff`, not here (2026-10-02). M0 also provides the shared `PostgresContainerHolder` that M3 and M5 use. Effort in total
(M0 – M3, M5, M6): about 14 – 17.5 working days AI-assisted, maintainer review extra (M0 +0.25 for the second baseline run, M6 research shorter because v3 hosts the SQL: net unchanged); M4 adds 5 – 6.5; the optional v3 items in M3 add 1 – 1.5.

| Milestone | Title | Production code changed? | Needs | Status |
|---|---|---|---|---|
| [M0](m00-foundation.md) | Foundation: CI, JaCoCo, builders | `pom.xml`, CI, `lombok.config` only | B-2, B-11, B-17, B-19 (all decided) | done (CI run not verified) |
| [M1](m01-unit-tests.md) | Cheap unit tests | no | M0 | done (2026-10-02) |
| [M2](m02-web-layer.md) | Web layer and tenant matrix (31 endpoints) | no | M0; B-14 decided (403); builds on `master` without the `fix-tenant-claim` merge (B-15 decided: no) | done (2026-10-02) |
| [M3](m03-billing-scenarios.md) | Billing scenarios S1 – S12 | no | M0, PDFBox (approved); S11 waits for B-13; optional v3 snapshot/oracle (B-16 decided) | done (2026-10-02; S11 waits for B-13; oracle done, snapshot not) |
| [M4](m04-testability-refactoring.md) | Refactoring for testability | yes | M3 complete, B-21 (decided: not now), B-13 (rounding only) | **blocked, optional**; done on the zvt branch `feat/zvt-time-tariff` (2026-10-02) |
| [M5](m05-concurrency-and-mail.md) | Concurrency and mail | no | M0, M1, M3 (base class), GreenMail (approved) | done (2026-10-02) |
| [M6](m06-contracts.md) | Contracts with the neighbours | no | M0, v3 SQL copy (B-16 decided: v3, AGPL), read access to v3 and web | open |

## Where each measure of the concept lives

| Concept item | Milestone | Note |
|---|---|---|
| F1 (#12), F2 (#13), F5 (#16), F6 (#17), F11 (#22), F12 (#23), F15 (#26) | M3 | scenarios S4/S9, S8, S5, S7, S12, S11, extra test; fixes in M4d or separate changes |
| F3 (#14), F4 (#15), F7 (#18) | M1 (unit) and M5 (integration) | disabled until fixed; F3 fix in M4b |
| F8 (#19), F9 (#20), F10 (#21), F16 (#27) | M2 | |
| F13 (#24) | not tested; accepted, known-errors #24 | B-20 decided: no change in billing |
| F14 (#25) | M4a (needs a `Clock`) | no test before M4 |
| F17 (#28), F18 (#29) | recorded only | F18 removed in M4b |
| S1 – S12 | M3 | |
| T1, T2, T3, T4, T7, T8, T10 | M3 | T5 builders in M0, variants in M3 |
| T6, T9, T12 | M0 | |
| T11 (`MassDataGenerator`) | none — own small change | `open-points.md` B-6 |

## Decisions needed

All are in `open-points.md`. Status as of 2026-10-01 after the maintainer's answers.

| Id | Question | Needed before | Status | Answer / recommended default |
|---|---|---|---|---|
| B-13 | Rounding: kWh before pricing, VAT per line or per rate (F12) | S11 in M3, M4 | **open, unanswered** | S11 has no test until answered; today's rounding is in known-errors #23 as "open, business question" |
| B-14 | No token: keep 403 or add an entry point for 401 | M2 | **decided: as it is (2026-10-01)** | billing keeps 403; the matrix pins it as a normal test |
| B-15 | Merge `fix-tenant-claim` (89725e8) before M2? | M2 | **decided: no** | M2 builds on `master`; rows failing on #1/#19 are `@Disabled` defect tests, enabled by the later merge |
| B-16 | Source of view SQL and test data | M3 (optional), M6 | **decided: use v3 generator/data, AGPL** | SQL copied from `eegfaktura-v3/docker/legacy-base/` with commit id; M3 optional snapshot and `BillingRules.kt` oracle |
| B-17 | CI runner and trigger | M0 | **decided: yes** | hosted `ubuntu-latest`, reusable `test.yml` on `pull_request` + `workflow_call` |
| B-18 | Defect tests as plain `@Disabled("known-errors #NN")` (exception to AGENTS.md §15) | M1 | **decided: yes** | grep check below |
| B-19 | `lombok.config` (`addLombokGeneratedAnnotation = true`) | M0 baseline | **decided: yes** | added in M0 before the baseline; baseline measured with and without, thresholds from the new one, targets re-confirmed |
| B-20 | Producer sign in `ParticipantAmountService` (F13) | – | **decided: no change in billing (2026-10-01)** | recorded in known-errors #24 and v3 #74; not fixed, not tested; consumers must cope |
| B-22 | Does the web total include meter-point fees (`meteringPointFeeSum`)? (unverified) | – | **open** | no billing change, no test; check with a member with a meter-point fee; a fix is in eegfaktura-web |
| B-21 | Go for M4 | M4 | **decided: not now** | M4 stays blocked and optional |

## Rules that apply to every milestone

- **No code change in M0 – M3, M5, M6** except `pom.xml`, CI and test code. A defect found by a test
  is recorded in `known-errors.md` and fixed in its own change (AGENTS.md §10.1). A test that would
  bless wrong behaviour is not written; it is added as a disabled test with the `known-errors.md`
  number in the reason, or waits for the fix. One convention everywhere: plain
  `@Disabled("known-errors #NN")` (JUnit 5, no setup, shows as skipped in surefire). Check:
  `grep -rn '@Disabled' src/test | grep -v 'known-errors #[0-9]'` prints nothing. A tag would need
  surefire configuration in the `pom.xml` and hides the defect from the report; not used. Each
  disabled test is run once enabled locally and its red result recorded before it is committed.
- **Order inside a milestone:** build everything first, write the tests once at the end of the step,
  run the long suites once at the end (AGENTS.md §10.3). While iterating: one class, `./mvnw test -Dtest='<Name>Tests'`. The whole suite
  once per milestone.
- **Dependencies:** exact versions, nothing younger than 7 days, licence read from the POM,
  row in `EXTERNAL_SOURCES.md` in the same change (AGENTS.md §12).
- **Size limits:** no new file above 300 lines if avoidable, never above 450 (AGENTS.md §13);
  check with `bash scripts/dev/loc-check.sh`. Split test classes by area.
- **Tracking:** every milestone updates `AGENT_LOG.md`; defects go to `known-errors.md`; open
  questions to `open-points.md`.
- **Measure with `mvn clean`** (T12). Coverage figures in a milestone report come from the JaCoCo
  report of the clean full run.
- **No mass-data runs** (`massdatatest/`) unless the user says so.
- Each milestone is its own change with its own review and is committed when green.

## Review log

Pass 1: 2026-10-01
- Endpoint count verified in the code: 31 mapped `/api/**` handlers (8+11+3+3+2+2+1+1; `GET /` is public, excluded); the 31 stands. Split added: 25 by id, 4 tenant in path, 2 tenant in body, 3 validated bodies.
- Security names corrected: there is no `SecurityConfig`; the classes are `JwtSecurityConfig` (profile `!dev`), `DevSecurityConfig`, `JwtRequestFilter`, `TenantFilter`, `JwtTokenService` (reads an X.509 certificate, claims `tenant`, `access_groups`, `preferred_username`).
- "No token → 401" was wrong: no entry point is configured, so 403 today; matrix fixed in M2 and the concept, new `open-points.md` B-14 (401 is a contract decision).
- "Missing `Tenant` header → 403" is 500 today (F9); marked in M2 and the concept. Matrix cases now apply only where meaningful (404 for the 25 id endpoints, 400 for the 3 validated bodies).
- `base.billing_masterdata` is a plain **table** created by `billing_master_data.sql` (`@Sql`), not a view: corrected in the concept §5, M0, M3, M6; M6 now names the real backend view migrations and their base-schema prerequisites and keeps the contract test out of the M3 database.
- The lock sits in two controllers (`BillingResource`, `BillingRunResource.sendAllBillingDocuments`); M5 tests call the resource beans directly (no tokens, no M2 dependency) with a deterministic latch design; M5 now depends on M3's base class.
- M3: scenarios with F1/F2/F11 must not run in the `@Transactional` test transaction (rollback would hide the defect); `sNN_` method names make "12 scenarios" checkable by grep.
- M1: lock expiry (private constant, no clock) and `startCleanupTask` (private) are not testable without code change, moved to M4; unit-test targets given with real method names.
- Defect numbers checked against `known-errors.md` (F1=#12 … F18=#29): all `@Disabled(known-errors #NN)` references are correct; the numbers are now spelled out per scenario/test.
- F15 (#26) was assigned to no milestone: now an extra test in M3; the README has a mapping table for all F, S, T items (nothing lost; T11 stays a separate change, F14 only in M4a, F17/F18 recorded only).
- M0: coverage gate bound to `verify` (not to `./mvnw test`), new CI actions pinned by SHA while the old floating tags stay under B-3, `CHANGELOG.md` entry, jar-diff and red-branch acceptance checks, `@{argLine}`; builders insert into the table via `JdbcTemplate`.
- M2: concrete slice set-up (`@Import` of `JwtSecurityConfig`, `JwtTokenService`, `AppProperties`, real `InMemoryLockRepository`; `@MockitoBean` services), test key pair/certificate, guard test against `RequestMappingHandlerMapping`, class naming that avoids the clash with the branch's `BillingConfigResourceTests`.
- M4: added tasks-style entry conditions (PIT versions, migration check), per-step call counts (8 `now()` calls verified), acceptance checks by grep/`loc-check`; M5 rewiring noted.
- Tracking files: `known-errors.md` and `open-points.md` pointed to the old file name `konzept-testabdeckung.md`; replaced by `test-coverage-concept.md`.
- Not resolved: base for M2 (merge `fix-tenant-claim` or not), licence/copy route of the backend SQL for M6 (backend has no `LICENSE` file), B-13 for S11, whether `@MockitoSpyBean` works on the transactional `BillingService` proxy (fallback in M5 risks).

Pass 2: 2026-10-01
- CI gap: `rolling-release.yml` runs on `push` only (no `pull_request`); M0 now adds a reusable `test.yml` called by the release workflow, so tests run on PRs and gate the image and the three deploy jobs.
- Testcontainers trap: `@Container` on a static field of a shared base class restarts the container per subclass while the cached Spring context keeps the old port; M0 now owns a singleton `PostgresContainerHolder`, M3/M5 use it, M6 has its own. This also removes the M6 to M3 dependency; README table and "Depends on" lines are consistent now.
- Lock location: not 2 but 5 handlers (`BillingResource.getAllInvoices`; `BillingRunResource` xlsx, archive, sendmail, delete); M5 corrected. The F7 race test now calls the mail service directly because the resource lock would serialise the calls.
- M4: `OffsetDateTime.now()` occurs 3 times (incl. `DomainConfig`), not 2; the 8 `LocalDate(Time).now()` calls re-verified.
- `@Disabled`: JUnit 5 accepts it with the existing setup; decided as the single plain convention, a tag rejected (needs surefire config, hides defects). It collides with AGENTS.md §15 ("no skipped tests") unless recorded: B-18; grep check added to README, M1.
- S11 had no consistent status (grep expects 12, S11 waits): acceptance now says 12, or 11 while B-13 is open, no empty placeholder test.
- `lombok.config` absent: JaCoCo counts Lombok-generated code; B-19 decides before the M0 baseline.
- M3 cleanup now includes the committed `base.billing_masterdata` rows of non-transactional scenarios; M2 notes the real filter order and the `azp` claim the merged branch demands.
- Percent thresholds in concept §8, M1, M2, M3, M4, M5 are estimates; marked as targets to be confirmed by measurement.
- "Decisions needed" table added (B-13..B-21); B-15..B-21 appended to `open-points.md`; M0 test count notes 37 after the merge.
- Unresolved/unchanged: concept file is 354 lines (pre-existing, over 300); effort figures remain estimates.

Decisions applied: 2026-10-01
- B-15 NO: M2 rewritten to build on `master` as it is (verified in `src/main/java/.../security`: no `azp`, no `iss` check, `AppProperties` has only `jwtPublicKeyFile`, the filter's tenant check never matches, #1); tokens need only `tenant`, `access_groups`, `preferred_username`; tenant rows failing on #1/#19 are disabled defect tests; the assumed 37 tests, `azp`, the branch's three test classes and `AppProperties` fields removed from M0, M2, concept; the `security` branch target is no longer 100 % on `master`.
- B-16: M6 rewritten (view built from the copied v3 `docker/legacy-base/01..08` SQL, byte-identical, with commit id; optional second check on `billing_masterdata_v3`); M3 got the optional v3 snapshot idea (generator never run in billing's build) and the optional `BillingRules.kt` oracle; the "backend has no LICENSE" blocker is removed everywhere.
- B-17, B-18 yes: recorded as decided, no change to M0/M1 content. B-21 not now: M4 marked.
- B-19 yes: `lombok.config` in M0 before the baseline; baseline measured twice (concept §2.2 figures are without the file), thresholds from the new one, §8 targets to be re-confirmed after it; effort of M0 +0.25 day.
- B-20: superseded, see "Decisions applied (B-13/B-14/B-20), 2026-10-01" below.
- Not resolved: B-13; whether the 64-column claim for `billing_masterdata_v3` holds (counted in M6's first task); the conversion step from the v3 world to `billing_masterdata` rows does not exist yet.


Decisions applied (B-13/B-14/B-20), 2026-10-01
- B-13 stays open and unanswered: S11 has no test until answered; today's rounding is documented in known-errors #23 as "open, business question".
- B-14 decided "as it is": billing keeps 403 for a missing token; M2 and the concept state 403 as the accepted contract; B-14 moved to Decided.
- B-20 decided: no change in billing; not fixed, not tested as a defect; recorded in known-errors #24 and v3 known-errors #74. F13 invariant test removed from M1; B-20 dependency removed from M1, M4, table and mapping; M4 depends on B-13 only.
- New open point B-22 (unverified): the web total may omit `meteringPointFeeSum`; no test, no billing change.