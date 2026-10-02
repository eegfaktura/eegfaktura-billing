# External sources

Every source outside this repository that the build, the tests or the running service depends
on. Adding a source requires a decision first (AGENTS.md section 12): record the question in
`open-points.md`, then add the row here in the same change. Licences were read from each
artifact's own POM in the local Maven repository on 2026-10-01.

## Build and toolchain

| Source | Used for | Version / licence | Trust / pinning |
|---|---|---|---|
| Maven Central (`repo.maven.apache.org`) | all dependencies and plugins | — | official |
| Maven wrapper (`.mvn/wrapper`) | `./mvnw` | Maven 3.8.7, wrapper 3.1.1; Apache-2.0 | pinned in `maven-wrapper.properties` |
| Docker Hub `maven:3-eclipse-temurin-21` | builder stage of the `Dockerfile`, local test runs | Apache-2.0 (Maven), GPLv2+CE (OpenJDK) | **floating tag** (`known-errors.md` #9) |
| Docker Hub `eclipse-temurin:21-jre-jammy` | runtime image | GPLv2+CE | **floating tag** (`known-errors.md` #9) |
| GitHub Actions: `actions/checkout`, `docker/metadata-action`, `docker/login-action`, `docker/build-push-action`, `actions/attest-build-provenance`, Snyk | CI (`.github/workflows/`) | — | by major tag in `rolling-release.yml`; by SHA in `snyk.yml` |
| GitHub Actions in `test.yml` (pinned by commit SHA): `actions/checkout` v4.2.2 (2024-10-23), `actions/setup-java` v6.0.1 (2026-09-09), `actions/upload-artifact` v7.0.1 (2026-04-10) | build, test and coverage report in CI | MIT (actions repositories) | SHA verified with `git ls-remote` / GitHub API on 2026-10-01 |
| `org.jacoco:jacoco-maven-plugin` | coverage report and gate (`pom.xml`) | 0.8.13 (Maven Central 2025-04-02); EPL-2.0 (from the POM) | exact version; build only, not in the jar |
| GitHub Container Registry `ghcr.io/eegfaktura/eegfaktura-billing` | where the image is published | AGPL-3.0 | tags per release |

## Libraries (runtime)

| Artifact | Used for | Version | Licence |
|---|---|---|---|
| Spring Boot starters (web, validation, data-jpa, security, oauth2-client, oauth2-resource-server, webflux, mail) | the service | 3.5.3 (parent POM) | Apache-2.0 |
| `org.postgresql:postgresql` | JDBC driver | from the Spring Boot parent | BSD-2-Clause |
| `org.flywaydb:flyway-database-postgresql` | schema migrations | from the Spring Boot parent | Apache-2.0 |
| `com.auth0:java-jwt` | JWT verification | 4.5.0 | MIT |
| `org.json:json` | reading the token payload | 20250107 | Public Domain |
| `net.sf.jasperreports:jasperreports` | PDF documents | 7.0.7 | LGPL |
| `jasperreports-functions`, `-fonts`, `-jdt`, `-pdf` | report functions, fonts, compiler, PDF export | 7.0.2 | LGPL |
| `org.apache.poi:poi`, `poi-ooxml` | XLSX export | 5.4.1 | Apache-2.0 |
| `org.freemarker:freemarker` | mail body template | 2.3.34 | Apache-2.0 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | OpenAPI / Swagger UI | 2.3.0 | Apache-2.0 |
| `org.projectlombok:lombok` | accessors at compile time (not in the jar) | from the Spring Boot parent | MIT |

## Libraries (tests only)

| Artifact | Used for | Version | Licence |
|---|---|---|---|
| `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ) | tests | from the Spring Boot parent | EPL-2.0 / MIT / Apache-2.0 |
| `org.testcontainers:junit-jupiter`, `postgresql` | PostgreSQL in the integration tests | 1.20.1 | MIT |
| Docker Hub PostgreSQL image (started by Testcontainers) | the test database | as named in the tests | PostgreSQL licence |
| `org.hamcrest:hamcrest-all` | assertions | 1.3 | BSD |
| `org.jacoco:jacoco-maven-plugin` | coverage (build time, agent in the test JVM) | 0.8.13 | EPL-2.0 |
| `net.datafaker:datafaker` | generated test data | 2.2.2 | Apache-2.0 |
| `org.apache.pdfbox:pdfbox` (with `pdfbox-io`, `fontbox`, same version) | reading the text of the generated PDFs in the billing scenarios (M3) | 3.0.8 (Maven Central 2026-07-08, latest release; approved in `open-points.md` B-12) | Apache-2.0 (licence header of the POM; `<licenses>` inherited from `org.apache:apache:39`); its optional Bouncy Castle dependencies are not pulled; `commons-logging` stays at 1.3.5 from JasperReports |
| `com.icegreen:greenmail` (core; without its `junit:junit` dependency, excluded in the POM) | local SMTP server in the mail tests (M5), started once per JVM by `support/GreenMailHolder` on a free port | 2.1.14 (Maven Central 2026-09-19, latest release; approved in `open-points.md` B-12) | Apache-2.0 (`<licenses>` of the parent `com.icegreen:greenmail-parent:2.1.14`); its `jakarta.mail-api`/`org.eclipse.angus:jakarta.mail`/`jakarta.activation-api` are managed down by Spring Boot 3.5.3 to 2.1.3/2.0.3/2.1.3 (GreenMail declares 2.1.5/2.0.5/2.1.4 — same Jakarta Mail 2.1 line, checked by the green mail tests); `angus-activation` 2.0.3 runtime |
| eegfaktura-v3 `billing-arithmetic-cases.json` (copied file, `src/test/resources/v3oracle/`) | expected item amounts for `BillingArithmeticOracleTests` (M3 optional item, B-16) | v3 commit `0b785d2`, file from `f6540a0`, sha256 `830d846c…` | AGPL-3.0 (same organisation); never fetched by the build, refreshed by hand |

## Runtime services

| Service | Used for | Notes |
|---|---|---|
| PostgreSQL database `eegfaktura` | schema `billingj`; reads `base.billing_masterdata` | shared with the other services (`known-errors.md` #10) |
| Keycloak realm `EEGFaktura` | issues the tokens; billing verifies them with the certificate in `JWT_PUBLICKEYFILE` | no network call from billing |
| SMTP relay (`MAIL_HOST`, in the platform `eegfaktura-postfix`) | sending invoices by mail | |

No licence audit runs automatically yet; the licences above were checked by hand from the POMs.
