# Agent log

One entry per AI session, newest first. Format: date, task, changes, decisions, verification, open.

## 2026-10-01 — Working agreement from eegfaktura-v3, test environment repaired

**Task.** The user: add the best practices of eegfaktura-v3 (AGENTS.md, agent log, open points,
known errors, …) to billing. Branch `improve-testing-environment`.

**Changes.** `AGENTS.md` (v3's agreement adapted to Java 21 / Maven / Spring Boot 3.5 / Flyway in
`billingj`; tenant isolation and the `base.billing_masterdata` contract as rules), `CLAUDE.md`
pointing to it, `known-errors.md` (11 rows), `open-points.md` (B-1 … B-10), `EXTERNAL_SOURCES.md`
(licences read from the POMs), `docs/migrations/` (README + TEMPLATE), `scripts/dev/loc-check.sh`.
Test environment: `src/test/resources/docker-java.properties` (Docker API 1.44, known-errors #2)
and `TEST_STORE_DOCUMENTS_PATH` instead of `/home/hla/temp` (#3).

**Decisions.** Rules that the code does not meet yet are written as rules, the gap as a
known error with a pointer to the decision (floating tags, CI without tests, sizes) — nothing was
changed silently. Testcontainers stays at 1.20.1: the bump to 1.21.4 (published 2025-12-16) did not
change the requested API version, the properties file did. Rows #1 (tenant check) points to branch
`fix-tenant-claim` (89725e8), which is not merged; AGENTS.md section 6 says so.

**Verification.** `mvn -B test` in `maven:3-eclipse-temurin-21` with the Docker socket, without
`DOCKER_API_VERSION` and without a mounted temp path: 23/23 green (before: 2 suites failed on
Docker, 5 tests on the path). `bash scripts/dev/loc-check.sh`: 4 blocked, 1 red (open-points B-6).

**Open.** B-1 … B-10; the agreement goes upstream only by pull request after the user's go (B-10).

## 2026-10-01 — Tenant check of `/cash` (branch `fix-tenant-claim`)

**Task.** The user: the tenant check in `JwtRequestFilter` never refuses; the token's issuer and
recipient are not checked.

**Changes.** On branch `fix-tenant-claim`, commit 89725e8: the filter reads the `Tenant` header
itself and requires it in the token's `tenant` claim (403 otherwise); tenants kept apart from
roles; issuer (`JWT_ISSUER`) and client (`JWT_ALLOWED_CLIENTS`, default `at.ourproject.vfeeg.app`)
checked; `PUT /api/billingConfigs/{id}` checks the stored record's tenant. 14 new tests.

**Verification.** 37/37 green in JDK 21 (with the two test-environment workarounds that this
branch's entry above makes unnecessary).

**Open.** Merge and release; `JWT_ISSUER` in the deployments (open-points B-8).
