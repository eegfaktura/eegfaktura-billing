# Changelog

All notable changes to **eegfaktura-billing (Java invoicing/billing service)** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and
versioning follows the deployment release tags. Detailed diffs stay in the `git log`;
this changelog highlights the changes relevant for overview and operations.

## [Unreleased]

### Fixed
- **Test: a failed billing run leaves nothing behind, checked through the launcher** (eegfaktura-dev tracking billing 12).
  The code was fixed in 1.0.8 (the calculation rolls back, `BillingRunLauncher` records the run as FAILED); the scenario
  test `s04_threeVatRatesFailAndLeaveNothingBehind` was still disabled and called the old synchronous path. It now
  accepts, claims and launches the run like the start endpoint and is enabled. No change in behaviour.
- **Export, mail run and deletion of a billing run: one at a time per run, refused instead of waiting** (eegfaktura-dev
  tracking billing 14). The in-memory lock per community (`InMemoryLockRepository`) let a third request in next to a
  waiting second one, expired after 15 minutes and did not hold across replicas. Now a PostgreSQL advisory lock per
  billing run (like the final-run lock) guards `GET /api/billingRuns/{id}/billingDocuments/xlsx`, `…/archive`,
  `…/sendmail` and `DELETE /api/billingRuns/{id}`. **What callers see (eegfaktura-web):** a second of these requests
  for the same run while one is running gets **409** at once (before: it waited until the first had finished); the
  web shows its usual error message for the action. Requests for different runs of a community no longer wait for
  each other. Answers are otherwise unchanged (sendmail still answers 500 with the text for its own refusals, e.g.
  mails already sent). The mail run keeps committing its status steps one by one (the lock is held on a connection
  of its own).
- **Logo and footer images stored before 1.2.0 get the community as owner** (eegfaktura-dev tracking billing 32, data
  part). Migration `V1_20` sets `file_data.tenant_id` for images without one, from the billing configuration that
  references them as logo or footer image — only when the configurations of exactly one community reference the
  row and nothing else does (no document PDF, no custom template, no mail-text image). `GET /api/fileData/{id}` then
  returns these images to their community (before: refused); every other row stays unchanged.

### Security
- **CI: the release workflow pins its actions by commit SHA** (eegfaktura-dev tracking W-261028-mm6). `rolling-release.yml`
  builds and pushes the shipped image, but used floating tags (`@v4`, `@v3`, …); a moved tag ran in the job that holds
  the push credentials. Now every action is pinned to a commit, the same SHAs `pr-checks.yml` and `security-scan.yml` already
  use where the action is the same, otherwise the commit the floating tag pointed to on 2026-10-28.

## [1.7.0] – 2026-10-28

### Fixed
- **A billing run without amounts, or whose amounts match no member, no longer ends as completed** (platform#189). **Contract (callers
  eegfaktura-web, eegfaktura-v3 `BillingClient`):** `POST /api/billing` (preview and final) with an empty or missing
  `allocations` list is now refused with **400** and `abstractText` „Keine Energiemengen für die Abrechnung übergeben
  – bitte die Seite neu laden und die Energiedaten des Zeitraums laden.“; no billing run row is created (before: 202,
  the run ended DONE, or NEW for a preview, with no document). When `allocations` are sent but none matches a
  member/metering point of `base.billing_masterdata`, the run now ends **FAILED** with the `errorSummary`
  „Abrechnung fehlgeschlagen: Keine der übergebenen Energiemengen passt zu einem Mitglied/Zählpunkt der Stammdaten –
  es wurde kein Beleg erstellt.“ (nothing is written — documents of an earlier preview of the run stay, as with
  every FAILED run; the run can be restarted). Runs where at least one amount
  matches are unchanged. A period with no energy at all therefore gives no run, also when members are marked
  „Beitrittsgebühr nachverrechnen“: their fee stays requested and is billed with the first run with energy (decided
  28.10.2026; before, such a run wrote fee-only invoices).

### Security
- **Security: the Spring profile `dev` no longer opens the API.** `DevSecurityConfig` (profile `dev`) replaced the
  JWT security chain with one that only disabled CSRF, so every endpoint answered without a token whenever the
  profile was switched on. No deployment uses that profile (gitops, platform, docker-compose checked 28.10.2026); the
  class is removed and the JWT chain applies in every profile. Found while assessing the Snyk Code alert
  `java/DisablesCSRFProtection` (dismissed as false positive: bearer-only, stateless API).

## [1.6.0] – 2026-10-28

