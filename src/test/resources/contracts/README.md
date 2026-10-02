# `contracts/` — what billing's neighbours send and read (M6)

Fixtures of the callers of billing's REST API and the copy of v3's billing view, for the tests in
`src/test/java/org/vfeeg/eegfaktura/billing/contract/`. Copied by hand from the sibling
repositories; the build never reads a sibling checkout. Every JSON file names its source
repository, file (with lines), commit and date in `source`.

| Path | What | Test |
|---|---|---|
| `web/endpoints.json`, `v3/endpoints.json` | every billing request of the caller: method, a concrete path, source line; `body` = the body fixture, `multipart` = the part name | `CallerEndpointContractTests` (each call routes to an endpoint of the M2 table, which the guard ties to the live mappings) |
| `web/*.json`, `v3/*.json` (others) | request bodies exactly as the caller builds them, with its headers | `CallerRequestContractTests` (real web layer with `JacksonConfig`; the DTO gets the values) |
| `responses/<Dto>.json` | `fields`: every JSON field billing writes; `readBy`: what each caller reads | `CallerResponseContractTests` (exact `fields`, `readBy` ⊆ `fields`) |
| `legacy-masterdata-rows.sql` | billing's own rows in the legacy base tables | `LegacyMasterdataEntityReadTests` |
| `v3view/V190__billing_masterdata_v3.sql` | v3's view for cutover communities, byte-identical | `LegacyMasterdataViewContractTests.v3ViewHasEveryLegacyAndEntityColumnByName` (names only) |

## Sources at the time of copying (2026-10-02)

- **eegfaktura-web**: commit `c37a0b7f9197dd31a16b3ab5294d2d13ff551883` (2026-09-27, branch
  `add-XClient-Header`; `master` `189b5b3` has the same billing calls without the `X-Client` header).
  Calls in `src/service/eeg.service.ts`, request types in `src/models/meteringpoint.model.ts` and
  `src/models/eeg.model.ts`, the run request in `ParticipantPane.component.tsx` (`onDoBilling`).
  The web sends the header `tenant` (lower case) and `X-Client`.
- **eegfaktura-v3**: commit `0b785d2aaefafb4d34a91a7111fa249119b47af9` (2026-10-01, branch
  `bugfixes-poc`). Calls in `backend/.../integration/billing/BillingClient.kt`, DTOs in
  `LegacyBillingDtos.kt`, payloads built in `billing/run/BillingRunService.kt` and
  `billing/config/BillingConfigService.kt`. `V190__billing_masterdata_v3.sql` from
  `backend/src/main/resources/db/migration/` (last changed in `12514bc`), sha256
  `cb9434d332850c2eae8e2e704bc74aa53bbf6f248c4e48bdeb228094301e3dc9`, AGPL-3.0.

## Decisions the tests pin (as the code decides today)

- **Unknown JSON fields are tolerated**: `JacksonConfig` disables `FAIL_ON_UNKNOWN_PROPERTIES`.
  Consequence: `isPreview` instead of `preview` is ignored and the run is **final** (v3 documents
  this trap in `LegacyDoBillingParams`).
- Explicit `null`s (v3 sends every key) stay `null` in the DTO.
- `POST /api/billingConfigs` answers 201 with the new id as a JSON string.
- Times are ISO strings (`2024-06-28T10:15:30`), the credit-note flag is `createCreditNotesForAllProducers`.
- Not pinned here: the failure texts of `POST /api/billing` that both callers parse
  (`Abrechnung fehlgeschlagen…`, `bereits abgeschlossen`, `Ungültiges Belegdatum`) — M3's
  `BillingScenarioErrorPathTests` asserts them; v3 also maps a 500 with `exception`
  `AccessDeniedException` to "not found" (`known-errors.md` #20).

## Refresh when a neighbour changes

1. Find the billing calls again: in eegfaktura-web `grep -n BILLING_API_SERVER src/service/*.ts`;
   in eegfaktura-v3 `BillingClient.kt` (every `uri(...)`) and the constructors of `LegacyDoBillingParams`
   and `LegacyBillingConfig`.
2. Update `endpoints.json` (one entry per call), the body fixtures (as the caller serialises them:
   key names, nulls, number formats) and the `readBy` lists in `responses/`, each with the new
   commit, date and source lines.
3. Run `-Dtest='Caller*ContractTests'`. A failing route or field is a contract change: billing keeps
   its contract or the change is agreed with the caller (AGENTS.md section 7, `CHANGELOG.md`).
4. A billing-side change of a response field fails `CallerResponseContractTests` until the snapshot's
   `fields` is updated on purpose; tell both callers.
5. v3's view: copy `V190__…` (or its successor) unchanged, update the sha256 in
   `LegacyMasterdataViewContractTests.COPIED` and this README.

Future work (not done): a CI job that diffs these copies against the neighbours.
