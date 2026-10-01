# Open points

Decisions that belong to the user or the maintainer. One line per point: date, question, current
assumption. Move answered points to "Decided" with the answer and the date.

## Open

| # | Date | Question | Current assumption |
|---|---|---|---|
| B-1 | 2026-10-01 | Bump the Spring Boot parent 3.5.3 to the latest 3.5.x (Tomcat, Spring Security CVEs, `known-errors.md` #5), together with the five JasperReports artifacts and POI (state-of-patches §3.2)? | yes, as its own pull request with the full test run and a look at the generated PDFs; the version must be ≥ 7 days old |
| B-2 | 2026-10-01 | Run `mvn test` in CI (`rolling-release.yml`) before the image is built, with Docker for Testcontainers (`known-errors.md` #6)? | yes; a red run blocks the image |
| B-3 | 2026-10-01 | Pin the `Dockerfile` images and the CI actions exactly (full tag or digest, actions by SHA), and drop the `latest` image tag (`known-errors.md` #9)? | yes; Dependabot (docker) then proposes the updates |
| B-4 | 2026-10-01 | Renovate with `minimumReleaseAge: 7 days` for Maven, Docker and actions, as in eegfaktura-v3 — or stay with the docker-only Dependabot the `dependabot.yml` comment chose on purpose? | stay with Dependabot until the backlog of open PRs is cleared; the 7-day rule is checked by hand |
| B-5 | 2026-10-01 | Structured log output (ECS JSON, `logging.structured.format.console=ecs`, Spring Boot ≥ 3.4) with a request id, as in eegfaktura-v3? Changes what the log shipper receives. | not done; new log lines follow the `event=` convention of AGENTS.md section 11 already |
| B-6 | 2026-10-01 | Files above the size limit (AGENTS.md section 13): `BillingService.java` 622, `BillingIntegrationTests.java` 739, `MassDataGenerator.java` 627 (blocked), `BillingDocumentDefaultTemplate.jrxml` 479 (red), the fixture `billing_master_data.sql` 649. Split, or accept? | `BillingService` split by responsibility (run, documents, numbering) in its own change; the test class by area; the fixture and the report template accepted as data |
| B-7 | 2026-10-01 | Write migration concepts for the existing `V1_0` … `V1_14` (`known-errors.md` #11), or start the rule with the next migration? | start with the next migration; the existing ones get a one-page summary when someone touches them |
| B-8 | 2026-10-01 | `JWT_ISSUER` in the deployments (`eegfaktura-docker-compose`, k8s) once `fix-tenant-claim` is merged: the realm's public issuer URL per environment | to be set by the operator with the release that contains the fix |
| B-9 | 2026-10-01 | Move error responses to RFC 9457 `ProblemDetail` (as eegfaktura-v3)? A contract change for `eegfaktura-web` and v3. | no; `ErrorResponse` stays |
| B-10 | 2026-10-01 | This working agreement itself: does the maintainer want `AGENTS.md`, the tracking files and the rules in the upstream repository? | proposed by pull request; nothing is pushed without the user's go |
| B-11 | 2026-10-01 | JaCoCo (`org.jacoco:jacoco-maven-plugin` 0.8.13, EPL-2.0, published 2025-04-02) in the POM, the report as a CI artifact and a coverage threshold per package that may only rise (`docs/improve-testting-environment/konzept-testabdeckung.md` Phase 0) | yes, with the CI test step (B-2); used once from the command line for the concept, not added |
| B-12 | 2026-10-01 | New test libraries for the concept's phases 3 and 5: PDFBox (read the generated PDFs), GreenMail (test SMTP), optionally PIT (mutation tests) | decide per phase; none added |
| B-13 | 2026-10-01 | Rounding of the billing (concept F12): round kWh before pricing? VAT per line or per rate on the net sum? A business question before tests fix the current behaviour | open; no test written for it yet |

## Decided

| # | Date | Question | Answer |
|---|---|---|---|