### Added
- **One-time joining fee („Beitrittsgebühr“)** (konzept-beitrittsgebuehr.md, platform#217). **Deploy order: the
  eegfaktura-backend migration `20261028130000_joining_fee` first** — billing reads three new columns of the view
  `base.billing_masterdata` (`tariff_joining_fee`, `tariff_joining_fee_use_vat`, `tariff_joining_fee_vat_in_percent`,
  from the member's EEG tariff); against the old view every run fails. Rules: the fee is due in the member's first
  final run with an energy item (consumption or feed-in) when the EEG tariff has a fee > 0 and the community has no
  final document (run DONE) with an energy item or a joining fee for the member yet — existing members count as
  paid. It is billed once as the item „Beitrittsgebühr“ (1 × amount €, no discount, own VAT setting, 0 % with
  „USt nein“) on the **invoice**, never on a credit note or INFO document; a pure producer gets an own invoice.
  Previews show it without consuming it; deleting a final run makes it due again unless the member has another
  final document with energy (then „Gebühr anfordern“ charges it in the next final run). **New item on printed invoices**
  (rendered like the membership fee, after the metering-point blocks); invoices of tariffs without a fee are
  unchanged.
- Flyway `V1_18`: column `billing_document_item.item_type` (`ENERGY`, `PARTICIPANT_FEE`, `METERING_POINT_FEE`,
  `JOINING_FEE`; every new item gets it, older items stay empty and their energy items are recognised by metering
  point and a text not starting with „Zählpunktgebühr“) and index `billing_document(tenant_id, participant_id)`.
  Rollback: older image, column and index may stay.
- **Contract (caller eegfaktura-web):** `GET /api/billingDocuments/tenant/{tenantId}/joiningFeeState` (role
  `EEG_ADMIN`, `Tenant` header, path tenant validated) →
  `[{"participantId", "energyBilled", "joiningFee": {"documentNumber", "documentDate"} | null}]` for every member
  with a final document of the community (others are absent).
- **Contract (callers eegfaktura-web, eegfaktura-v3 `BillingClient` — additive):** `ParticipantAmount.joiningFee`
  (gross, 0 when none) in `POST /api/billing` results and `GET /api/billingRuns/{id}/participantAmounts`; the joining
  fee no longer ends up in `participantFee`.
- **Joining fee per member: charge later, paid outside eegfaktura** (extension 27.10.2026 of the concept). Flyway
  `V1_19`: table `billing_joining_fee_record`, one row per (tenant, member) with `kind` `CHARGE_REQUESTED` or
  `PAID_EXTERNALLY`, `paid_on`, `note` (free text, at most 200 characters, never logged), `created_by` (user of the
  token), `created_at`. Due rule now: fee > 0, no final document with the fee, not paid externally, and either the
  first energy billing or a request. A requested member is charged in the next final run even when already billed
  with energy; without a metering point in the run it gets an own invoice with only the fee (a member in the run
  without energy gets the fee on its usual invoice). Previews do not consume a request; after deleting the run the
  fee is due again and the request stays. **Rollback note:** an older image ignores the table (requests are not
  charged, members paid externally get the automatic fee) — check rows `PAID_EXTERNALLY` without a fee document
  before rolling back.
- **Contract (caller eegfaktura-web):** `PUT /api/billingDocuments/tenant/{tenantId}/participants/{participantId}/joiningFee`
  with `{"kind": "CHARGE_REQUESTED"}` or `{"kind": "PAID_EXTERNALLY", "paidOn": "YYYY-MM-DD", "note": "…"}` → 204
  (creates or replaces the member's record, so the two kinds exclude each other); `DELETE` on the same path → 204
  (idempotent). Both answer 409 once a final document with the fee exists; PUT answers 400 for a missing/unknown
  kind, a missing, malformed or future `paidOn`, `paidOn`/`note` with `CHARGE_REQUESTED`, a note over 200 characters
  or a member id that is no UUID. `joiningFeeState` gains `requested` (bool, request not billed yet) and
  `paidExternally` (`{"paidOn", "note"}` or null) and also lists members that only have a record.
- **Final runs of one community run one at a time** (PostgreSQL transaction advisory lock per tenant, two-key so it
  does not meet other advisory locks, the first step of a final run; previews take no lock). A second final run of
  the community does not wait (decided 28.10.2026, so no billing thread waits and other communities are never held
  up): it ends FAILED at once with „Ein endgültiger Abrechnungslauf dieser Gemeinschaft läuft gerade – bitte nach
  dessen Ende erneut starten“. The joining fee records (`PUT`/`DELETE …/joiningFee`) answer 409 while a final run
  of the community runs, because the run reads them once. Records are stored with the tenant in upper case.

### Changed
- **Abort text for a third VAT rate** (callers eegfaktura-web and eegfaktura-v3 show the run's `errorSummary`; the
  prefix „Abrechnung fehlgeschlagen: “ is unchanged): instead of „More than 2 VAT rates not supported. Rate 1=…“ the
  run now reports „Mitglied <member number>: mehr als zwei verschiedene USt-Sätze über 0 % auf einem Beleg (20,00 %,
  10,00 %, 13,00 %). Bitte die USt-Sätze der Tarife prüfen.“ — the member number, never the name (also logged).
- Tests: `BillingScenarioFeeTests.s07` (participant fee with VAT on but no rate → billed with 0 %) runs again;
  known-errors #17 was fixed by upstream #51 (`b373fcc`), the test stayed disabled. No change to the service.

### Fixed
- **CI: the Snyk Code scan fails visibly when it cannot run.** An invalid `SNYK_TOKEN` ended the weekly and
  push scan green without any result (`continue-on-error`); now only „issues found“ keeps the run green (the
  issues go to Code Scanning), an authentication or tool error fails it. The Snyk CLI is pinned to 1.1308.1
  instead of the latest version.
- Document numbers of two final runs of one community started at the same time (known-errors #15): the second
  run is now refused at once while the first runs (see Added), so it never fails on the unique constraint
  (tenant, document number) with a duplicate number.


## [1.5.0] – 2026-10-27

### Fixed
- **Visible change on printed documents — VAT of items with „USt nein“** (261027-mm1, also known-errors #16):
  energy items, the membership fee and the consumer meter-point fee of a tariff with „USt nein“ kept the VAT rate
  stored in the tariff. The VAT column of these items now shows **0 %**; they no longer count as a VAT rate, so
  the document no longer shows a sum line „USt 20,00 %: 0,00 €“ for them; the Excel export of the run shows 0 in
  „Pos. UST %“ and no such rate in „UST Satz 1/2 (%)“ — for a document with only „USt nein“ items the columns „UST Satz 1/2 (%)“ and „UST Satz 1/2 Summe (Euro)“ are now empty (were: rate 20, sum 0). A rate left in a „USt nein“ tariff no longer causes the abort
  „More than 2 VAT rates not supported“ or a second VAT column. VAT rates are compared numerically: `20` and `20.0`
  are one rate (before: two sums or the abort). Net, VAT and gross amounts are unchanged; inform the
  communities (pilot group first) before the rollout.

## [1.4.0] – 2026-10-27

### Changed
- **Visible change — entry names in the document ZIP of a run** (`GET /api/billingRuns/{id}/billingDocuments/archive`,
  platform#221): the number in front of each file name (a hash of the internal file id) is gone. Invoices and credit
  notes keep their file name as stored (`Abr_YM-2025-6_Rechnung_RG202500304.pdf`, unique by the document number);
  preview and INFO documents get the member number before `.pdf`
  (`Abr_YM-2025-6_Rechnung_Vorschau_<member number>.pdf`; characters `/ \ : * ? " < > |` become `_`, no suffix
  without a member number); a name that still occurs twice gets ` (2)`, ` (3)` (unlike `_<member number>`, so a counter never looks like a member number) … in a stable order (by name, then file
  id). Communities that sort or match the unpacked files by the old names need to adapt. Unchanged: the response
  shape of the endpoint, the stored file name, the PDF itself and the name of the single-document download.

## [1.3.0] – 2026-10-26

### Added
- **Fixed footer in the `billing-document` mail**: every mail (send run, default and stored text, preview
  and test mail) ends with `versandt durch EEG Faktura, ein Service des VFEEG - https://vfeeg.org/`,
  appended by the server after the sanitized body (HTML and the derived plain-text part); it is not
  editable and not part of the stored text. The list and GET of `/api/mailtemplates` return it as the new
  field `fixedFooter` (additive contract change, same field as in eegfaktura-backend; the web shows it
  below the editor and nothing when the field is missing, so the deploy order is not critical — billing
  before or together with the web).
- **Font size in mail texts**: `mail-html-policy.json` version 2 (identical copy in eegfaktura-backend)
  allows `font-size` `13px` and `20px` in the `style` of `p`, `h1`–`h3` and `li`, also together with
  `text-align`; other sizes and units are dropped. New cases in the shared `mail-html-policy-cases.json`.
- **Link rules in the `billing-document` mail text** (Variante A, decided 2026-10-26, same rules as in
  eegfaktura-backend): no links to IP addresses (IPv4, IPv6, numeric hosts), none to URL shorteners (list
  `links.blockedHosts` in the shared `mail-html-policy.json`, also their subdomains), a link text that shows
  an address (`https://…`, `www.…`, a bare domain, an e-mail address) must show the link's own host/address,
  at most 3 links and links to at most 2 main domains per text (the last two labels, or three after a
  `secondLevelSuffixes` entry such as `co.at`/`gv.at`, so subdomains of one main domain count once; mailto links
  count as links, not as domains; the fixed footer is not counted). A link text that is a file name
  (`fileExtensions`, e.g. `anleitung.pdf`) is not an address. Save, preview and test mail answer 400 in the existing error format with a
  German message per broken rule. A stored text that breaks the rules is not changed; at send time it counts
  as not renderable → the default text is sent and the send protocol carries the existing fallback note.
  `limits` of the definition has the new fields `maxLinks` and `maxLinkDomains` (additive). Shared cases:
  `mail-link-cases.json` (identical in eegfaktura-backend).
  Link texts are read as the reader sees them, with the same algorithm as eegfaktura-backend: zero-width
  characters are removed, every Unicode space (also NBSP) separates words, and every word that is an address
  (`…://…`, `www.…`, an e-mail address, a bare domain; surrounding quotes and punctuation stripped) must match
  the target — „Mehr unter https://eeg.at“ is fine, `a@b`, `office@eeg` and `z.B.` are no address, a `mailto:`
  with `%40` matches its text. The host dots `。` `．` `｡` count as `.` (`bit。ly` is a shortener); a single hex
  word like `cafe` is a host name, not an IP. **No placeholder inside a link** (new key `placeholder-in-link`,
  „Platzhalter dürfen nicht in einem Link stehen: Name der EEG“): at send time `eeg.email` would show another
  address than the target. The sanitizer now drops the links the backend drops, so both give the same verdict:
  an https link whose host part has a backslash, `%`, `^`, `{`, `|`, `}` or a backtick, an unclosed IPv6
  bracket or a port that is no number, and any URL with a space (e.g. `mailto: office@…`, before kept as `%20`);
  the link text stays. Also dropped as in the backend: an https link with more than one port (`bit.ly:80:90`),
  with user info outside Go's characters (e.g. `ü@`), with a bracket that holds no IPv6 address (`[abc]`,
  `[1.2.3.4]`) or with a control character; Unicode spaces (also NBSP) around a URL are trimmed.
  **Host characters:** a link whose host has other characters than ASCII letters, digits, `-` and `.` (fullwidth
  letters or digits, soft hyphen, zero-width characters, umlauts) is refused (new key `bad-host`, „Die
  Linkadresse enthält ungültige Zeichen: münchen.at“); an umlaut domain has to be written in its `xn--` form
  (`xn--mnchen-3ya.at`). Link texts: invisible format characters (Unicode `Cf`, e.g. soft hyphen) are removed,
  fullwidth ASCII counts as ASCII, a word also ends at a `:` not followed by `//` (`Website:bank.at`),
  punctuation and symbols at both ends of a word are stripped (`»bank.at«`, `bank.at…`, `→bank.at`; a final `/`
  stays), the alt text of an image inside a link counts as link text, and `www.eeg.at:443` shows the host
  `eeg.at`. Hosts and `mailto` addresses are compared with ASCII-only lower-casing (`İnfo@` is not `info@`); a
  `mailto` address is trimmed of Unicode spaces (also NBSP) after percent-decoding.

### Security
- **Uploaded mail images are re-encoded** (`/api/mailtemplates/{key}/files`, kind `image`): after the content
  type check the dimensions are read without decoding (more than 1200 px wide, 4000 px high or 4.8 million
  pixels → 400 „Das Bild ist zu groß …“), then the image is decoded and written again with JDK ImageIO in the
  same format (PNG lossless, JPEG quality 0.8, RGB); only these bytes are stored — EXIF/GPS, comments and data
  appended to the file are dropped. A file that does not decode → 400 „Das Bild kann nicht gelesen werden.“
  (also e.g. CMYK JPEGs). The byte limits apply to the re-encoded image. Already stored images are unchanged.

### Changed
- Default text of `billing-document` without the eegfaktura logo on top (the fixed footer names the
  sender); `defaultVersion` 1 → 2, so EEGs with an own text see the "default changed" notice. Stored texts
  are not migrated; one that still contains `mailfile:eegfaktura-logo` keeps showing the built-in logo.

## [1.2.0] – 2026-10-26

### Added
- **Editable member mail text (`billing-document`)** (konzept-mail-texte-editierbar.md): an EEG admin edits
  subject and text of the invoice/credit note mail under `/api/mailtemplates` (same schema as the backend:
  list, get, PUT with version stamp → 409 on a stale version, DELETE = back to the default, image upload,
  preview, test mail). Text is sanitized server-side with OWASP java-html-sanitizer driven by the shared
  `mail-html-policy.json`; placeholders (`{key}` in the subject, `span/div[data-ph]` in the text) are replaced
  as escaped values only. Images: PNG/JPEG checked by content, at most 3, together ≤ 500 KB; no extra PDF
  (the document PDF is always attached). Test mail goes only to the login's e-mail claim or the EEG address
  from `base.billing_masterdata`, without cc, subject prefixed `[Test] ` (send failure: 502). The
  eegfaktura logo is the built-in image `mailfile:eegfaktura-logo` (as in the backend; served under
  `/files/eegfaktura-logo`, not counted against the limits, not deletable).
  Uploads are drafts (`committed=false`, never sent) until a saved version uses them; PUT removes files the
  version no longer uses and drafts older than 24 h (a referenced older draft is committed; an image in the
  text whose file no longer exists is a 400), DELETE of a file the saved version uses answers 409.
  Uploads, saves and deletes of one mail are serialized (advisory lock), so the limits hold. A block
  placeholder is allowed only as a top-level `div`; in the subject only `{key}` with the catalog syntax is a
  placeholder (`{EEG}` stays text). `billing.footerText` is HTML (as in the PDF) and goes through the same
  sanitizer instead of being escaped. The test mail to the EEG goes to every valid address of a `;` list;
  an EEG without participants has no address in `base.billing_masterdata` (400).
- Flyway `V1_16`: tables `billing_mail_template` and `billing_mail_template_file` (additive; files in
  `file_data`). `V1_17`: column `billing_mail_template_file.committed` (additive).
- Coverage gate: JaCoCo reports coverage in `test.yml` (artifact `jacoco-report`) and `verify` fails if a
  package falls below its floor; as `test.yml` gates the image (1.1.0), **coverage below a floor now blocks
  the image and every deploy job** too. `./mvnw test` stays without the gate.
- `lombok.config` (`lombok.addLombokGeneratedAnnotation = true`) so coverage ignores generated code.
- Test builders under `src/test/.../support` (`PostgresContainerHolder`, `BillingMasterdataBuilder`,
  `AllocationBuilder`, `BillingRunFixture`); no production code changed.
- Billing scenario tests S1 – S12 (`src/test/.../scenario`, S11 waits for the rounding decision B-13): amounts,
  VAT sums, document types and numbers, run status, result text, PDF text and XLSX sums; new test-only
  dependency PDFBox 3.0.8. Five scenarios that expose known defects (#12, #13, #16, #17, #22) are
  committed disabled. No production code changed.
- `EXTERNAL_SOURCES.md` (every source of build, tests and service) and `scripts/dev/loc-check.sh`
  (lines per file). The working agreement, the tracking files and the migration-concept template are
  kept outside the repository.

### Changed
- **Billing run mail** uses the EEG's stored text, else the default. The default reproduces the former mail
  (logo, greeting, text, signature, footer) in the same frame (grey background, white 595 px box);
  layout differences inside the box: one column with even padding instead of the former table columns
  (greeting and text were indented differently), the logo is a centered paragraph instead of a 205 px
  header row, and the footer text is a normal paragraph instead of extra-small text at the bottom. **Subject:** same pattern
  `<Belegart> <Zeitraum> - <EEG>`, but the period is now the readable date range (e.g.
  `01.07.2026 - 30.09.2026` instead of `Abr_YQ-2026-3`), and with an empty EEG name the subject ends in ` -`. If a stored text cannot be rendered, the
  default is sent and the run's send protocol says so.
- `spring.servlet.multipart.max-file-size` 256KB → 512KB (image limit of the mail text; also applies to
  logo/footer uploads).
- Tests follow release 1.0.8 (asynchronous billing run, ZVT, mail): the concurrency tests exercise the claim
  (`secondStartOfARunningPeriodIsRefused`, `simultaneousStartsOfOnePeriodAcceptExactlyOne`) instead of the removed
  per-tenant lock, the two-periods case is disabled as the reproduction of the number race; the scenario tests expect
  the exceptions the service throws now (the resource answers 400/409); the mail tests expect the issuer in the subject
  and the plain-text alternative; the M3 masterdata table and the M6 legacy view (`legacy-base/06_…zvt_time_tariff`,
  copied from eegfaktura-backend) carry the ZVT columns; `responses/BillingRunDTO.json` records `errorSummary`.
  New: scenario S13 (`BillingScenarioZvtTests`, one position per window share and the ten contract refusals),
  `BillingRunLauncherTests` (FAILED summaries), the web slice covers 202/409/503 of `POST /api/billing`.
  `LegacyMasterdataViewContractTests.v3ViewHasEveryLegacyAndEntityColumnByName` is disabled: v3's cutover view
  lacks the 11 ZVT columns (known error). No production code changed.
- Mail text tests: without a Tenant header every handler of `/api/mailtemplates` is checked to answer 403,
  the writing ones (PUT, DELETE, file upload and delete, test mail) included, with nothing stored and no mail
  sent. No production code changed.

### Fixed
- Tests: the `rest` coverage gate (100 % lines) failed now and then, because the 409 for two simultaneous first
  starts of a period was only reached when the race happened in the concurrency tests; a web test covers it
  deterministically now (pre-existing, found on this PR's CI).
- The plain-text part of the billing mail decodes all HTML entities (numeric ones such as `&#64;` showed).
- Recipient and EEG name were inserted into the billing mail without HTML escaping (FreeMarker without
  output format); the FreeMarker template and dependency are removed.
- Logo/footer upload: `file_data.tenant_id` is set, and the type is checked from the content (PNG/JPEG/GIF);
  an upload without Content-Type or with other content is rejected.
- The integration tests run on Docker 29: `src/test/resources/docker-java.properties` sets the
  Docker API version Testcontainers asks for (it asked for 1.32, Docker 29 refuses below 1.40).
- `TEST_STORE_DOCUMENTS_PATH` also sets the directory for the generated PDFs and XLSX of the
  integration tests (empty by default, as in 1.1.0).
- The web-layer tests generate their JWT key pair and self-signed certificate at runtime (`TestTokens`,
  JDK `keytool`, certificate in a temp file, handed to `WebSliceTest` via `@DynamicPropertySource`); no
  private key is committed, so the secret scan stays green (known error 261009-ca1). No new dependency.
- `.gitattributes` keeps `src/test/resources/legacy-base/**` and `src/test/resources/contracts/v3view/**`
  on LF, so `LegacyMasterdataViewContractTests.copiedFilesAreUnchanged` also passes on Windows checkouts
  with `core.autocrlf=true`.
- `EXTERNAL_SOURCES.md` lists the OWASP java-html-sanitizer (20261011.1, Apache-2.0 and BSD-2-Clause) of the mail texts
  instead of FreeMarker, whose dependency this release removes.
- Mail texts: saving checks the image limits again (at most 3 images, together ≤ 500 KB) over every image the
  saved version uses. The upload counts the saved files plus drafts of the last 24 h, so with older drafts a text
  could commit more images than allowed; such a save is now a 400 and stores nothing.

## [1.1.0] – 2026-10-21

### Added
- **Time-of-use tariffs (ZVT)**: a time-based tariff prices up to two named
  daytime windows (T1/T2) plus a base price. The caller (web) sends per
  metering point the window sums from energystore (`allocations[].buckets:
  [{key: BASE|T1|T2, kWh}]`) plus the used window definitions
  (`allocations[].timeWindows`) — billing prices each bucket separately
  (1–3 positions per metering point, labels `Tarif: Basis` /
  `Tarif: <Name> (HH:MM - HH:MM)`), discount/VAT/rounding per position as
  before. Fail-loud contract guards: a time-based tariff without buckets, a
  mismatch between the sent windows and the current masterdata windows (the
  view is live, no tariff snapshot) or buckets on a simple tariff abort the
  run with a clear error (`FAILED` + errorSummary in the async path). New
  masterdata fields come from the extended backend view
  `base.billing_masterdata` (no billing-side Flyway). Free kWh remain
  simple-mode-only.

### Changed
- **ZVT: tariff options without energy are left out** (pilot feedback 20.10.2026, RC103172). A time-based tariff
  printed every option even at 0 kWh (decision of 12.07.), e.g. a night window of a PV producer as a 0 kWh line on
  every credit note and in the billing Excel. Options with 0 kWh are no longer created, the base line included;
  options with energy stay, also at a price of 0.
- **PDF: no „Zwischensumme Zählpunkt“ for a single position** (pilot feedback 20.10.2026). A metering point block
  with one position (simple tariff without metering point fee) repeated it as subtotal. The subtotal now appears
  only when the block has two or more positions (time-based tariff, metering point fee).
- **PDF layout: items are now grouped per metering point on ALL documents**
  (invoice, credit note, RC credit note, info): a bold block header
  `Zählpunkt <nr> - <name> (Rabatt xx %)`, the item rows without the
  repeated metering point id, the metering point fee inside its block, and
  a bold `Zwischensumme Zählpunkt <nr>` (net/VAT/gross) per block.
  Participant-level positions (membership fee) follow after the blocks.
  Metering point fee items now carry the `metering_point_id` (grouping
  key); per-metering-point amounts in the API aggregate multiple positions
  per metering point into one entry. The `Zwischensumme Zählpunkt` row is
  framed by a faint separator line above and below (no line between the
  individual tariff-variant rows, no grey background); the subtotal label
  reads `Zwischensumme Zählpunkt` without the metering point id and also
  shows the summed energy amount (kWh) of the block's energy positions.
- **Billing run is now asynchronous** (`POST /api/billing` → `202 Accepted` + `billingRunId`,
  progress via polling `GET /api/billingRuns/{id}`): the synchronous call used to run for
  minutes on large communities, hit proxy timeouts (504 while the run kept going server-side)
  and an impatient second click queued a full second run behind the per-tenant in-memory lock.
  Duplicate starts now get `409 Conflict` with the id of the already-running run; a full
  executor answers `503` and rolls the status claim back. The in-memory lock is replaced by an
  atomic DB status claim (`RUNNING` only from `NEW`/`FAILED`) — safe across replicas.
- `BillingRunStatus` gains `RUNNING` and `FAILED` (appended — ordinal mapping!); a failed run
  persists a short `errorSummary` on the billing run (previously the error only lived in the
  ephemeral HTTP response) and can be restarted (cleanup of partial documents included).
  Preview runs end back at `NEW` (unchanged semantics), final runs at `DONE`;
  `DONE`/`CANCELLED` still reject new starts.
- Flyway `V1_15`: `billing_run.error_summary` column + unique index on
  `(tenant_id, clearing_period_type, clearing_period_identifier)` (closes the first-creation
  race of two parallel starts; fails visibly if pre-existing duplicate rows need manual cleanup).
- Mail deliverability: document mails now go out as **multipart/alternative** (a plain-text part
  derived from the rendered HTML — a lone `text/html` body is a spam signal), carry an
  `Auto-Submitted: auto-generated` header, and use a **Message-ID with the `eegfaktura.at` domain**
  instead of the pod hostname (`spring.mail.properties.mail.from`). The document mail subject now
  includes the EEG name; the default mail template declares `lang="de"`.
- Build: the code is compiled for Java 21 (`release` follows `java.version`; was 17), the version
  the image is built and runs with (Temurin 21) and CI tests with. Qodana's `projectJDK` is 21 as
  well. A local build now needs JDK 21.
- CI: `rolling-release.yml` runs the full test suite (`test.yml`) before it builds and pushes the
  image; a push to `master`, `preview/**`, `env/**` or a `v*` tag whose tests fail no longer
  produces an image or a deploy. Build and deploy take a few minutes longer. If a test fails only by chance
  (e.g. a Testcontainers start), „Re-run failed jobs“ on the Docker Image CI run builds and deploys after all.
- Tests: `android-json` (pulled in by `spring-boot-starter-test` via jsonassert) is excluded; it
  repackaged `org.json`, which is already a direct dependency, so the same classes were on the
  test classpath twice. Test-only, the image is unchanged.

### Fixed
- **PDF: blank line below the metering point header** (pilot feedback 20.10.2026). Jasper measured text with DejaVu
  Sans, the only font in the image, but the PDF is written in Helvetica, which is narrower: „Zählpunkt AT…“ (233 pt in
  DejaVu Sans Bold, cell 194 pt) got two lines reserved and printed in one. The image now installs
  `fonts-liberation` and fontconfig prefers Liberation Sans (Helvetica's glyph widths, 189 pt) for sans-serif, the
  font Jasper measures with. The PDF fonts stay Helvetica/Helvetica-Bold.
- Tests: `BillingIntegrationTests` read `billing_master_data.sql` with the platform charset, so
  umlauts in the test data broke six tests on JDK 17 / Windows (Cp1252). The SQL scripts are now
  read as UTF-8 (`@SqlConfig(encoding = "UTF-8")`).
- Tests: the test configuration no longer points `app.test.store-documents-path` at a developer's
  local directory (`/home/hla/temp`); it is empty by default (no documents written), and
  `APP_TEST_STOREDOCUMENTSPATH` still sets it (as in CI).

### Security
- Jackson raised to 2.21.7 (from 2.21.4, managed by Spring Boot 3.5.16): fixes CVE-2026-89425,
  CVE-2026-89407 (jackson-core), CVE-2026-91777, CVE-2026-91776, CVE-2026-68497 and several
  moderate ones (jackson-databind).
- PostgreSQL JDBC driver raised to 42.7.14 (from 42.7.11): fixes CVE-2026-54291 (silent
  channel-binding downgrade).

## [1.0.7] – 2026-10-18

### Added
- CI: PR checks and security scan. Every pull request runs unit tests and the full suite with
  Testcontainers (`pr-checks.yml`); `security-scan.yml` looks for leaked secrets in new commits,
  vulnerable dependencies and Dockerfile/Kubernetes misconfigurations. The dependency and
  misconfiguration scans only report for now and do not block; a leaked secret fails the run. Image
  build and deploy (`rolling-release.yml`) are unchanged.
- CI: every run keeps its scan and test results as artifacts for 30 days (`scan-*`,
  `test-results-<job>`, `known-errors-<job>`) for a CI report collector; the secret scan keeps
  outcomes and counts only.

### Removed
- Unused WebFlux starter (`spring-boot-starter-webflux`). With it netty and reactor-netty leave the
  image; known CVEs in these libraries no longer apply. The one leftover netty import
  (`StringUtil.isNullOrEmpty` in `BigDecimalTools`) is replaced by a plain null/empty check.

## [1.0.6] – 2026-10-17

### Security
- Embedded Tomcat raised to 10.1.60 (from 10.1.55, managed by Spring Boot 3.5.16) to fix known
  vulnerabilities in the web server that serves every request (CVE-2026-68525 incorrect
  authorization, fixed in 10.1.58; CVE-2026-68569 authentication bypass). Affects
  `tomcat-embed-core`, `-el` and `-websocket`.

## [1.0.5] – 2026-10-05

### Fixed
- **Super-admins could no longer load another community's billing settings.** Since the tenant
  check in `JwtRequestFilter` took effect (1.0.4), a token with the realm role `superuser` was
  refused for every community not in its own `tenant` claim — unlike backend and energystore,
  which let that role through. The filter now does the same; the `EEG_ADMIN` role and the
  per-record tenant checks still apply. A token without a `tenant` or `access_groups` claim
  is no longer rejected as a whole; those lists are simply empty.

## [1.0.4] – 2026-10-04

### Security
- **A billing configuration could be taken over by another community.**
  `PUT /api/billingConfigs/{id}` only checked the tenant in the request body, never the tenant
  of the stored record. An EEG admin who knew the id of another community's configuration could
  send it with their own tenant and so move it over; the other community then billed silently
  with the default texts and document-number settings. The stored record's tenant is now checked
  first, and an update never changes the tenant.
- **Any stored file was readable through one's own logo.** The file ids for logo, footer image
  and custom template were taken from the request body unchecked, and `GET …/logoImage` returns
  the referenced file without a tenant check. Setting the id of a known file — an invoice PDF of
  another community, for instance — as one's own logo made it downloadable. These ids are now set
  only by the upload and delete endpoints, never from the body.
  Both reported by a project member in a review of the billing code.
- Spring Boot 3.5.3 → 3.5.16, which brings patched Tomcat and Spring Security releases, and
  FreeMarker 2.3.34 → 2.3.35 (template path traversal, the only critical alert on this repo).
- The database login no longer falls back to `postgres`/`postgres` when
  `JDBC_DATABASE_USERNAME` or `JDBC_DATABASE_PASSWORD` is missing; the start fails instead. All
  deployments set both.

- **The tenant check never ran — in any direction.** `JwtRequestFilter` was meant to reject a
  request whose tenant is not in the caller's token. Three separate defects made it inert:
  the condition was inverted (`contains` instead of `!contains`); `Authority` overrode no
  `equals`/`hashCode`, so `contains` compared object identity and never matched anything; and
  the tenant was read from `TenantContext`, which a servlet filter at `@Order(1)` fills — while
  the Spring Security chain runs at order −100, i.e. before it. Thirty days of production logs
  contain the corresponding message exactly zero times.
  The filter now reads the `Tenant` header itself and checks it against the token's tenant
  list, which removes the ordering dependency entirely. `Authority` compares its tenant
  case-insensitively, as everywhere else in the suite.
  What remained in effect until now was `TenantContext.validateTenant()`, and that compares the
  tenant from the path against the one from the header — both supplied by the caller. It is
  only a boundary in combination with the filter check. A request without a tenant header no
  longer raises a `NullPointerException` (and with it a 500) but is rejected.

- The runtime image now starts from `eclipse-temurin:21-jre-jammy` instead of the full JDK
  image, and runs as a non-root user (UID/GID 1001). The builder stage still carries the JDK
  for `mvn package`, so the build is unchanged — the shipped image simply no longer contains a
  compiler toolchain it never uses. The UID is numeric because kubelet can only verify
  `runAsNonRoot` against a numeric `USER` directive; a named user yields
  `CreateContainerConfigError`. Prerequisite for the Pod-Security-Admission `restricted`
  profile. (#34)

### Fixed
- **A document date of "today" was rejected as post-dated during the night.** The container runs
  in UTC, so between midnight and 01:00 (02:00 in summer) Vienna time `LocalDate.now()` was still
  the previous day. The calendar day is now taken in `Europe/Vienna` — also for the default
  document date, which at the turn of the year decided the year and with it the number sequence.
- A participant fee or metering-point fee without a VAT rate stored the raw `null` and made the
  whole run fail with a `NullPointerException` in the VAT totals; it now counts as 0 %.
- `GET /api/billingConfigs/{id}/footerImage` required a multipart file parameter and could not be
  called as a plain GET; both image downloads now answer 404 instead of 500 when no image is set.

### Added
- OCI image labels (title, description, vendor, licenses) for registry and SBOM consumers. (#34)
- CI builds `env/**` branches and deploys the resulting image into the matching feature
  environment (ADR-0008): a push to `env/billing` pins `eegfaktura-billing` in namespace
  `env-billing` to that branch's `sha-…` image. Until now only `master`/`main`, tags and
  `preview/**` produced an image at all, so a feature branch had to be built and rolled out
  by hand — that is how the ZVT end-to-end test ran in July. The environment itself is still
  provisioned manually; this only deploys into an existing one.

## [1.0.3] – 2026-09-07

### Docs
- Add `docs/konzept-zeitvariable-tarife.md` — the ZVT (zeitvariabler Tarif) feature concept
  + tech design + acceptance criteria. billing is the lead component for this cross-component
  feature (backend/energystore/web); the concept lives here as the canonical spec.

## [1.0.2] – 2026-07-05

### Fixed
- Mail send no longer hands raw recipient strings to the mail parser ("Illegal address",
  observed in prod for addresses with leading/trailing whitespace): to/cc are normalized per
  `;`-separated part (outer whitespace incl. NBSP stripped) and validated against the shared
  suite-wide address rule before the MimeMessage is built; the normalized values are what
  actually gets sent. Invalid parts are reported instead of failing the whole message —
  no valid recipient at all remains an error.

### Changed
- Billing run send protocol: a failed send now names the member (participant number + name)
  next to the address — a blank/garbage address used to produce an unattributable
  "  FEHLER" entry. Rejected address parts on an otherwise delivered mail (e.g. an invalid
  issuer cc) are reported as a warning on the OK line, NOT as FEHLER — flagging a delivered
  invoice as failed would invite manual re-sends and duplicate invoices.
- CI: Preview-Deployments (ADR-0007) — Push auf `preview/**` baut+deployt on-demand in die Dev-Zone (sha-pinned, kein `:latest`), Auto-Reset bei Branch-Delete.


## [1.0.1] – 2026-06-30

### Changed
- Bump `jasperreports` 7.0.2 -> 7.0.4. (#24)
- CI: Snyk Code (SAST) workflow + SARIF upload to code scanning. (#25, #26)

## [1.0.0] – 2026-06-28

Part of the unified source-build cutover of the eegfaktura suite.

### Changed
- CI: push to the registry's development tier (ADR-0005). (#20)
- Multi-stage Docker build with a version glob in the runtime stage. (#18)
- Added README with service overview and tech stack. (#21)

## Earlier releases

Before the 1.0.0 cutover, billing was versioned independently (`v0.1.9`–`v0.1.23`,
2024-04 to 2025-06). Full list: [GitHub Releases](https://github.com/eegfaktura/eegfaktura-billing/releases).
Selected:

- **v0.1.23** (2025-06-28): JRXML template shows issuer/bank creditorId; among others
  creditorId, SEPA direct debit, tariff ID/version in invoices/XLSX export.
- **v0.1.22** (2025-04-16): additional mail headers (Reply-To, Return-Path).
- **v0.1.21** (2025-04-14) … **v0.1.9** (2024-04-04): see releases.
