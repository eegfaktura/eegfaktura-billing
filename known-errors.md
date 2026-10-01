# Known errors

Status values: open, mitigated, fixed (date). Add an entry as soon as a problem is found
(AGENTS.md section 14). Rows 1–11 were collected on 2026-10-01 when this file was started; their
sources are the security review of that day and `eegfaktura-analyze-it` (state-of-patches,
state-of-testing, 2026-09-01).

| # | Date | Area | Description | Status |
|---|---|---|---|---|
| 1 | 2026-10-01 | security / tenant | **The tenant check in `JwtRequestFilter` never refuses a request**: the condition is inverted, it compares a `String` with an `Authority` that has no `equals`, and it reads `TenantContext`, which `TenantFilter` (`@Order(1)`) fills only after the security filter chain (order −100). An `EEG_ADMIN` reaches runs, documents and files of any community by naming it in the `Tenant` header. Also: roles and tenants share one authority list; the issuer and client of the token are not checked; `PUT /api/billingConfigs/{id}` checks only the body's tenant. | fixed on branch `fix-tenant-claim` (89725e8, 2026-10-01), **not merged** |
| 2 | 2026-10-01 | tests / Testcontainers | **The integration tests find no Docker on Docker 29**: Testcontainers 1.20.1 (docker-java) asks for API 1.32, Docker 29 refuses below 1.40 (`client version 1.32 is too old`). `BillingIntegrationTests` and `DocumentNumbersTests` fail before the first test. A Testcontainers bump to 1.21.4 alone does not change the requested version. | fixed 2026-10-01: `src/test/resources/docker-java.properties` sets `api.version=1.44` |
| 3 | 2026-10-01 | tests | **The integration tests write PDFs and XLSX to `/home/hla/temp`** (a developer's machine, `src/test/resources/application.properties`) and fail with `NoSuchFileException` everywhere else (5 tests). | fixed 2026-10-01: `TEST_STORE_DOCUMENTS_PATH`, empty by default = not written (the tests already skipped an empty path) |
| 4 | 2026-10-01 | build / JDK | **The build fails under JDK 25**: Lombok generates no accessors (`cannot find symbol getAllocations()`). The project is Java 21. | mitigated: AGENTS.md section 9 names JDK 21 and the container command |
| 5 | 2026-10-01 | dependencies | **Spring Boot 3.5.3 brings Tomcat 10.1.42 and Spring Security 6.5.1 with known CVEs** (state-of-patches §3.2: CVSS ≥ 9 in Tomcat, header suppression in Spring Security; most need a configuration billing does not have, the escape-sequence and header issues apply). | open: `open-points.md` B-1 |
| 6 | 2026-10-01 | CI | **CI runs no tests**: `rolling-release.yml` builds and pushes the image; the Testcontainers suite runs only when a developer starts it. `snyk.yml` runs SAST with `continue-on-error: true`. | open: `open-points.md` B-2 |
| 7 | 2026-10-01 | security / config | **Profile `dev` switches authorization off** (`DevSecurityConfig`: no rule, CSRF off). Harmless locally, a full bypass if the profile is ever set on a server. | open |
| 8 | 2026-10-01 | config | **`application.properties` falls back to the database login `postgres`/`postgres`** when `JDBC_DATABASE_USERNAME`/`_PASSWORD` are missing. The deployments set both; a forgotten variable should stop the start, not try a superuser login. | open |
| 9 | 2026-10-01 | versions | **Floating versions** against AGENTS.md section 12.1: `maven:3-eclipse-temurin-21` and `eclipse-temurin:21-jre-jammy` in the `Dockerfile`, CI actions by major tag (`actions/checkout@v4`, …), the image tag `latest` on the default branch. | open: `open-points.md` B-3 |
| 10 | 2026-10-01 | database / contract | **`base.billing_masterdata` is owned by the Go backend** and read here through `@Subselect`, which `ddl-auto=validate` does not check: a backend migration that drops or renames a column breaks billing at runtime only. The backend recreated the view on 2025-06-05. | open: AGENTS.md section 8 makes it a rule; no test yet |
| 11 | 2026-10-01 | migrations | **The migrations `V1_0` … `V1_14` have no migration concepts** (AGENTS.md section 8.1 starts with this file). | open: `open-points.md` B-7 |
