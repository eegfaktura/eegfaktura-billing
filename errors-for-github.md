# Errors in the billing code — list for GitHub issues

Defects in the production code of eegfaktura-billing (state of `master`, 2026-10-02), one section
per future GitHub issue. Not included: problems of the test environment, the build or CI
(known-errors #2, #3, #4, #6) and gaps against this repository's own working agreement (#9 floating
versions, #11 missing migration concepts). The number in brackets is the row in `known-errors.md`.

Tests named under "Reproduced by" are on branch `improve-testing-environment`. Defect tests assert
the correct behaviour and are `@Disabled("known-errors #NN")` until the fix; enabled, they fail.

Severity: **critical** — data of other communities or legally relevant documents at risk;
**high** — wrong or lost billing data; **medium** — wrong API behaviour; **low** — robustness, cleanup.

## Overview

| Issue | Severity | Title | Reproduced |
|---|---|---|---|
| [1](#1-tenant-check-in-jwtrequestfilter-never-refuses-a-request-1) | critical | Tenant check in `JwtRequestFilter` never refuses a request | M2 (disabled rows) |
| [2](#2-put-apibillingconfigsid-checks-only-the-tenant-of-the-body-19) | critical | `PUT /api/billingConfigs/{id}` checks only the tenant of the body | yes |
| [3](#3-a-billing-run-request-without-preview-runs-the-final-billing-34) | critical | A billing-run request without `preview` runs the final billing | yes (pinned) |
| [4](#4-a-failed-billing-run-leaves-documents-pdfs-and-document-numbers-behind-12) | high | A failed billing run leaves documents, PDFs and document numbers behind | yes |
| [5](#5-documents-are-saved-without-a-billing-run-13) | high | Documents are saved without a billing run | yes |
| [6](#6-the-per-community-lock-does-not-prevent-parallel-runs-14) | high | The per-community lock does not prevent parallel runs | yes |
| [7](#7-document-numbers-are-assigned-without-a-lock-15) | high | Document numbers are assigned without a lock | yes |
| [8](#8-vat-rates-are-compared-scale-sensitively-16) | high | VAT rates are compared scale-sensitively | yes |
| [9](#9-participant-fee-without-a-vat-rate-fails-the-whole-run-17) | high | Participant fee without a VAT rate fails the whole run | yes |
| [10](#10-mail-status-is-wrong-and-mails-can-be-sent-twice-18) | high | Mail status is wrong and mails can be sent twice | yes |
| [11](#11-deleting-a-billing-run-with-documents-fails-22) | medium | Deleting a billing run with documents fails | yes |
| [12](#12-a-record-of-another-community-gives-500-an-unknown-one-404-20) | medium | A record of another community gives 500, an unknown one 404 | yes |
| [13](#13-client-errors-end-as-500-31) | medium | Client errors end as 500 | yes |
| [14](#14-footer-and-logo-download-endpoints-are-broken-21) | medium | Footer and logo download endpoints are broken | yes |
| [15](#15-replacing-a-config-image-deletes-the-old-one-first-wrong-type-gives-500-27) | medium | Replacing a config image deletes the old one first; wrong type gives 500 | yes |
| [16](#16-config-images-are-stored-without-a-tenant-32) | medium | Config images are stored without a tenant | no (code reading) |
| [17](#17-numberofinvoices-and-numberofcreditnotes-are-never-set-33) | medium | `numberOfInvoices` and `numberOfCreditNotes` are never set | no |
| [18](#18-master-data-view-of-the-backend-is-not-checked-at-start-10) | medium | Master-data view of the backend is not checked | mitigated by tests |
| [19](#19-profile-dev-switches-authorization-off-7) | medium | Profile `dev` switches authorization off | no |
| [20](#20-database-login-falls-back-to-postgrespostgres-8) | medium | Database login falls back to `postgres`/`postgres` | no |
| [21](#21-spring-boot-353-brings-tomcat-and-spring-security-with-known-cves-5) | medium | Spring Boot 3.5.3 brings Tomcat and Spring Security with known CVEs | no |
| [22](#22-shared-decimalformat-is-not-thread-safe-30) | medium | Shared `DecimalFormat` is not thread-safe | no (suspicion) |
| [23](#23-rounding-of-kwh-and-vat-needs-a-business-decision-23) | question | Rounding of kWh and VAT needs a business decision | – |
| [24](#24-producer-amounts-negative-sum-from-positive-values-24) | question | Producer amounts negative, sum from positive values | – |
| [25](#25-document-year-depends-on-the-jvm-time-zone-25) | low | Document year depends on the JVM time zone | no |
| [26](#26-billingconfigservicedefault-is-a-public-mutable-static-entity-26) | low | `BillingConfigService.DEFAULT` is a public mutable static entity | no |
| [27](#27-streams-not-closed-printstacktrace-for-logging-28) | low | Streams not closed, `printStackTrace()` for logging | no |
| [28](#28-dead-code-in-the-lock-and-commented-out-endpoints-29) | low | Dead code in the lock and commented-out endpoints | no |

---

## 1. Tenant check in `JwtRequestFilter` never refuses a request (#1)

**Severity:** critical · **Where:** `security/JwtRequestFilter`, `TenantFilter`, `TenantContext`

The check whether the `Tenant` header is in the token's `tenant` claim never refuses:
- the condition is inverted;
- it compares a `String` with an `Authority` that has no `equals`;
- it reads `TenantContext`, which `TenantFilter` (`@Order(1)`) fills only after the security filter chain (order −100).

An `EEG_ADMIN` reaches runs, documents and files of **any** community by naming it in the `Tenant`
header. Also: roles and tenants share one authority list; issuer and client of the token are not
checked.

**Reproduced by:** `EndpointMatrix.headerTenantNotInTokenClaimIsForbidden` (31 rows, 200/201/204 instead of 403).
**Fix:** exists on branch `fix-tenant-claim` (89725e8), not merged. After the merge the test tokens
need `iss` and `azp`.

## 2. `PUT /api/billingConfigs/{id}` checks only the tenant of the body (#19)

**Severity:** critical · **Where:** `BillingConfigResource` update

The tenant of the **stored** record is not compared, only the one in the request body. A caller can
overwrite another community's configuration by sending its id with an own tenant in the body.

**Reproduced by:** `BillingConfigCrudWebTests.updateOfAForeignStoredConfigIsForbidden` (200 instead of 403).
**Fix:** part of `fix-tenant-claim` (89725e8).

## 3. A billing-run request without `preview` runs the final billing (#34)

**Severity:** critical · **Where:** `model/DoBillingParams.java`, `config/JacksonConfig.java`

`DoBillingParams.isPreview` is a primitive `boolean` (default `false` = final), Lombok maps it to
the JSON key `preview`, and `JacksonConfig` disables `FAIL_ON_UNKNOWN_PROPERTIES`. A body without
`preview`, or with `isPreview` / `Preview`, therefore runs a **final** billing without any error:
document numbers are consumed (`BillingService.java:365`), the run is set to `DONE`
(`BillingService.java:155`) and blocks the period, the PDFs are final documents.

Both current callers (eegfaktura-web, eegfaktura-v3) send `preview` correctly; v3 documents the trap
in `LegacyBillingDtos.kt`.

**Reproduced by:** `CallerRequestContractTests.keyIsPreviewIsIgnoredAndTheRunIsFinal` (pins today's behaviour).
**Fix (proposal):** `Boolean preview` with `@NotNull` and `@Valid` on the endpoint → 400 without the
key; together with issue 13. Contract change: announce to web and v3.

## 4. A failed billing run leaves documents, PDFs and document numbers behind (#12)

**Severity:** high · **Where:** `BillingService.java:59-60,165-169`

`doBilling` catches every exception inside `@Transactional` and returns normally with a failure
text. Nothing is rolled back: documents, PDFs and consumed document numbers of the failed run stay.
Errors raised by the code itself include more than two VAT rates and an NPE (issue 9).
A caller sees a failure but the data is half written.

**Reproduced by:** `BillingScenarioVatTests.s04_threeVatRatesFailAndLeaveNothingBehind` (3 documents left instead of 0).
**Fix:** narrow the `catch`, rethrow (or mark rollback-only) so the transaction rolls back.

## 5. Documents are saved without a billing run (#13)

**Severity:** high · **Where:** `BillingService.java:218-226,349`, `BillingDocumentService.java:134`

`createBillingDocument` saves an invoice and a producer document for **every** participant before
it knows whether they are needed; unused ones (e.g. amount 0) stay without a billing run.
`deleteByBillingRunId` never removes them, and `GET /api/billingDocuments/tenant/{id}/{year}`
fails on them with an NPE.

**Reproduced by:** `BillingScenarioErrorPathTests.s08_participantWithAmountZeroLeavesNoDocumentWithoutRun` (3 documents without run for 2 participants).
**Fix:** assign the run before the first save and save a document only when it is used.

## 6. The per-community lock does not prevent parallel runs (#14)

**Severity:** high · **Where:** `InMemoryLockRepository.java:29-45`, `BillingResource`, `BillingRunResource`

- `releaseLock` removes the map entry while other threads still wait on the old monitor, so a third request gets a new lock and runs in parallel.
- The entry expires after 15 minutes, even during a running run.
- There is an NPE window between `compute` and `get`.

**Reproduced by:** `InMemoryLockRepositoryTests.threeThreadsOfOneTenantNeverOverlap`,
`ConcurrentBillingRunTests.runsOfOneCommunityNeverOverlap` (a third run enters `doBilling` while the second is inside).
**Fix:** release only the own entry, no expiry during a run; better move the lock into the service.

## 7. Document numbers are assigned without a lock (#15)

**Severity:** high · **Where:** `BillingDocumentNumberGeneratorImpl.java:43-48`

The next number is "read max, then save". Two runs of one community both read the same maximum,
both take e.g. `TRECH202400042`; the second fails on the unique constraint and its caller gets
`UnexpectedRollbackException`. Also: the prefixes `" R"` and `"R"` have separate sequences but
format to the same number.

**Reproduced by:** `ConcurrentBillingRunTests.numbersOfParallelRunsInTheServiceAreDistinct`,
`BillingDocumentNumberGeneratorTests.prefixWithBlankContinuesTheSequenceOfTheTrimmedPrefix` ("R202400000" instead of "R202400042").
**Fix:** row lock / sequence per (tenant, year, prefix); trim the prefix.

## 8. VAT rates are compared scale-sensitively (#16)

**Severity:** high · **Where:** `BillingService.java:598-610`

VAT rates are compared with `BigDecimal.equals`, where 20 ≠ 20.00. The same rate is then split into
two VAT sums, or the run fails with "More than 2 VAT rates".

**Reproduced by:** `BillingScenarioVatTests.s05_sameRateWithOtherScaleIsOneVatSum` (tariff 20 and fee 20.00: VAT sum 2.00 instead of 4.00).
**Fix:** `compareTo` or normalise the scale.

## 9. Participant fee without a VAT rate fails the whole run (#17)

**Severity:** high · **Where:** `BillingService.java:510,587`

The participant fee stores the raw `vatPercent`; `null` leads to an NPE in the VAT sums. Because of
issue 4 the run then fails half written.

**Reproduced by:** `BillingScenarioFeeTests.s07_participantFeeWithoutVatRateIsBilledWithZeroVat` (NPE on `vatPercent`).
**Fix:** treat a missing rate as 0 % (or refuse the configuration up front).

## 10. Mail status is wrong and mails can be sent twice (#18)

**Severity:** high · **Where:** `BillingDocumentMailService.java:106-114,144`

- The status is "check, then set" without a lock: two simultaneous send requests both send, so every member gets every mail twice.
- The run is marked "SENT" even when every mail failed.
- After an exception outside the per-document loop the status stays "IN PROGRESS" (not reproduced).

**Reproduced by:** `BillingDocumentMailServiceTests.runWhereEveryMailFailedIsNotMarkedSent`,
`BillingMailDeliveryTests.runWhereEverySmtpDeliveryFailedIsNotMarkedSent`,
`MailSendRaceTests.onlyOneOfTwoSimultaneousSendsSends`.
**Fix:** atomic status transition (conditional update), a failure status, `finally` for the final status.

## 11. Deleting a billing run with documents fails (#22)

**Severity:** medium · **Where:** `BillingRunService.java:67`

The foreign key from documents to the run has no cascade and the service does not delete the
documents first; the delete ends in a `DataIntegrityViolationException` → 500.

**Reproduced by:** `BillingScenarioErrorPathTests.s12_deletingACompletedRunRemovesItWithItsDocuments`.
**Fix:** delete documents, items and files of the run first (or cascade); decide whether completed runs may be deleted at all.

## 12. A record of another community gives 500, an unknown one 404 (#20)

**Severity:** medium · **Where:** `RestExceptionHandler.java:43-50`

The per-record tenant check throws `AccessDeniedException`, which the catch-all handler answers with
500, while an unknown id gives 404. Whether a record of another community exists is therefore
distinguishable. A missing `Tenant` header also gives 500 instead of 403.

**Reproduced by:** `EndpointMatrix.foreignTenantIsForbidden`, `EndpointMatrix.missingTenantHeaderIsForbidden` (31 rows each).
**Fix:** 403 (or 404 for both). **Contract change:** eegfaktura-v3 `BillingErrors.kt:29` maps 500 +
`AccessDeniedException` to "not found" — announce before changing.

## 13. Client errors end as 500 (#31)

**Severity:** medium · **Where:** `RestExceptionHandler.java:61-68`, `DoBillingParams.java`

The catch-all `@ExceptionHandler(Throwable.class)` also takes Spring's own 4xx exceptions:
unreadable JSON (`HttpMessageNotReadableException`), a malformed UUID in the path
(`MethodArgumentTypeMismatchException`), a missing multipart part. All answer 500. `DoBillingParams`
has no constraints, so `POST /api/billing` without `tenantId` is a 500, not a 400 with field errors.

**Reproduced by:** `BillingWebTests.bodyWithoutTenantIsBadRequestWithFieldErrors`,
`BillingWebTests.unreadableJsonIsBadRequest`, `RestExceptionHandlerWebTests.malformedIdIsBadRequest`.
**Fix:** extend `ResponseEntityExceptionHandler` (or handle the 4xx types explicitly); bean validation on `DoBillingParams`.

## 14. Footer and logo download endpoints are broken (#21)

**Severity:** medium · **Where:** `BillingConfigResource.java:94,103-108`

`GET /api/billingConfigs/{id}/footerImage` requires a multipart file parameter and cannot be called
as a plain GET. `GET …/logoImage` for a config without a logo gives 500 instead of 404.

**Reproduced by:** `BillingConfigImageWebTests.footerDownloadWorksWithoutAnUpload`,
`BillingConfigImageWebTests.logoDownloadWithoutALogoIsNotFound`.
**Fix:** remove the stray `@RequestParam MultipartFile`; 404 when no image is stored.

## 15. Replacing a config image deletes the old one first; wrong type gives 500 (#27)

**Severity:** medium · **Where:** `BillingConfigService.java:75-88`

The old image is deleted before the new one is stored and the config updated; if that fails, the
config has no image any more. An unsupported file type ends as 500 instead of 4xx.

**Reproduced by:** `BillingConfigImageStoreWebTests.oldLogoSurvivesAFailedUpdate`,
`BillingConfigImageStoreWebTests.wrongFileTypeIsAClientError`.
**Fix:** store new → update config → delete old; 400/415 for a wrong type.

## 16. Config images are stored without a tenant (#32)

**Severity:** medium · **Where:** `BillingConfigService.java:60-66`

`storeImage` never sets `FileData.tenantId` (only `BillingPdfService` does, for PDFs). Therefore
`GET /api/fileData/{id}` refuses every logo and footer image for every community (fails closed,
500 per issue 12). The images are reachable only through the config endpoints. Found by reading
the code, not reproduced.

**Fix:** set the config's tenant when storing; migrate existing rows.

## 17. `numberOfInvoices` and `numberOfCreditNotes` are never set (#33)

**Severity:** medium · **Where:** `BillingService.java:155-160`, `BillingRunService`

The columns and DTO fields exist and `GET /api/billingRuns/…` returns them, but no code writes
them — always `null`. The old integration test had the assertions commented out with a `@TODO`.

**Fix:** count when the run is completed; needs a decision what counts as a credit note
(`INFO`, `CREDIT_NOTE_RC`).

## 18. Master-data view of the backend is not checked at start (#10)

**Severity:** medium · **Where:** `domain/BillingMasterdata` (`@Subselect` on `base.billing_masterdata`)

The view is owned by the Go backend. `ddl-auto=validate` does not check a `@Subselect`, so a backend
migration that drops or renames a column breaks billing only at runtime, at the first billing run.

**Mitigated by:** `LegacyMasterdataViewContractTests`, `LegacyMasterdataEntityReadTests` (on the
copied backend SQL, refreshed by hand). Not seen until refreshed: the backend's
`20260711120000_zvt_time_tariff` on `feat/zvt-time-tariff`.
**Fix (proposal):** a start-up check of the mapped columns, or a CI job comparing the copy with the backend.

## 19. Profile `dev` switches authorization off (#7)

**Severity:** medium · **Where:** `DevSecurityConfig`

With profile `dev` there is no authorization rule and CSRF is off. Harmless locally; a full bypass
if the profile is ever activated on a server.

**Fix:** refuse to start with `dev` outside localhost, or require an explicit second flag.

## 20. Database login falls back to `postgres`/`postgres` (#8)

**Severity:** medium · **Where:** `application.properties`

When `JDBC_DATABASE_USERNAME` / `_PASSWORD` are missing the application tries the superuser login
`postgres`/`postgres`. The deployments set both; a forgotten variable should stop the start.

**Fix:** no default; fail at start.

## 21. Spring Boot 3.5.3 brings Tomcat and Spring Security with known CVEs (#5)

**Severity:** medium · **Where:** `pom.xml`

Spring Boot 3.5.3 brings Tomcat 10.1.42 and Spring Security 6.5.1 with known CVEs (CVSS ≥ 9 in
Tomcat; header suppression in Spring Security). Most need configuration billing does not have; the
escape-sequence and header issues apply.

**Fix:** update Spring Boot to the current 3.5.x patch release.

## 22. Shared `DecimalFormat` is not thread-safe (#30)

**Severity:** medium · **Where:** `BigDecimalTools.java:12-13`, used by `BillingPdfService`

`DECIMAL_FORMAT` is one static `java.text.DecimalFormat`, which is not thread-safe. The lock is per
community, so two PDFs of two communities can be rendered at the same time and print garbled
amounts. Suspicion from reading the code, not reproduced.

**Fix:** a formatter per call, or `ThreadLocal`.

## 23. Rounding of kWh and VAT needs a business decision (#23)

**Severity:** question · **Where:** `BillingService.java:417,451,605`

kWh are rounded to 2 places before pricing; VAT is rounded per line and then summed (not per rate).
Printed amounts use half-even rounding (0.125 → "0,12") and no thousands separator. Whether this is
correct is a business question; no test until it is answered.

## 24. Producer amounts negative, sum from positive values (#24)

**Severity:** question · **Where:** `ParticipantAmountService.java:50-52`

Producer meter amounts are negative while the sum is built from positive gross values. Accepted,
not changed in billing; consumers (v3 frontend) must cope.

## 25. Document year depends on the JVM time zone (#25)

**Severity:** low · **Where:** `BillingService.java:70,274`

`LocalDate.now()` in the JVM's zone decides the document year and with it the number sequence; at
year end a run may land in the wrong year.

**Fix:** inject a `Clock` with `Europe/Vienna`.

## 26. `BillingConfigService.DEFAULT` is a public mutable static entity (#26)

**Severity:** low · **Where:** `BillingConfigService`

The default configuration is a public, mutable static JPA entity passed into runs. Today nothing
changes it (`BillingScenarioFeeTests.f15_runsWithoutConfigurationUseTheUnchangedDefault` is green),
but any code that mutates it changes the default for every later run.

**Fix:** return a fresh copy per call.

## 27. Streams not closed, `printStackTrace()` for logging (#28)

**Severity:** low · **Where:** archive and XLSX export, `RestExceptionHandler.handleThrowable`

`ZipOutputStream` and `XSSFWorkbook` are not in try-with-resources; `handleThrowable` logs with
`printStackTrace()` instead of the logger.

## 28. Dead code in the lock and commented-out endpoints (#29)

**Severity:** low · **Where:** `InMemoryLockRepository.startCleanupTask`, several resources

`startCleanupTask` / `cleanupExpiredLocks` are private and never called; many endpoints are commented
out.

**Fix:** remove.
