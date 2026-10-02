# Agent log

One entry per AI session, newest first. Format: date, task, changes, decisions, verification, open.

## 2026-10-02 — M6: contract tests with v3 view SQL, web and backend; plan complete except M4

**Task.** Implement milestone M6 of `docs/improve-testting-environment/` (the last one) without touching
`src/main`: the real `base.billing_masterdata` view from the v3-hosted backend SQL in an own database, the
caller DTO contract of eegfaktura-web and eegfaktura-v3, response snapshots if time permits.

**Inventory.**
- Minimal ordered subset of v3 `docker/legacy-base/01..08` (v3 commit `ad120d1`): `01` (base tables), `02`
  (`activeMeteringPartition`, `activeTariff`, first view), `04` (bank/mandate columns, `creditor_id`), `05`
  (the view as of 2025-06-05, 64 columns). `03` only inserts grid operators, `06`–`08` only add table
  columns the view does not select. The four copies equal `eegfaktura-backend/migrations` at its HEAD
  `f4974b2` after v3's two-line header (checked); no newer backend migration on its main line; branch
  `feat/zvt-time-tariff` (`81ab6f8`) recreates the view.
- Columns: `BillingMasterdata` maps 62 columns (63 fields minus the generated `id`), all in the legacy view
  (64; unmapped: `eec_website`, `tariff_participant_version`); `billing_masterdata_v3` (V190, counted from the
  file) has 65 = the 64 legacy names + `eeg_id`.
- Callers (grep of `BILLING_API_SERVER` in web `src/service/eeg.service.ts` at `c37a0b7`, `uri(` in v3
  `BillingClient.kt` at `0b785d2`): web 18 calls on 18 endpoints, v3 20 calls on 20 endpoints, 21 of 31
  endpoints used. Fixtures per endpoint (web/v3): `POST /api/billing` 1/1 (+ body), `POST /api/billingConfigs`
  1/1 (+ body), `PUT /api/billingConfigs/{id}` 1/1 (+ body), `POST …/logoImage` and `…/footerImage` 1/1
  (multipart), `DELETE` both images 1/1, `GET …/logoImage` 1/1, `GET /api/billingConfigs/tenant/{tenantId}`
  and `/{id}` 1/1, `GET /api/billingRuns/{tenantId}/{type}/{identifier}`, `/{id}`, `/participantAmounts`,
  `/billingDocuments/xlsx`, `/archive`, `/sendmail` 1/1, `GET /api/fileData/{id}` 1/1,
  `GET /api/billingDocumentFiles/tenant/{id}` 1/0, `GET /api/billingRuns/{id}/billingDocuments`,
  `GET /api/billingDocuments/tenant/{id}/{year}`, `GET /api/billingDocuments/{id}` 0/1
  (`grep -c '"method"'`: web 18, v3 20). DTOs: `DoBillingParams`/`Allocation`, `BillingConfigDTO`; read:
  `DoBillingResults`, `BillingRunDTO`, `ParticipantAmount`, `BillingDocumentDTO`, `BillingDocumentFileDTO`,
  `BillingConfigDTO`, `ErrorResponse`.

**Changes.** New `support/LegacyBaseDatabase` (own `postgres:15-alpine`, applies the four scripts once per
JVM), `contract/MasterdataColumns` (entity columns through Hibernate's `CamelCaseToUnderscoresNamingStrategy`,
view columns from `information_schema`, Java type → readable `data_type`, V190 alias parser),
`contract/LegacyMasterdataViewContractTests` (7), `contract/LegacyMasterdataEntityReadTests` (6,
`@DataJpaTest` on the own container, rows in `contracts/legacy-masterdata-rows.sql`),
`contract/ContractFixtures`, `contract/CallerEndpointContractTests` (78 executions),
`contract/CallerRequestContractTests` (12, `@WebMvcTest` with `@Import(JacksonConfig)`),
`contract/CallerResponseContractTests` (9, `@JsonTest` with `JacksonConfig`). Resources:
`legacy-base/` (4 copied files + README), `contracts/` (README, two endpoint inventories, six body
fixtures, seven response snapshots, `v3view/V190__billing_masterdata_v3.sql`). `pom.xml`: domain line floor
0.88 → 0.94. Docs: `known-errors.md` #10 mitigated, #20 note, #34 new; `open-points.md` B-25 new;
`EXTERNAL_SOURCES.md` three rows and the PostgreSQL row; m06 (ticks, result); README (status, "Plan status").

