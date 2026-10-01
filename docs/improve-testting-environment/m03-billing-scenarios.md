# M3 — Billing scenarios S1 – S12

**Concept:** phase 3 · **Status:** open · **Production code:** none
**Depends on:** M0 (builders, `PostgresContainerHolder`); PDFBox approved (`open-points.md` B-12); business answer B-13 for S11
(S11 waits, it does not block the milestone). **Effort:** 4 – 5 days (scenarios 3, helpers and cleanup 1 – 2).

## Goal

The business-critical result — documents, items, net/VAT/gross, document numbers, run status and the
result text — is pinned by a matrix of small integration scenarios instead of one large fixture.

## Scope

`@SpringBootTest` + Testcontainers PostgreSQL (`postgres:15-alpine`, Flyway migrations `V1_0` …),
one abstract base class on M0's `PostgresContainerHolder` (singleton container, started once per
JVM; the two old classes keep their own per-class container and context, so expect up to three
contexts in the full run) so the Spring context and the Jasper template are built once for M3. Each scenario builds a small data set with the M0 builders (rows in the **table**
`base.billing_masterdata`, created by `base_masterdata_ddl.sql`), runs `BillingService.doBilling`
(preview and/or final) and asserts: document types, net/VAT/gross per document, sums per VAT rate,
document numbers, run status **and** `DoBillingResults.abstractText` (T3). Items are sorted in the
test or read with an explicit `ORDER BY` (T4). Dates come from a fixed reference, not from
`LocalDate.now()` (T7).

**Transaction rule.** The old classes are `@Transactional` (rollback per test). Scenarios whose point
is what survives a failed run (S4, S9, S12, and S8 for the run assignment) must **not** run inside
the test transaction: `doBilling` has to commit or roll back on its own, otherwise F1 is invisible.
These scenarios clean up with `DELETE`/`TRUNCATE` in `@AfterEach` — including the rows the builders inserted into `base.billing_masterdata` (committed, since no test transaction wraps them); the rest may stay transactional.

| # | Scenario | Covers | Defect |
|---|---|---|---|
| S1 | consumers only, one VAT rate | base case | – |
| S2 | consumers and producers, reverse charge vs. info credit note | document-type choice | – |
| S3 | two VAT rates (10 % / 20 %) | second VAT sum | – |
| S4 | three VAT rates | error path; no documents, no consumed numbers | F1 (#12) |
| S5 | same rates, different scale (20 / 20.00) | rate comparison | F5 (#16) |
| S6 | free kWh larger / equal / smaller than consumption | free kWh | – |
| S7 | discount, participant fee, meter-point fee, fee without VAT rate | fees | F6 (#17) |
| S8 | participant with amount 0 | documents without a run; `GET /api/billingDocuments/tenant/{id}/{year}` via service | F2 (#13) |
| S9 | document date in the future; run already completed | error paths, nothing saved | F1 (#12) |
| S10 | preview → final → preview | old documents removed, numbers only when final | – |
| S11 | rounding boundaries (x.xx5 €, tiny quantities) | rounding | F12 (#23), after B-13 |
| S12 | delete a run after final billing (`BillingRunService.delete`) | FK without cascade | F11 (#22) |

**Outputs.** Extract PDF text with PDFBox (`Loader.loadPDF`, `PDFTextStripper`) and check amount,
VAT, document number and name; read the XLSX with POI and check sums and row count (T2).

## Defect tests

S4, S5, S7 (VAT-less fee), S8, S9, S12 expose F1, F5, F6, F2, F11. They assert the **correct**
result and are `@Disabled("known-errors #NN")` until the fix lands (numbers in the table). S4/S9 must
assert the database state (no documents, no consumed document numbers), not only the result text.
S11 waits for B-13: no test until then. The existing five-document scenario in
`BillingIntegrationTests` stays as the regression baseline. Cleanup of the old classes (test code
only): remove or give a real assertion to the placeholders `contextLoads`/`testContainer` in both
classes (T1), assert the XLSX test (T2), restore or delete commented-out assertions and the two
`@TODO`s (T8), fix the faulty participant id `039e8d60-…-0c31aa53a49` in `TEST_ALLOCATIONS` (T10).

## Tasks

- [ ] PDFBox `org.apache.pdfbox:pdfbox` 3.0.8 (test scope, exact version, ≥ 7 days old, Apache-2.0), `EXTERNAL_SOURCES.md` row
- [ ] Scenario base class (shared container, cleanup helper, fixed reference date)
- [ ] S1 – S3, S6, S10 (green) first, then S2, then the defect scenarios as disabled tests
- [ ] PDF text helper and XLSX reader helper
- [ ] Split by area: `BillingScenarioVatTests`, `BillingScenarioFeeTests`, `BillingScenarioErrorPathTests`, `BillingScenarioOutputTests` (each < 300 lines)
- [ ] Clean up T1, T2, T3, T4, T7, T8, T10 in the old classes
- [ ] Full suite once; raise thresholds; `AGENT_LOG.md`

## Acceptance criteria

- `grep -rhoE 'void s(0[1-9]|1[0-2])_' src/test | sort -u | wc -l` prints 12 (scenario methods named `sNN_…`), or 11 while S11 waits for B-13 (no empty placeholder test; the wait is listed in `AGENT_LOG.md`); each is green or `@Disabled("known-errors #NN")`. The F15 test (`f15_…`) exists.
- Every scenario that runs `doBilling` asserts the result text.
- PDF content (amount, VAT, number) and XLSX sums asserted for at least S1 and S2.
- JaCoCo CSV of the clean full run, **targets to be confirmed by measurement** (concept §8 figures are estimates): total lines ≥ 75 %, branches ≥ 70 %; `BillingService` branches ≥ 75 %. Report the measured values; a miss is explained, not hidden by exclusions.
- No `Thread.sleep`; suite time before/after recorded in `AGENT_LOG.md`.
- `git diff --stat src/main` is empty; only `pom.xml` gains PDFBox.

## Risks

- The data come from a table the fixture creates, not from the real backend view; scenarios prove billing logic, not the contract (M6).
- Jasper compiles the template on the first PDF run (slow); the shared context keeps it to once.
- Non-transactional scenarios leave data behind if cleanup fails; use a distinct tenant id per scenario.
- Disabled F1 scenarios are only meaningful if they would have failed: verify each once against the current code by enabling it locally (record the red result).
