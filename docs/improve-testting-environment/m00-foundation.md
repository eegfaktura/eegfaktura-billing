# M0 — Foundation: CI, JaCoCo, builders

**Concept:** phase 0 · **Status:** open · **Production code:** none (`pom.xml`, CI, test code)
**Depends on:** `open-points.md` B-2 (tests in CI) and B-11 (JaCoCo) — both approved 2026-10-01; B-17
(CI runner and trigger) and B-19 (`lombok.config`) answered or their defaults accepted.
**Effort:** 1 – 1.5 days (mostly CI plumbing; the builder cross-check may push it to 2).

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
3. **Threshold that may only rise.** One `check` rule per package (`BUNDLE` for the total) with the
   line and branch ratio of concept §2.2 (re-measured at the end of M0 with `mvn clean`), rounded
   **down** to the full percent. Packages without branches get a line rule only. Raised at the end
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
   operation); `open-points.md` B-2 and B-11 → Decided; `AGENT_LOG.md`.

## Out of scope

Functional tests (M1 – M3); moving `MassDataGenerator` (T11, a separate small change); replacing the
placeholders `contextLoads`/`testContainer` and the other old-test cleanups T1, T2, T3, T4, T7, T8,
T10 (M3); pinning the existing floating tags (B-3).

## Tasks

- [ ] Decide B-19 (`lombok.config` with `lombok.addLombokGeneratedAnnotation = true`; without it JaCoCo counts generated accessors and `equals`/`hashCode` branches); then baseline: `./mvnw -B clean verify` on a clean tree, JaCoCo CSV saved; figures into `AGENT_LOG.md`
- [ ] `pom.xml`: JaCoCo plugin with exact version, report + check, `@{argLine}`
- [ ] Thresholds from the baseline (rounded down) as `check` rules
- [ ] CI: `test` job, `needs: test` on the image job, report upload, pinned new actions
- [ ] `base_masterdata_ddl.sql`, `BillingMasterdataBuilder`, `AllocationBuilder`, `BillingRunFixture`
- [ ] One test using the builders that reproduces the fixture's result (cross-check)
- [ ] `show-sql` off; docs, `EXTERNAL_SOURCES.md`, `CHANGELOG.md`, `open-points.md`, `AGENT_LOG.md`
- [ ] Size check: `bash scripts/dev/loc-check.sh` shows no new yellow/red file

## Acceptance criteria

- A deliberately red test on a throw-away pull request yields a red `test` job; a red test on a throw-away `preview/` branch yields **no** image (shown once; links in `AGENT_LOG.md`).
- `./mvnw -B clean verify` is green locally and in CI with the 23 existing tests (37 once `fix-tenant-claim` is merged) plus the new one(s); `./mvnw test` still runs the suite without the coverage gate.
- `target/site/jacoco/index.html` exists as a CI artefact; raising one package threshold by 1 point on a throw-away branch fails `verify`.
- The packaged jar lists the same classes before and after (`unzip -l target/*.jar | sort` diff is empty apart from the build timestamp/version).
- The builder cross-check produces the same document count (5) and gross amounts as the `BillingIntegrationTests` assertions for the SQL fixture.
- `git diff --stat src/main` is empty.

## Risks

- Runner without Docker or with another Docker API → Testcontainers fail before the first test; fallback is a self-hosted runner (B-17).
- Threshold too tight → flaky red builds; rounded-down values, measured on a clean run.
- The builders' DDL copy drifts from the fixture's `create table` → both are replaced by the contract test in M6.
- `check` in `verify` is not part of `./mvnw test` (AGENTS.md §10.3 gate): a coverage drop is only seen in CI; accepted.
