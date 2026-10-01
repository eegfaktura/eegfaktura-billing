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
| `net.datafaker:datafaker` | generated test data | 2.2.2 | Apache-2.0 |

## Runtime services

| Service | Used for | Notes |
|---|---|---|
| PostgreSQL database `eegfaktura` | schema `billingj`; reads `base.billing_masterdata` | shared with the other services (`known-errors.md` #10) |
| Keycloak realm `EEGFaktura` | issues the tokens; billing verifies them with the certificate in `JWT_PUBLICKEYFILE` | no network call from billing |
| SMTP relay (`MAIL_HOST`, in the platform `eegfaktura-postfix`) | sending invoices by mail | |

No licence audit runs automatically yet; the licences above were checked by hand from the POMs.