**Decisions.** Copied files byte-identical from v3 (B-16): `cmp` against v3 at copy time, sha256 pinned in
the test so a local edit fails; the v3 working tree was clean at `0b785d2`. Plain JDBC for the column check
(fast, message names the column) plus a Hibernate read through the real view (the true contract: ordinal
enum, `uuid` → `String`, `real`/`double precision`/`integer` → `BigDecimal`, `date` → `LocalDate`). The
request tests import `JacksonConfig`, which a `@WebMvcTest` slice does not pick up (the M2 slices run with
Boot's defaults; they differ only in `ACCEPT_FLOAT_AS_INT`, noted, not changed). Unknown fields: tolerated
because `JacksonConfig` disables `FAIL_ON_UNKNOWN_PROPERTIES`; the `isPreview` trap is pinned as today's
behaviour and recorded as #34/B-25, not as a defect test (a safer default is a contract decision). Endpoint
routing is checked against the M2 `EndpointTable` with Spring's `PathPatternParser` (most specific match),
which the M2 guard ties to the live mappings. v3 view by name only (its schema needs V1..V190). Response
snapshots done (optional item). No new dependency, no `CHANGELOG.md` entry (test-only, as M5).

**Acceptance.** Throw-away change of the copied `05_…` (`eec_city` → `eec_town`, `participant_sepa_direct_debit`
dropped), run once: `everyEntityColumnExistsInTheViewWithAReadableType` failed with "missing column eec_city
(String field of BillingMasterdata), missing column participant_sepa_direct_debit (String field of
BillingMasterdata)", also red: the 64-column count (63), the sha256, the v3 name check (`eec_town`), the
type and rename tests; restored with `cp` from v3 + `cmp`. Same for the caller side once: a misspelt web
path (`/api/billingRun/…`) failed with "no billing endpoint for eegfaktura-web GET /api/billingRun/RC100001/YM/
Abr_YM-2024-6 (eeg.service.ts:152 fetchBillingRun)", a renamed snapshot field failed the `BillingRunDTO`
snapshot; both restored.

**Disabled tests.** None new (no M6 test needed one); `grep -rn '@Disabled' src/test | grep -v 'known-errors
#[0-9]'` prints nothing.

**Verification.** Each class alone in `maven:3-eclipse-temurin-21` (view classes 7 + 6, caller classes
78 + 12 + 9, all green on the first run); `target/` removed with the alpine command; then one `mvn -B clean
verify`: **490 tests, 0 failures, 0 errors, 47 skipped** (M6: 112 executions in 5 classes, 0 skipped), all
coverage checks met, 36 s wall clock (M6 classes about 1.8 s in surefire plus one JPA and two slice context
starts). JaCoCo CSV of the clean run: bundle lines **81.41 %** (1502/1845), branches **83.33 %** (285/342);
`domain` 94.44 % (17/18) / 75 %, the others unchanged from M5 (`service` 77.31 % / 80.63 %, `rest` 100 %,
`controller` 100 %, `security` 88.12 % / 83.33 %, `util` 96.15 % / 94.34 %, `config` 90.48 %, `repos`
70.21 % / 92.86 %, `model` 92.31 %). Raised floor checked with `jacoco:check@check` against the same run's
data. `git diff --stat src/main` empty; `loc-check.sh`: all new files green (largest
`CallerRequestContractTests` 227 lines; copied SQL max 219).

**Open.** Copies are refreshed by hand (README steps; no CI diff job); the backend's `feat/zvt-time-tariff`
view change; #34/B-25; fixing #20 is a contract change for v3. Plan: M0–M3, M5, M6 done, M4 on the zvt
branch; the remaining open items are listed in the README's "Plan status".

## 2026-10-02 — M5: concurrency and mail tests (GreenMail); F3/F4/F7 integration defect tests

**Task.** Implement milestone M5 of `docs/improve-testting-environment/` without touching `src/main`: parallel runs
through the resource bean with deterministic latches, mail against a real SMTP server, F3/F4/F7 as integration defect
tests.

**Changes.** New `support/GreenMailHolder` (one GreenMail per JVM on a dynamic port, SMTP user and test-only login
because the test properties demand `mail.smtp.auth`), `support/Gate` (latches and a bounded barrier; every wait has a
timeout and a failure message), `support/ConcurrencyAndMailBase` (extends M3's `BillingScenarioBase`; `@MockitoSpyBean`
on `BillingService`, `InMemoryLockRepository`, `EmailService`, `BillingRunRepository`, `BillingDocumentNumberRepository`
declared once so the M5 classes share one context; worker pool with `TenantContext` per thread; GreenMail purge and
mail-sender port reset per test). New `concurrency/ConcurrentBillingRunTests` (4), `concurrency/MailSendRaceTests` (1),
`mail/BillingMailDeliveryTests` (4). `pom.xml`: `com.icegreen:greenmail` 2.1.14 (test, `junit:junit` excluded).
`known-errors.md` #14, #15, #18 (reproduction notes), `open-points.md` B-12 (GreenMail added), `EXTERNAL_SOURCES.md`
(GreenMail row), m05 (ticks, result), README status.

**Decisions.** GreenMail 2.1.14: Maven Central `last-modified` 2026-09-19 (13 days, latest release), Apache-2.0 from
the `<licenses>` of `com.icegreen:greenmail-parent:2.1.14`. Jakarta Mail check: GreenMail declares `jakarta.mail-api`
2.1.5 / Angus `jakarta.mail` 2.0.5 / `jakarta.activation-api` 2.1.4; Spring Boot 3.5.3 manages them to 2.1.3 / 2.0.3 /
2.1.3 — same Jakarta Mail 2.1 line, the mail tests are green with it. **Core artifact instead of `greenmail-junit5`**
(spec deviation): the extension restarts the server with a new port per class while the cached context keeps the old
one; the holder pattern of `PostgresContainerHolder` avoids that, so the JUnit 5 wrapper would be unused. The lock test
calls `BillingResource.getAllInvoices` with a stubbed `doBilling` (A held, B has fetched A's lock object — hooked in the
`getLock` spy —, A leaves, B inside, D enters with a new object); D's entry is awaited with the bounded 1 s
`Gate.NOT_EXPECTED`, spent only once the lock is fixed. The F4 test runs at the **service** level (the generator itself
has no lock; a second instance would race the same way): a barrier after `getMaxSequenceNumber` lets both runs read the
maximum before either saves. Part two of the spec (two real runs back to back) goes through the resource and is green:
with two runs the old monitor still serialises them. Mail race on the service with a barrier in `saveAndFlush("IN
PROGRESS")`. SMTP failure = the real `JavaMailSenderImpl` pointed at a closed local port (connection refused), after the
first mail for "mid-batch" (hook in the `EmailService` spy), restored after each test. Spring Data spies cannot
`callRealMethod()` ("abstract real method"); `proceed(spy, invocation)` uses the spy's delegating default answer. The
"stuck in IN PROGRESS" aspect of #18 is not tested (needs an exception outside the per-document loop, i.e. a failing
repository); mid-batch failure leaves "SENT" (green). Floors not raised: the clean run gives exactly M3's figures.
No `CHANGELOG.md` entry (test-only, the spec asks for none).

