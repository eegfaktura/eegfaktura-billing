# M2 — Web layer and tenant matrix

**Concept:** phase 2 · **Status:** open · **Production code:** none
**Depends on:** M0; B-14 decided: 403 stays. **B-15 is decided: `fix-tenant-claim` is NOT
merged first** — M2 is written against `master` as it is and does not assume the branch's code or its
three test classes. **Effort:** 3 – 4 days (unchanged: simpler tokens, but the #1/#19 rows are disabled tests to be run once against `master` and recorded).

## Base: `master` as it is (verified 2026-10-01 in `src/main/java/.../security`)

- `JwtTokenService` reads the claims `tenant[]`, `access_groups[]`, `preferred_username` and nothing else:
  **no `azp`, no `iss` check**; `AppProperties` has only `jwtPublicKeyFile` (no `jwtIssuer`, no allowed clients).
- `JwtRequestFilter` has no working tenant check: it compares `getAuthorities().contains(TenantContext.getCurrentTenant())`
  (a list of `Authority` against a `String`, never true), and the tenant is not yet set when it runs
  (`known-errors.md` #1). A foreign tenant therefore is **not** refused by the filter; only the per-record
  comparison in the controllers (`TenantContext.validateTenant`) holds.
- Consequence: `TestTokens` needs only the three claims. When the fix is merged later, `TestTokens` gains `iss`
  and `azp` in that change (the rows enabled by it need them).

## Goal

Every endpoint is tested for authentication, authorization and tenant isolation with the **real**
security configuration, and the error format is pinned. This closes the gap that hid
`known-errors.md` #1.

## Scope

**Endpoints (31 mapped handlers under `/api/**`, counted 2026-10-01; `GET /` of `HomeController` is
public and not part of the matrix):** `BillingRunResource` 8, `BillingConfigResource` 11,
`BillingDocumentResource` 3, `BillingDocumentFileResource` 3, `BillingDocumentItemResource` 2,
`BillingDocumentNumberResource` 2, `BillingResource` 1, `FileDataResource` 1. Commented-out
mappings do not count (F18, `known-errors.md` #29). 25 handlers look up a record by id, 4 take the
tenant in the path (`billingRuns/{tenantId}/…`, `billingDocuments/tenant/…`,
`billingConfigs/tenant/…`, `billingDocumentFiles/tenant/…`), 2 in the body (`POST /api/billing`,
`POST /api/billingConfigs`).

**Test set-up.** One `@WebMvcTest(controllers = …)` class per resource, named
`<Resource>WebTests` in `org.vfeeg.eegfaktura.billing.rest` (the unmerged branch `fix-tenant-claim` owns
`BillingConfigResourceTests`; the names avoid a clash later). Imported for real: `JwtSecurityConfig`
(profile `!dev`; never activate `dev`/`DevSecurityConfig`), `JwtTokenService`, `AppProperties`,
`InMemoryLockRepository` (the resources use the concrete class and `synchronized` on its lock, so a
mock returning `null` would fail); picked up by the slice: `JwtRequestFilter`, `TenantFilter`,
`RestExceptionHandler`. Mocked with `@MockitoBean`: all services. `DomainConfig` is not scanned by
a slice, so no JPA is needed.

**Tokens.** `JwtTokenService` verifies RS256 against an **X.509 certificate file**
(`app.jwtpublickeyfile`) and needs the claims `tenant[]`, `access_groups[]`, `preferred_username`
(nothing more on `master`). A test-only key pair and self-signed certificate (valid
~100 years, `openssl` command documented in `src/test/resources/jwt/README.md`, clearly named
`test-only`) live in `src/test/resources/jwt/`; a `TestTokens` helper signs tokens with java-jwt
(already a dependency). The property is set through `@TestPropertySource`.

**Matrix, only the cases that apply to an endpoint** (table-driven: one list of endpoints with
method, path, kind, sample body):

| Case | Applies to | Expectation |
|---|---|---|
| no token | all 31 | **403** (accepted contract, `open-points.md` B-14 decided 2026-10-01: no authentication entry point; a normal test, not a defect) |
| token without role `EEG_ADMIN` | all 31 | 403 |
| own tenant | all 31 | the status the code returns (200 / 204; `POST /api/billing` returns 200 although annotated 201) |
| foreign tenant (header, path, body or stored record) | all 31 | 403 — today 500 (F9). The part that today **passes through the filter unchecked** (token's `tenant` claim does not contain the header; #1) is a separate row set: disabled `known-errors #1` |
| missing `Tenant` header | all 31 | 403 — today 500 (F9) |
| unknown id | the 25 id endpoints | 404 with `ErrorResponse` |
| invalid body | `POST /api/billing`, `POST` and `PUT /api/billingConfigs` | 400 with `fieldErrors` |

**Guard test:** compares the endpoint table with the live mappings from `RequestMappingHandlerMapping`
(all handlers whose pattern starts with `/api/`) in both directions; a new endpoint without a row
fails it.

**Plus:** `RestExceptionHandler` format for 404, 400, `ResponseStatusException` and an unexpected
exception; F8 (`PUT` checks the stored record's tenant, not only the body's), F10
(`footerImage` GET demands a file; `logoImage` without an image), F16 (image replace, wrong type), F9.

## Defect tests

Same rule as M1: assert the correct behaviour; `@Disabled("known-errors #NN")` until fixed.
Expected disabled: **the tenant-isolation rows that fail on `master`** — header tenant not in the
token's `tenant` claim (`known-errors #1`, filter check never matches) and `PUT /api/billingConfigs/{id}`
with a foreign stored record (F8 = `known-errors #19`) — both are enabled in the change that merges
`fix-tenant-claim`; F9 = #20 (every foreign-tenant and missing-header row), F10 = #21, F16 = #27. Rows that
pass today (per-record comparison in the controllers) are not disabled. Each disabled test is run once
enabled locally against `master` and the red result recorded before it is committed.

## Tasks

- [ ] B-15 is decided (no merge first): tokens with the three claims only; rows for #1/#19 as disabled defect tests with the number in the reason (listed in `AGENT_LOG.md`, to be enabled by the merge of `fix-tenant-claim`)
- [ ] Test key pair, certificate, `TestTokens`, `@WebMvcTest` base class (annotations only, no logic)
- [ ] Endpoint table, guard test (live mappings vs. table)
- [ ] One test class per resource (split `BillingConfigResource` into CRUD and images); error-format tests
- [ ] Disabled defect tests with numbers; list them in `AGENT_LOG.md`
- [ ] Full suite once; raise thresholds; `AGENT_LOG.md`

## Acceptance criteria

- The guard test reports 31 of 31 `/api/**` handlers covered; deleting one table row makes it fail.
- JaCoCo CSV of the clean full run, **targets from concept §8 to be confirmed by measurement** (the concept's figures are estimates, not derived): `security` branches: the reachable ones of `JwtTokenService`, `JwtRequestFilter` (no/invalid/valid token), `TenantFilter`, `JwtSecurityConfig`; the target of 100 % is **not** reachable on `master` without the fix (the broken tenant branch is dead); report the measured value and the disabled rows instead, `rest` lines ≥ 85 %, total lines ≥ 65 %, branches ≥ 60 %. A branch that cannot be reached is listed in `AGENT_LOG.md` instead of being silently excluded; a target that proves unrealistic is lowered in the report with the measured value, not met by excluding code.
- Each new test class < 300 lines (`bash scripts/dev/loc-check.sh`); the M2 classes run without Docker (surefire report shows no Testcontainers start for them).
- Every `@Disabled` carries a `known-errors.md` number (grep check in the README); `git diff --stat src/main pom.xml` is empty.

## Risks

- `master` has no working filter tenant check (`known-errors.md` #1): the matrix rows rest on `TenantContext.validateTenant` in the controllers; the #1 rows stay disabled until the later merge. When it arrives, `TestTokens` (`iss`, `azp`) and the `AppProperties` binding in the slice's `@Import` change, and the branch's own tests may collide in name with M2's (use `<Resource>WebTests`).
- Test coverage of `security` stays low until the fix is merged; that is a consequence of B-15, not a gap in M2.
- Filter order: `TenantFilter` (`@Order(1)`) runs after the security chain, so the tenant is not yet set when `JwtRequestFilter` runs; the slice must keep both filters in their real order (do not use `addFilters = false`).
- If M4 4b later moves the lock into the service, the slices' `@Import` of `InMemoryLockRepository` changes; rerun M2 after 4b.
- The slice may need more imports than listed (e.g. `AppProperties` binding); keep them in the base class, not per test.
- A committed key pair triggers secret scanners (Snyk); name and document it as test-only.
- `GET …/billingDocuments/sendmail` has a side effect (sends mail) behind GET; mock the mail service, never call a real one.
