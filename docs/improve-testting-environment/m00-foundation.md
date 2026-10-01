# M0 — Foundation: CI, JaCoCo, builders

**Concept:** phase 0 · **Status:** done (CI run not verified) · **Production code:** none (`pom.xml`, CI, `lombok.config`, test code)
**Depends on:** `open-points.md` B-2 (tests in CI), B-11 (JaCoCo), B-17 (hosted `ubuntu-latest`, reusable
`test.yml`) and B-19 (`lombok.config` first, then the baseline) — all decided 2026-10-01.
**Effort:** 1.25 – 1.75 days (CI plumbing; the second baseline run with and without `lombok.config` adds
about 0.25 day; the builder cross-check may push it to 2).

## Goal

Tests run on every change, coverage is measured and cannot silently fall, and new tests can be
written cheaply. Without M0 the later milestones have no safety net in CI.

## Scope

1. **Tests in CI.** `rolling-release.yml` triggers only on `push` (master, main, `preview/**`,
   `env/**`, tags), never on pull requests, so "tests on every change" needs a new trigger. New
   reusable workflow `.github/workflows/test.yml` (`on: pull_request` and `workflow_call`; job
   `./mvnw -B clean verify`, JDK 21); `rolling-release.yml` calls it as job `test` and
   `build-and-push-image` gets `needs: test` (the three dispatch jobs follow through their own
   `needs`); a red run blocks the image and every deploy (`known-errors.md` #6). GitHub-hosted
   `ubuntu-latest` has Docker, so Testcontainers needs no service container; `docker-java.properties`
   (`known-errors.md` #2) already pins the API version. The existing floating action tags stay
   under B-3 (`known-errors.md` #9); every **new** action (`setup-java`, `upload-artifact`) is pinned
   by commit SHA and its release is ≥ 7 days old (AGENTS.md §12.1; record the dates in `AGENT_LOG.md`).
2. **JaCoCo in the `pom.xml`.** `org.jacoco:jacoco-maven-plugin` **0.8.13** (published 2025-04-02,
   EPL-2.0; re-check on the day that no fixed newer version ≥ 7 days exists), goals `prepare-agent`,
   `report`, `check`; `check` is bound to `verify`, so `./mvnw test` stays the fast loop and does
   not gate on coverage. `argLine` is passed through as `@{argLine}` (Mockito/agent conflict). The
   report `target/site/jacoco` is uploaded as a CI artefact.
3. **`lombok.config` and the re-measured baseline (B-19, decided).** New file `lombok.config` in the
   repo root with `lombok.addLombokGeneratedAnnotation = true`. It is not a code change, but it
   changes how coverage is counted: JaCoCo then ignores Lombok-generated accessors and
   `equals`/`hashCode` branches. The concept §2.2 percentages (55.6 % lines, 56.1 % branches) were
   measured **without** it, so M0 measures twice with `mvn clean` — first without, then with the
   file — and records **both** figures in `AGENT_LOG.md` and the concept. The thresholds start from
   the **new** baseline; the targets of concept §8 are re-confirmed after that measurement.
   **Threshold that may only rise.** One `check` rule per package (`BUNDLE` for the total) with the
   line and branch ratio of the new baseline, rounded **down** to the full percent. Packages without branches get a line rule only. Raised at the end
   of every milestone.
4. **Test-data builders** in `src/test/java/org/vfeeg/eegfaktura/billing/support/`:
   `PostgresContainerHolder` (one *singleton* `postgres:15-alpine` container started in a static
   initializer plus the `@DynamicPropertySource` registration — **not** `@Container` on a base
   class, which restarts the container per subclass while the cached Spring context still points at
   the old port; M3 and M5 build on it, M6 starts its own instance), `BillingMasterdataBuilder`, `AllocationBuilder` (`BigDecimal` instead of `Double.parseDouble`,
   T6) and a `BillingRunFixture` that inserts masters and allocations for one tenant. There is no
   backend view in the test database: `billing_master_data.sql` creates a plain **table**
   `base.billing_masterdata`; the builders insert rows into it with `JdbcTemplate` (participates in
   the `@Transactional` test transaction). The table DDL is extracted into
   `src/test/resources/base_masterdata_ddl.sql`; the 649-line fixture stays for
   `BillingIntegrationTests` (accepted data file, `open-points.md` B-6).
5. **Test hygiene and convention:** defect tests use plain `@Disabled("known-errors #NN")` (README, "Rules");
   `spring.jpa.show-sql=false` in `src/test/resources/application.properties`
   (T9); no new `boolean` assert helpers — Hamcrest (`assertThat(x, is(…))`) or AssertJ, both on the
   classpath through `spring-boot-starter-test`/`hamcrest-all` (T1 hygiene).
6. **Docs and tracking:** `mvn clean` rule (T12) in the repo `README.md`; `EXTERNAL_SOURCES.md` row
   for JaCoCo; `CHANGELOG.md` `[Unreleased]` entry (a red test now blocks the image — relevant for
   operation); `lombok.config`; `open-points.md` B-2 and B-11 → Decided; `AGENT_LOG.md`.

## Out of scope

Functional tests (M1 – M3); moving `MassDataGenerator` (T11, a separate small change); replacing the
placeholders `contextLoads`/`testContainer` and the other old-test cleanups T1, T2, T3, T4, T7, T8,
T10 (M3); pinning the existing floating tags (B-3).

## Tasks

- [x] Baseline run 1 without `lombok.config` (JaCoCo via command line, `mvn clean`), CSV saved; then add `lombok.config` (B-19) and baseline run 2; both figures into `AGENT_LOG.md` and concept §2.2; confirm the §8 targets
- [x] `pom.xml`: JaCoCo plugin with exact version, report + check, `@{argLine}`
- [x] Thresholds from the baseline (rounded down) as `check` rules
- [x] CI: `test` job, `needs: test` on the image job, report upload, pinned new actions
- [x] `base_masterdata_ddl.sql`, `BillingMasterdataBuilder`, `AllocationBuilder`, `BillingRunFixture`
- [x] One test using the builders that reproduces the fixture's result (cross-check)
- [x] `show-sql` off; docs, `EXTERNAL_SOURCES.md`, `CHANGELOG.md`, `open-points.md`, `AGENT_LOG.md`
- [x] Size check: `bash scripts/dev/loc-check.sh` shows no new yellow/red file

## Acceptance criteria

- A deliberately red test on a throw-away pull request yields a red `test` job; a red test on a throw-away `preview/` branch yields **no** image (shown once; links in `AGENT_LOG.md`).
- `./mvnw -B clean verify` is green locally and in CI with the 23 existing tests (`master` as it is) plus the new one(s); `./mvnw test` still runs the suite without the coverage gate.
- `target/site/jacoco/index.html` exists as a CI artefact; raising one package threshold by 1 point on a throw-away branch fails `verify`.
- The packaged jar lists the same classes before and after (`unzip -l target/*.jar | sort` diff is empty apart from the build timestamp/version).
- The builder cross-check produces the same document count (5) and gross amounts as the `BillingIntegrationTests` assertions for the SQL fixture.
- `git diff --stat src/main` is empty; the only new non-test files are `lombok.config` (repo root), `.github/workflows/test.yml` and the `pom.xml` change.

## Risks

- Runner without Docker or with another Docker API → Testcontainers fail before the first test; fallback is a self-hosted runner (B-17 chose the hosted runner; revisit only if it fails).
- `lombok.config` lowers the line/branch counts of the model/domain classes and may raise the percentages; old and new figures are not comparable, hence both are recorded.
- Threshold too tight → flaky red builds; rounded-down values, measured on a clean run.
- The builders' DDL copy drifts from the fixture's `create table` → both are replaced by the contract test in M6.
- `check` in `verify` is not part of `./mvnw test` (AGENTS.md §10.3 gate): a coverage drop is only seen in CI; accepted.

## Result (2026-10-01)

- Baseline (23 tests, `mvn clean`): without and with `lombok.config` identical, 1026 / 1845 lines (55.61 %), 192 / 342 branches (56.14 %); Lombok 1.18.38 already marks generated code, so the file changes nothing today.
- Thresholds (floors, `pom.xml`, `check` in `verify`): BUNDLE lines 55 % / branches 56 %; service 62/54, util 88/84, domain 88/75, repos 40/50 (lines/branches); model 61, config 21, security 19, rest 17, controller 50, root package 33 (lines only). `security` has 18 branches, none covered: no branch rule.
- Full run `clean verify`: 24 tests (23 + `BuilderCrossCheckTests`), 0 failed, all coverage checks met; the builders reproduce the fixture (5 documents, gross 35.88 / 762.55 / 125.21 / 10.00 / 431.68). Negative check: raising `rest` to 0.18 fails `jacoco:check`.
- Not verified: the GitHub run of `test.yml` and the two throw-away-branch checks (YAML syntax only checked locally, no actionlint); the jar class-list diff before/after (no production code or dependency changed; 103 classes in the jar). Command for local runs: `HOWTO-run-tests.md`.
- JaCoCo 0.8.13 kept although 0.8.14/0.8.15 exist: `open-points.md` B-23.