**Disabled defect tests** (each run once enabled with `-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition`
on 2026-10-02, all red):
- `ConcurrentBillingRunTests.runsOfOneCommunityNeverOverlap` — `@Disabled("known-errors #14")` (F3): "run D entered
  doBilling while run B was inside", expected false, was true.
- `ConcurrentBillingRunTests.numbersOfParallelRunsInTheServiceAreDistinct` — `@Disabled("known-errors #15")` (F4): second
  run failed with `UnexpectedRollbackException` (log: duplicate key `ukhbmeftd1x7fdlqtqqg2v20aev`, `TRECH202400042`
  twice). (A first enabled attempt failed on the spy's `callRealMethod` instead; fixed with `proceed`, then red for the
  right reason.)
- `BillingMailDeliveryTests.runWhereEverySmtpDeliveryFailedIsNotMarkedSent` — `@Disabled("known-errors #18")` (F7):
  expected not "SENT"/"IN PROGRESS", was "SENT".
- `MailSendRaceTests.onlyOneOfTwoSimultaneousSendsSends` — `@Disabled("known-errors #18")` (F7): calls that sent expected
  1, was 2.

**Verification.** The three classes alone, then enabled once (above); then **20 consecutive runs** of the three classes
in one `maven:3-eclipse-temurin-21` container (`mvn -o -q test -Dtest=…`): 20 × exit 0, every run 9 tests, 0 failures,
4 skipped. Then one `mvn -B clean verify`: **378 tests, 0 failures, 0 errors, 47 skipped** (M5: 9 in 3 classes, 4
skipped), all coverage checks met, 34 s wall clock (M5 classes about 11 s in surefire, mostly the extra context start).
JaCoCo CSV of the clean run: bundle lines 81.36 % (1501/1845), branches 83.33 % (285/342); `service` 77.31 % / 80.63 %;
`EmailService` 100 % (27/27), `BillingDocumentMailService` 95.83 % (92/96) — targets ≥ 85 % met (already by M1);
`rest` and `controller` 100 %; all other packages as in M3. `grep -rn '@Disabled' src/test | grep -v 'known-errors
#[0-9]'` prints nothing; no `Thread.sleep` in the new files; `git diff --stat src/main` empty; `loc-check.sh`: new files
green (largest `ConcurrentBillingRunTests` 215 lines).

**Open.** Enable the four tests with the fixes of #14 (M4b), #15, #18. If M4b moves the lock into the service, rewire
`runsOfOneCommunityNeverOverlap` to the service entry point. "Stuck in IN PROGRESS" (#18) has no test.

## 2026-10-02 — M3: billing scenarios S1 – S12 (S11 waits for B-13)

**Task.** Implement milestone M3 of `docs/improve-testting-environment/` without touching `src/main`: the scenario
matrix S1 – S12 on Testcontainers PostgreSQL, PDF/XLSX checks, the clean-up T1/T2/T3/T4/T7/T8/T10 of the old
classes, the optional v3 items only if cheap.

**Changes.** New `support/BillingScenarioBase` (abstract `@SpringBootTest` on `PostgresContainerHolder`, not
transactional, one community id per test, `@AfterEach` delete of all its rows incl. `base.billing_masterdata`,
fixed `REFERENCE_DATE` 2024-06-28, run/read/assert helpers; meant for M5 as well) and `support/DocumentReaders`
(PDFBox text, POI rows and column sums, ZIP entries). New `scenario/BillingScenarioVatTests` (S1, S3, S4, S5),
`BillingScenarioFeeTests` (S6, S7, `f15_…`), `BillingScenarioErrorPathTests` (S8, S9, S10, S12),
`BillingScenarioOutputTests` (S2, PDF text and XLSX sums of S1/S2, archive), `BillingArithmeticOracleTests`
(optional item 2) with `src/test/resources/v3oracle/billing-arithmetic-cases.json`. Old classes:
`BillingIntegrationTests` (739 → 658 lines: placeholders removed, one `params(...)` helper instead of four copies
of the allocation loop, result text and run asserted in `assertRun`, XLSX test asserts 5/9 rows and the gross
sum 1365.32, items sorted, document date between run day and today, Sonne invoice VAT 20 % / 5.98 restored,
Sonne participant amount matched on either meter point, more master-data properties instead of the `@TODO`),
`DocumentNumbersTests` (placeholders removed), `BuilderCrossCheckTests` (result text). Participant id
`039e8d60-…-0c31aa53a49` fixed to `…-0c31aa53a490` in `TEST_ALLOCATIONS`, `billing_master_data.sql` and
`BillingRunFixture` (T10). `pom.xml`: PDFBox 3.0.8 (test) and the floors bundle 0.81/0.83, `service` 0.77/0.80.
`known-errors.md` #33 (new) and reproduction notes on #12, #13, #16, #17, #22, #26; `open-points.md` B-24 (new),
B-6 updated; `EXTERNAL_SOURCES.md` (PDFBox, v3 golden file); `CHANGELOG.md`; m03 (ticks, result), m05 (base class
name), README status.

**Decisions.** All scenarios non-transactional with per-tenant cleanup (one rule instead of a mix; the spec allows
the rest to stay transactional). Numbers are asserted exactly (`TRECH202400042`, `TGUT202400073`), participant
order is never assumed (sets or lookup by participant). S4 and S8 each have an enabled sibling that pins what holds
today (failure text; zero participant has no document in the run). **S9 is green and stays enabled**: both
refusals are thrown before the first save, so F1 cannot leave data there — a deviation from the spec's table, which
lists S9 under F1. F15 is green (the run path leaves `DEFAULT` untouched). PDFBox 3.0.8: Maven Central
`last-modified` 2026-07-08 (> 7 days), latest release, Apache-2.0 from the POM header and the `org.apache:apache:39`
parent; `pdfbox-io`/`fontbox` 3.0.8 come along, Bouncy Castle is optional and not pulled, `commons-logging` stays at
1.3.5 (JasperReports). Optional item 2 (oracle) done: rows 1–4 of v3's golden file (v3 commit `0b785d2`, file from
`f6540a0`, byte-identical, sha256 `830d846c…`), 10 cases, all agree with billing; the item VAT rate is not compared
(billing stores the tariff rate even with VAT off, v3 0); row 5 left out because billing never bills
`tariff_basic_fee` (B-24), row 14 is no billing concept. **Optional item 1 (v3 world snapshot) not done**: the
conversion step from the v3 manifest to `billing_masterdata` rows does not exist (about a day).
`numberOfInvoices`/`numberOfCreditNotes` are never set (#33): the commented assertions are replaced by a pointer,
no test (what counts as a credit note is a decision).

**Disabled defect tests** (each run once enabled with `-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition`
on 2026-10-02, all red):
- `BillingScenarioVatTests.s04_threeVatRatesFailAndLeaveNothingBehind` — `@Disabled("known-errors #12")` (F1):
  expected 0 documents of the tenant, was 3.
- `BillingScenarioVatTests.s05_sameRateWithOtherScaleIsOneVatSum` — `@Disabled("known-errors #16")` (F5): VAT sum 1
  expected 4.00, was 2.00.
- `BillingScenarioFeeTests.s07_participantFeeWithoutVatRateIsBilledWithZeroVat` — `@Disabled("known-errors #17")`
  (F6): expected the preview success text, was "Abrechnung fehlgeschlagen: Cannot invoke
  `java.math.BigDecimal.compareTo(java.math.BigDecimal)` because `vatPercent` is null".
- `BillingScenarioErrorPathTests.s08_participantWithAmountZeroLeavesNoDocumentWithoutRun` —
  `@Disabled("known-errors #13")` (F2): expected 0 documents without run, was 3.
- `BillingScenarioErrorPathTests.s12_deletingACompletedRunRemovesItWithItsDocuments` — `@Disabled("known-errors #22")`
  (F11): `DataIntegrityViolationException` (FK `fk6gouorockuin30j66lev4xmky`).

**Verification.** Each new or changed class alone (`-Dtest=<Name>`, container with the Docker socket), the five
disabled ones also enabled; then one `mvn -B clean verify` in `maven:3-eclipse-temurin-21`: **369 tests, 0 failures,
0 errors, 43 skipped** (M3: 31 test executions in 5 new classes, 5 skipped; old classes 7 + 5 + 1), all coverage checks
met; the raised floors checked with `jacoco:check@check` against the same run's data. JaCoCo CSV of the clean run:
bundle lines **81.36 %** (1501/1845), branches **83.33 %** (285/342); `service` 77.31 % / 80.63 %; `BillingService`
lines 98.05 % (352/359), branches **88.70 %** (102/115); `BillingPdfService` 94.78 % / 92.31 %,
`BillingDocumentXlsxService` 99.01 % / 84.38 %, `BillingDocumentArchiveService` 100 % / 75 %; the other packages
unchanged from M2 (`rest` 100 %, `controller` 100 %, `security` 88.12 % / 83.33 %, `util` 96.15 % / 94.34 %, `config`
90.48 %, `repos` 70.21 % / 92.86 %, `domain` 88.89 % / 75 %, `model` 92.31 %). Targets of the spec (total lines ≥ 75 %,
branches ≥ 70 %, `BillingService` branches ≥ 75 %) met. Suite time (surefire sum): after 24.3 s, of which the
pre-existing classes 12.2 s (before-figure taken from the same run; M2 recorded no total), M3 classes 12.1 s
(9.8 s of it the first class with the Spring context start and Jasper compile); whole `clean verify` 34 s wall
clock. `grep -rhoE 'void s(0[1-9]|1[0-2])_' src/test | sort -u | wc -l` = 11 (S11 waits for B-13, no placeholder);
`f15_…` exists; `grep -rn '@Disabled' src/test | grep -v 'known-errors #[0-9]'` prints nothing; no `Thread.sleep`;
`git diff --stat src/main` empty; `loc-check.sh`: all new files green (largest `BillingScenarioBase` 240 lines);
`BillingIntegrationTests` still blocked at 658 (B-6).

**Open.** S11 waits for B-13; B-24 (base fee); #33; optional v3 snapshot not done; the five disabled scenarios are
enabled with the fixes of #12, #13, #16, #17, #22 (M4d or own changes). The criterion "only `pom.xml` gains PDFBox"
holds with the instructed floor changes in the same file.

## 2026-10-02 — M2: web-layer slice tests and tenant matrix for the 31 endpoints

**Task.** Implement milestone M2 of `docs/improve-testting-environment/` on `master` as it is (B-15: no
`fix-tenant-claim` merge; B-14: no token stays 403) without touching `src/main`.

**Changes.** New in `src/test/java/.../rest/`: `WebSliceTest` (annotations only: `@Import` of `JwtSecurityConfig`,
`JwtTokenService`, `InMemoryLockRepository`, `@EnableConfigurationProperties(AppProperties)`, the certificate via
`@TestPropertySource`), `TestTokens` (java-jwt, claims `tenant`, `access_groups`, `preferred_username` only),
`Endpoint` + `EndpointTable` (31 rows: method, Spring pattern, tenant kind, own status, body, multipart, invalid
body), the abstract matrix `EndpointMatrix`, one `@WebMvcTest` per resource (`BillingRunWebTests`,
`BillingConfigCrudWebTests` + `BillingConfigImageWebTests` over the shared `BillingConfigWebSlice`,
`BillingDocumentWebTests`, `BillingDocumentFileWebTests`, `BillingDocumentItemWebTests`,
`BillingDocumentNumberWebTests`, `BillingWebTests`, `FileDataWebTests`), `EndpointMappingGuardTests`,
`RestExceptionHandlerWebTests`, `BillingConfigImageStoreWebTests`; `security/JwtRequestFilterWebTests`. Test-only
key pair and certificate in `src/test/resources/jwt/` (README with the `openssl` command, CN "test-only - NOT A
SECRET"). `pom.xml`: JaCoCo floors raised (bundle 0.78/0.75, service 0.73/0.69, util 0.96, model 0.92, config 0.90,
security 0.88 + new branch rule 0.83, rest 1.00, controller 1.00). `known-errors.md` #31, #32 (new); m02 and README
status done.

**Decisions.** Matrix cases per row as the spec's table; besides each disabled 403 row (foreign tenant, missing
header) an enabled row asserts what holds today: refused (non-2xx) and no service call except the lookup `get` —
this protects the per-record comparison without blessing the 500. No token and no `EEG_ADMIN` assert 403 and no
service call. `GET …/footerImage` gets a multipart part in the matrix (#21 has its own disabled plain-GET test).
`POST /api/billing` has no invalid-body row (no constraints on `DoBillingParams`); its 400 cases are disabled
under the new #31, together with unreadable JSON and a malformed UUID (catch-all handler → 500). F16 is tested
through the real `BillingConfigService` with mocked repositories, the rest of the config slice mocks the service.
The error-format probe controller is nested in its test class (excluded from other slices by
`TestTypeExcludeFilter`) and mapped outside `/api`. `@ParameterizedTest(allowZeroInvocations = true)` for the
id/invalid-body rows (not every resource has them). No new dependency.

**Disabled defect tests** (all run once enabled with `-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition`
on 2026-10-02: 101 executions, all red):
- `EndpointMatrix.foreignTenantIsForbidden` — `@Disabled("known-errors #20")`, inherited by the 9 resource
  classes, 31 rows: expected 403, was 500.
- `EndpointMatrix.missingTenantHeaderIsForbidden` — `@Disabled("known-errors #20")`, 31 rows: expected 403, was 500.
- `EndpointMatrix.headerTenantNotInTokenClaimIsForbidden` — `@Disabled("known-errors #1")`, 31 rows: expected 403,
  was 200/201/204 (the filter's check never matches); enabled by the merge of `fix-tenant-claim`.
- `BillingConfigCrudWebTests.updateOfAForeignStoredConfigIsForbidden` — `@Disabled("known-errors #19")` (F8):
  expected 403, was 200; enabled by the merge of `fix-tenant-claim`.
- `BillingConfigImageWebTests.footerDownloadWorksWithoutAnUpload` — `@Disabled("known-errors #21")` (F10):
  expected 200, was 500.
- `BillingConfigImageWebTests.logoDownloadWithoutALogoIsNotFound` — `@Disabled("known-errors #21")` (F10):
  expected 404, was 500.
- `BillingConfigImageStoreWebTests.oldLogoSurvivesAFailedUpdate` — `@Disabled("known-errors #27")` (F16):
  `deleteById(old logo)` was invoked before the failing update.
- `BillingConfigImageStoreWebTests.wrongFileTypeIsAClientError` — `@Disabled("known-errors #27")` (F16):
  expected 4xx, was 500.
- `BillingWebTests.bodyWithoutTenantIsBadRequestWithFieldErrors`, `BillingWebTests.unreadableJsonIsBadRequest`,
  `RestExceptionHandlerWebTests.malformedIdIsBadRequest` — `@Disabled("known-errors #31")`: expected 400, was 500.

**Verification.** Each new class alone (`-Dtest=<Name>`, container without the Docker socket — the M2 classes need
no Docker), then one `mvn -B clean verify` in `maven:3-eclipse-temurin-21` with the socket: 342 tests, 0 failures,
0 errors, 38 skipped (M2: 258 executions in 13 classes, 35 skipped; about 6 s together in surefire, context start included), all coverage
checks met; the raised floors checked with `jacoco:check@check` against the same run's data. JaCoCo CSV of the
clean run: bundle lines 78.48 % (1448/1845), branches 75.44 % (258/342); `rest` 100 % lines (184/184; no
branches); `security` 88.12 % lines (89/101), 83.33 % branches (15/18); `config` 90.48 %; `controller` 100 %;
`service` 73.36 % / 69.96 %; `util` 96.15 % / 94.34 %; `model` 92.31 %. Targets of the spec: `rest` ≥ 85 % met,
total lines ≥ 65 % and branches ≥ 60 % met; `security` 100 % branches not reachable on `master` (spec). Unreached
`security` branches: `JwtRequestFilter` tenant check true-branch (dead, #1; lines 47-48 with it),
`TenantContext.validateTenant` "no tenant set" (unreachable, `TenantFilter` always sets an `Authority`) and
`tenant == null` (reachable only with a stored record without tenant, e.g. images, #32; not tested). Missed lines
besides: `DevSecurityConfig` (profile `dev`, never activated), `JwtAuthentication.getCredentials/getDetails/
setAuthenticated`, implicit constructors. Guard: 31 of 31 `/api/**` handlers, a removed row is reported
(`aMissingRowIsReported`). `grep -rn '@Disabled' src/test | grep -v 'known-errors #[0-9]'` prints nothing;
`git diff --stat src/main` empty; `loc-check.sh`: all new files green (largest 178 lines).

**Open.** Enable the #1/#19 rows (and give `TestTokens` `iss`/`azp`) with the merge of `fix-tenant-claim`; #20,
#21, #27, #31, #32 need production fixes; the `rest` and `controller` floors are 1.00, so any new uncovered
resource line fails the gate (intended: the guard demands a row anyway). The criterion "`git diff --stat src/main
pom.xml` is empty" again holds for `src/main` only (floors raised as instructed).

## 2026-10-02 — M1: cheap unit tests, F3/F4/F7 as disabled defect tests

**Task.** Implement milestone M1 of `docs/improve-testting-environment/` (unit tests without Spring or Docker)
without touching `src/main`.

**Changes.** New test classes `util.BigDecimalToolsTests`, `util.StringToolsTests`,
`repos.BillingDocumentNumberGeneratorTests`, `repos.InMemoryLockRepositoryTests`, `service.EmailServiceTests`,
`service.BillingDocumentMailServiceTests`, `service.ParticipantAmountServiceTests`, `domain.BillingDocumentTests`;
`ClearingPeriodIdentifierToolTests` extended by the production form `Abr_YQ-2023-3`. `pom.xml`: JaCoCo floors
raised to the new figures, rounded down (bundle 0.63/0.69, service 0.71/0.67, util 0.93/0.94, repos 0.70/0.92
line/branch; the others unchanged). `known-errors.md` #30 (new), `open-points.md` B-13 (note on printed rounding),
m01 and README status done.

**Decisions.** The M0 builders insert database rows and are not used by unit tests; entities are built with their
Lombok builders. One disabled test per defect, as the spec counts exactly three; F7's second aspect (status stuck
in "IN PROGRESS" after an exception outside the per-document loop) is left to M5's SMTP-failure test, the unit
test covers "SENT although every mail failed". The F4 test asserts behaviour, not implementation: " R" must
continue the sequence of "R". The F3 test is deterministic with latches; B waits for C inside its section with a
bounded 1 s latch wait (no sleep), which is only spent once the lock is fixed. `BigDecimalToolsTests` records
today's printed rounding (`HALF_EVEN`, no thousands separator) and points to B-13 instead of calling it a defect.
Disabled tests run enabled once with `-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition`.

**Disabled defect tests** (each red when enabled, 2026-10-02):
- `InMemoryLockRepositoryTests.threeThreadsOfOneTenantNeverOverlap` — `@Disabled("known-errors #14")` (F3):
  expected 1 thread inside, was 2.
- `BillingDocumentNumberGeneratorTests.prefixWithBlankContinuesTheSequenceOfTheTrimmedPrefix` —
  `@Disabled("known-errors #15")` (F4): expected "R202400042", was "R202400000".
- `BillingDocumentMailServiceTests.runWhereEveryMailFailedIsNotMarkedSent` — `@Disabled("known-errors #18")`
  (F7): expected not "SENT", was "SENT".

**Verification.** Each class alone (`-Dtest=<Name>`), then one `mvn -B clean verify` in
`maven:3-eclipse-temurin-21` with the Docker socket: 84 tests, 0 failures, 0 errors, 3 skipped, all coverage
checks met; the raised floors checked with `jacoco:check` against the same run's data. The new unit classes take
about 2.4 s together (surefire). JaCoCo CSV of the clean run: bundle lines 63.25 % (1167/1845), branches 69.59 %
(238/342); service 71.80 % / 67.98 %, util 93.59 % / 94.34 %, repos 70.21 % / 92.86 %. Line coverage per target:
`StringTools` 100 % (3/3), `EmailService` 100 % (27/27), `BillingDocumentNumberGeneratorImpl` 100 % (16/16),
`ParticipantAmountService` 100 % (44/44), `BillingDocumentMailService` 95.8 % (92/96), `BigDecimalTools` 87.5 %
(7/8, target 95 %: the missed line is the implicit public constructor, excluding it needs production code),
`InMemoryLockRepository` 44 % (11/25, target 80 %: all reachable lines covered, the 14 missed are the private,
never called cleanup task, F18). `grep -rn '@Disabled' src/test | grep -v 'known-errors #[0-9]'` prints nothing;
`git diff --stat src/main` empty; `loc-check.sh`: all new files green (largest 238 lines).

**Open.** Lock expiry and cleanup task wait for M4 (4a/4b); #30 (shared `DecimalFormat`) needs a production
fix; the m01 criterion "`git diff --stat src/main pom.xml` is empty" contradicts the task to raise the floors in
`pom.xml` — read as `src/main` only.

## 2026-10-01 — M0: CI test job, JaCoCo with thresholds, lombok.config, test builders

**Task.** Implement milestone M0 of `docs/improve-testting-environment/` without touching `src/main`.

**Changes.** `pom.xml` (JaCoCo 0.8.13: prepare-agent, report in `test`, check in `verify`, per-package floors;
surefire `argLine=@{argLine}`), `lombok.config`, `.github/workflows/test.yml` (new, reusable, on `pull_request`
and `workflow_call`), `rolling-release.yml` (job `test`, `needs: test` on the image job), test support classes
`PostgresContainerHolder`, `BillingMasterdataBuilder`, `AllocationBuilder`, `BillingRunFixture`,
`BuilderCrossCheckTests`, `src/test/resources/base_masterdata_ddl.sql`, `show-sql=false`; docs: repo README
(`mvn clean`), `HOWTO-run-tests.md`, concept §2.2/§8, m00 ticked, EXTERNAL_SOURCES, CHANGELOG, open-points
(B-2, B-11 decided; B-23 new), known-errors #6 mitigated, AGENTS.md CI line.

**Decisions.** JaCoCo stays on 0.8.13 (approved B-11; 0.8.14 published 2025-10-11 and 0.8.15 2026-06-04 exist,
B-23). Actions pinned by SHA, verified with `git ls-remote` and the GitHub API: checkout v4.2.2
(11bd719, the SHA `snyk.yml` already uses), setup-java v6.0.1 (de7274f, 2026-09-09), upload-artifact v7.0.1
(043fb46, 2026-04-10), all older than 7 days. No new test dependency.

**Verification.** No local JDK 21/Maven (host JDK 25): ran in `maven:3-eclipse-temurin-21` with the docker.sock
mount (command in `HOWTO-run-tests.md`). Baseline without `lombok.config` and with it, 23 tests, `mvn clean`:
both 1026/1845 lines (55.61 %), 192/342 branches (56.14 %), CSV identical — Lombok 1.18.38 marks generated code
by default. Final `clean verify`: 24 tests green, lines 55.61 %, branches 57.31 %, coverage checks met.
Raising `rest` to 0.18 failed `jacoco:check` as expected. YAML parsed with python; **the real CI run is not
verified**, neither are the throw-away-branch checks nor the jar class-list diff. `git diff --stat src/main` empty.

**Open.** First CI run on GitHub; B-23; thresholds to be raised after M1.

## 2026-10-01 — Concept: improve the test coverage (no code change)

**Task.** The user: a concept how to improve test coverage **without changing any code**; errors
found are noted, not fixed; as a Markdown file in `docs/improve-testting-environment/`.

**Changes.** `docs/improve-testting-environment/test-coverage-concept.md` (English since the review; was `konzept-testabdeckung.md`): measured
baseline, weaknesses of the existing tests (T1–T12), 18 defects (F1–F18), test obstacles, six
phases, target figures. `known-errors.md` #12–#29 (= F1–F18), `open-points.md` B-11–B-13. No file
under `src/` or `pom.xml` changed.

**Verification.** Coverage measured with JaCoCo 0.8.13 from the command line (not in the POM),
`mvn clean` first, JDK 21 builder image: 23/23 green, lines 55.6 % (1026/1845), branches 56.1 %
(192/342); `rest` 17.9 %, `security` branches 0 %. The defects come from reading the code (a
read-only review plus spot checks of F1, F2, F3, F5, F10 by hand); none was reproduced by a run.

**Open.** B-11–B-13; the phases need the user's go one by one.

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
