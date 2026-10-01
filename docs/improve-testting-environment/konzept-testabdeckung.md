# Konzept: Testabdeckung der Abrechnung verbessern

**Stand:** 01.10.2026 · **Branch:** `improve-testing-environment` · **Art:** Konzept, keine Code-Änderung

Dieses Dokument beschreibt, wie die Testabdeckung von eegfaktura-billing systematisch erhöht wird.
Es ändert **keinen Code**. Fehler, die bei der Analyse gefunden wurden, sind in Abschnitt 4
festgehalten und **nicht behoben**; sie stehen zusätzlich in `known-errors.md` (#12 – #29).

## 1. Ziel

1. Jede fachlich riskante Logik – Beträge, Umsatzsteuer, Rundung, Belegnummern, Status eines
   Abrechnungslaufs – ist durch Tests abgesichert, die bei einer Änderung sofort rot werden.
2. Die Trennung der Gemeinschaften (Tenant) ist für **jeden** Endpunkt getestet.
3. Tests laufen bei jeder Änderung in der CI; die Abdeckung wird gemessen und darf nicht sinken.
4. Bekannte Fehler werden zuerst durch einen Test sichtbar gemacht, dann in einer eigenen Änderung
   behoben (AGENTS.md §10.1: kein Test, der falsches Verhalten festschreibt).

## 2. Ausgangslage (gemessen am 01.10.2026)

### 2.1 Tests

| Testklasse | Art | Tests | Was sie prüft |
|---|---|---:|---|
| `BillingIntegrationTests` | `@SpringBootTest` + Testcontainers PostgreSQL, SQL-Fixture | 9 | Vorschau- und Endabrechnung eines Laufs mit genauen Beträgen (5 Belege), Belegdatum, Reverse-Charge-Gutschriften, Konfiguration speichern/lesen, Stammdaten der Fixture; XLSX ohne Prüfung |
| `DocumentNumbersTests` | `@SpringBootTest` + Testcontainers | 7 | Format und Folge der Belegnummern (Beginn 0/1, Überlauf der Stellen, zehn in Folge, Jahreswechsel) |
| `ClearingPeriodIdentifierToolTests` | Unit | 5 | Texte der Abrechnungszeiträume (Jahr, Halbjahr, Quartal, Monat, Schaltjahre) |
| `EmailAddressUtilTest` | Unit | 2 | Normalisieren und Prüfen von Mailadressen |
| **Summe** | | **23** | |

Alle 23 Tests sind grün (JDK 21, Docker 29, nach den Reparaturen der Testumgebung in `ec80530`).

### 2.2 Abdeckung

Gemessen mit JaCoCo 0.8.13 über die Kommandozeile, **ohne** Änderung der `pom.xml`
(Abschnitt 8.1):

| Paket | Zeilen | Zeilen % | Zweige % |
|---|---:|---:|---:|
| `service` | 842 / 1344 | 62,6 % | 54,2 % |
| `util` | 69 / 78 | 88,5 % | 84,9 % |
| `domain` | 16 / 18 | 88,9 % | 75,0 % |
| `model` | 16 / 26 | 61,5 % | – |
| `repos` | 19 / 47 | 40,4 % | 50,0 % |
| `config` | 9 / 42 | 21,4 % | – |
| `security` | 20 / 101 | 19,8 % | **0,0 %** |
| `rest` | 33 / 184 | **17,9 %** | – |
| **Gesamt** | **1026 / 1845** | **55,6 %** | **56,1 %** (192 / 342) |

Die Zahl täuscht über die Verteilung: Die Rechenlogik ist über die Integrationstests gut erreicht,
alles um sie herum kaum.

| Klasse | Zeilen % | Bemerkung |
|---|---:|---|
| `BillingDocumentXlsxService` | 99,0 % | durchlaufen, aber **ohne eine einzige Prüfung** des Inhalts |
| `BillingPdfService` | 93,0 % | durchlaufen; der PDF-Inhalt wird nie geprüft |
| `BillingService` | 87,7 % | ein Szenario mit einem USt-Satz; Fehlerpfade, zweiter USt-Satz, Freie kWh nicht |
| `ParticipantAmountService` | 63,6 % | nur über die Vorschau |
| `BillingConfigService` | 57,3 % | nur Speichern/Lesen; Bilder nicht |
| `BillingDocumentService` | 43,4 % | `findByTenantIdAndYear` nie |
| `BillingRunService` | 38,5 % | Löschen nie |
| `InMemoryLockRepository` | 16,0 % | die Sperre je Gemeinschaft nie |
| `BillingRunResource` | 14,7 % | kein Endpunkt getestet |
| `JwtTokenService` / `JwtRequestFilter` | 12,9 % / 21,1 % | Token- und Tenant-Prüfung nie (dadurch blieb `known-errors.md` #1 unentdeckt) |
| `EmailService` / `BillingDocumentMailService` | 11,1 % / 9,4 % | Mailversand nie |
| `BillingDocumentNumberService` | 8,6 % | |
| `BillingConfigResource` | 8,2 % | |
| `BillingDocumentItemService` | 6,1 % | |
| `RestExceptionHandler` | 3,3 % | Fehlerformat nie |

**Abdeckung ist hier ein Wegweiser, kein Qualitätsmaß:** Die zwei am besten „abgedeckten“ Klassen
(XLSX, PDF) werden nur ausgeführt, nicht geprüft.

### 2.3 CI

`rolling-release.yml` baut und veröffentlicht das Image ohne Tests (`known-errors.md` #6,
`open-points.md` B-2). `snyk.yml` prüft nur den eigenen Code und bricht nie ab.

## 3. Schwächen der bestehenden Tests

| # | Schwäche | Folge |
|---|---|---|
| T1 | `contextLoads` und `testContainer` in beiden Integrationsklassen prüfen nichts Fachliches | zählen als Tests, schützen nichts |
| T2 | `testBillingXlsxService` hat keine Assertion | 99 % Abdeckung ohne Aussage |
| T3 | Das Ergebnis von `doBilling` (`abstractText`) wird nie geprüft | ein abgefangener Fehler (Abschnitt 4, F1) fällt nicht auf, wenn die Teildaten zufällig passen |
| T4 | Annahmen über die Reihenfolge der Positionen (`get(0)`, `get(1)`) ohne `ORDER BY` | können je nach Datenbank-Plan kippen |
| T5 | Eine Fixture (649 Zeilen SQL), ein USt-Satz (10 %), ein Szenario | Varianten (2 Sätze, Freie kWh, Rabatt, nur Erzeuger, Nullbeleg) ungetestet |
| T6 | Beträge der Zuteilungen über `Double.parseDouble` | Rundungsfehler im Testaufbau selbst möglich |
| T7 | `LocalDate.now()` in Tests und Code, daneben fest 2022/2023 | Tests können am Jahreswechsel kippen |
| T8 | Auskommentierte Assertions (`numberOfInvoices`), TODOs für Archiv und XLSX | Lücken sind bekannt, aber nicht verfolgt |
| T9 | `spring.jpa.show-sql=true` im Test | unübersichtliche Ausgabe, Fehler gehen im Rauschen unter |
| T10 | Die ID eines Teilnehmers in `TEST_ALLOCATIONS` hat eine fehlerhafte letzte Gruppe (`…-0c31aa53a49`) | prüft unbeabsichtigt nur den Pfad „nicht gefunden“ |
| T11 | `MassDataGenerator` liegt unter `src/test`, ist aber ein `main()`-Programm, kein Test | gehört nach `tools/` oder `massdatatest/` |
| T12 | `target/` wird zwischen Branches nicht geleert: Berichte (und kompilierte Klassen) eines anderen Branches bleiben liegen | Messungen immer mit `mvn clean` |

## 4. Gefundene Fehler (nur festgehalten, nicht behoben)

F*n* steht in `known-errors.md` als #(*n* + 11), also F1 = #12 … F18 = #29. „Gelesen“ = im Code nachvollzogen; „Verdacht“ = plausibel, aber nicht durch einen Lauf belegt. Jede
Zeile nennt den Test, der den Fehler zuerst sichtbar machen soll (Abschnitt 6).

| # | Schwere | Fund | Beleg | Status | Test, der ihn zeigt |
|---|---|---|---|---|---|
| F1 | hoch | **`doBilling` fängt jede Exception innerhalb von `@Transactional` ab und kehrt normal zurück.** Fehler aus dem eigenen Code (Belegdatum in der Zukunft, Lauf schon abgeschlossen, mehr als zwei USt-Sätze, NPE) rollen nichts zurück: bis dahin erzeugte Belege, Positionen, PDFs und **verbrauchte Belegnummern** bleiben gespeichert. Fehler aus Repository-Proxies markieren die Transaktion dagegen als rollback-only (Verdacht: `UnexpectedRollbackException` beim Commit). | `BillingService.java:59-60, 165-169` | gelesen | Integrationstest: Lauf mit drittem USt-Satz → keine Belege, keine Nummern |
| F2 | hoch | **Belege mit Betrag 0 werden ohne Abrechnungslauf gespeichert.** `createBillingDocument` speichert den Beleg, bevor die Beträge feststehen; den Lauf bekommt er nur bei einem Betrag ungleich 0. Solche Belege löscht `deleteByBillingRunId` nie, und `/api/billingDocuments/tenant/{id}/{year}` scheitert an ihnen mit einer NPE (`getBillingRun().getId()`). Die bestehende Fixture erzeugt solche Belege. | `BillingService.java:218-226, 349`; `BillingDocumentService.java:134` | gelesen | Integrationstest: nach dem Lauf keine Belege ohne Lauf; Jahresliste liefert 200 |
| F3 | hoch | **Die Sperre je Gemeinschaft schließt parallele Läufe nicht aus.** `releaseLock` entfernt den Eintrag, während andere Threads noch auf das alte Objekt warten; ein später kommender Thread bekommt ein neues Objekt und läuft parallel. Die Sperre verfällt außerdem nach 15 Minuten, auch wenn der Lauf noch läuft; zwischen `compute` und `get` kann ein `releaseLock` eine NPE auslösen. | `InMemoryLockRepository.java:29-45`; `BillingResource.java:31-38` | gelesen | Unit-Test mit drei Threads auf dieselbe Gemeinschaft |
| F4 | mittel | **Vergabe der Belegnummern ohne Sperre:** höchste Nummer lesen, dann speichern. Zwei gleichzeitige Läufe (siehe F3) scheitern an der Eindeutigkeit. Präfix `" R"` und `"R"` haben getrennte Folgen, ergeben aber dieselbe formatierte Nummer. | `BillingDocumentNumberGeneratorImpl.java:43-48`; `V1_0__init_schema.sql:7` | gelesen | Unit-Test (Präfix); Integrationstest mit zwei Threads |
| F5 | mittel | **USt-Sätze werden mit `BigDecimal.equals` verglichen**, das die Nachkommastellen beachtet: 20 und 20.00 gelten als verschiedene Sätze → zwei Summen oder „More than 2 VAT rates“. Ob die Stammdaten-Ansicht unterschiedliche Skalen liefert, ist offen. | `BillingService.java:598-610` | gelesen (Auswirkung: Verdacht) | Unit-Test der USt-Summen mit 20 und 20.00 |
| F6 | mittel | **Teilnahmegebühr speichert den USt-Satz ungeprüft** (`vatPercent` statt des null-sicheren Werts); ist er leer, scheitert die Summenbildung mit einer NPE, die F1 verschluckt. | `BillingService.java:510, 587` | gelesen | Integrationstest: Tarif ohne USt-Satz für die Gebühr |
| F7 | mittel | **Mailstatus:** Prüfen-dann-Setzen ohne Sperre; eine Exception nach „IN PROGRESS“ lässt den Status hängen, erneutes Senden ist dann gesperrt; „SENT“ wird auch gesetzt, wenn jede Mail scheiterte. | `BillingDocumentMailService.java:106-114, 144` | gelesen | Unit-Test mit Mock-`JavaMailSender`, der wirft |
| F8 | mittel | **`PUT /api/billingConfigs/{id}` prüft nur die Gemeinschaft im Body**, nicht die des gespeicherten Datensatzes. | `BillingConfigResource.java:127-131` | gelesen; behoben auf `fix-tenant-claim` (89725e8, nicht gemergt) | WebMvc-Test |
| F9 | mittel | **Fremde ID ergibt 500 statt 403:** `AccessDeniedException` aus `validateTenant` landet in `handleThrowable` (500 mit Klassenname); unbekannte ID ergibt 404 – die Existenz fremder Datensätze ist so unterscheidbar. | `RestExceptionHandler.java:43-50` | gelesen | WebMvc-Test: eigene / fremde / unbekannte ID |
| F10 | mittel | **`GET /{id}/footerImage` verlangt einen Datei-Upload** (`@RequestParam MultipartFile`) und ist so nicht benutzbar; `GET …/logoImage` ohne Bild → 500. | `BillingConfigResource.java:94, 103-108` | gelesen | WebMvc-Test |
| F11 | mittel | **Abrechnungslauf löschen scheitert**, sobald er Belege hat (Fremdschlüssel ohne Kaskade) → 500. | `BillingRunService.java:67`; `V1_0__init_schema.sql:8` | gelesen | Integrationstest |
| F12 | niedrig | **Rundung:** kWh werden vor der Preisberechnung auf 2 Stellen gerundet; USt je Position gerundet und dann summiert (nicht je Satz auf die Nettosumme). Fachlich zu klären. | `BillingService.java:417, 451, 605` | gelesen | Unit-Tests mit Grenzwerten, nach Klärung |
| F13 | niedrig | **`ParticipantAmountService`:** Beträge der Zählpunkte für Erzeuger negativ, die Summe aber aus positiven Bruttowerten. | `ParticipantAmountService.java:50-52` | Verdacht | Unit-Test, nach Klärung mit dem Frontend |
| F14 | niedrig | **Zeitzone:** `LocalDate.now()` in der JVM-Zeitzone bestimmt Belegjahr und damit die Nummernfolge; am Jahreswechsel abhängig von der Server-Zeitzone. | `BillingService.java:70, 274`; `BillingDocumentService.java:42` | Verdacht | Test mit fester Uhr (braucht Code-Änderung, Phase 4) |
| F15 | niedrig | `BillingConfigService.DEFAULT` ist ein öffentliches, veränderbares statisches Objekt und wird in jeden Lauf ohne Konfiguration gereicht. | `BillingConfigService`; `BillingService.java:83` | Verdacht | Unit-Test: zwei Läufe ohne Konfiguration |
| F16 | niedrig | Bild ersetzen löscht das alte Bild vor dem Update; falscher Dateityp endet als 500. | `BillingConfigService.java:75-88` | gelesen | WebMvc-Test |
| F17 | niedrig | `ZipOutputStream` und `XSSFWorkbook` nicht in try-with-resources (im Speicher, geringe Wirkung); `handleThrowable` nutzt `printStackTrace()` statt Logger. | `BillingDocumentArchiveService.java:50`; `BillingDocumentXlsxService.java:233`; `RestExceptionHandler.java` | gelesen | – |
| F18 | niedrig | `startCleanupTask` der Sperre wird nie aufgerufen (toter Code); viele auskommentierte Endpunkte. | `InMemoryLockRepository.java:50` | gelesen | – |

## 5. Hindernisse für Tests

| Hindernis | Wo | Umgehung ohne Code-Änderung | Lösung mit Code-Änderung (Phase 4) |
|---|---|---|---|
| Rechenlogik nur über `doBilling` erreichbar (622 Zeilen, 10 Abhängigkeiten, private Methoden) | `BillingService` | Integrationstests mit Fixture-Varianten | Rechner (`BillingCalculator`) als eigene, reine Klasse herauslösen |
| Zeit nicht injizierbar (`LocalDate.now()`) | Service, Sperre, Domain | Tests mit relativen Daten | `java.time.Clock` als Bean |
| Statischer Zustand (`TenantContext`, `BillingPdfService.defaultReport`, `BillingConfigService.DEFAULT`) | | im Test setzen und im `@AfterEach` leeren | Tenant als Request-Attribut; Report als Bean |
| Sperre im Controller statt im Service, konkrete Klasse statt Interface | `BillingResource` | WebMvc-Test oder Unit-Test der Sperre allein | Sperre in den Service, `LockRepository` injizieren |
| Jasper kompiliert das Template beim ersten Aufruf (langsam) | `BillingPdfService` | einmal pro Testkontext (Spring-Kontext-Cache) | vorkompiliertes `.jasper` |
| Stammdaten aus der Ansicht eines anderen Dienstes | `base.billing_masterdata` | SQL-Fixture legt sie an | Vertragstest gegen die Backend-Migration (Phase 6) |

## 6. Maßnahmen in Phasen

Jede Phase ist eine eigene Änderung mit eigenem Review. Die Phasen 1 – 3 brauchen **keine**
Änderung am Produktionscode, nur neue Tests.

### Phase 0 – Grundlage

| Maßnahme | Art | Entscheidung |
|---|---|---|
| Tests in der CI vor dem Image-Bau, mit Docker für Testcontainers | CI | `open-points.md` B-2 |
| JaCoCo-Plugin in die `pom.xml`, Bericht als CI-Artefakt | Build, neue Quelle | `open-points.md` B-11 |
| Mindestabdeckung als Schwelle, die nur steigen darf (Start: heutiger Wert je Paket) | Build | B-11 |
| Testdaten-Builder (`BillingMasterdataBuilder`, `AllocationBuilder`) statt einer großen SQL-Fixture für jede Variante | Test-Code | – |
| `show-sql` im Test aus, Assertions direkt (`assertThat(x, is(…))` statt `boolean`-Helfer) | Test-Code | – |
| Messungen immer mit `mvn clean` (T12) | Doku | – |

### Phase 1 – Günstige Unit-Tests (kein Spring, kein Docker)

| Ziel | Was geprüft wird |
|---|---|
| `BigDecimalTools` | `makeZeroIfNull`, `isNullOrZero`, `makeGermanString` (Rundungsmodus, Tausenderpunkt, Einheit) |
| `StringTools.nullSafeJoin` | null, leer, gemischt |
| `ClearingPeriodIdentifierTool` | zusätzlich die Produktionsform `Abr_YQ-2023-3` |
| `BillingDocumentNumberGeneratorImpl` (Repository gemockt) | Stellenbegrenzung, Präfix null/leer/mit Leerzeichen (F4), Startwert |
| `InMemoryLockRepository` | Sperre, Freigabe, Ablauf; F3 mit drei Threads (rot, bis behoben) |
| `EmailService` (`JavaMailSender` gemockt) | abgelehnte Adressen, eingebettetes Bild vs. Anhang |
| `BillingDocumentMailService` (Mocks) | Statusfolge, Fehler beim Senden (F7) |
| `ParticipantAmountService` | Erzeuger/Verbraucher, Teilnahmegebühr (F13 nach Klärung) |
| `BillingDocument.getDocumentTypeName` | alle Belegarten |

### Phase 2 – Web-Schicht (`@WebMvcTest` mit der echten Security-Konfiguration)

Pro Resource eine Testklasse. Pflichtmatrix für **jeden** Endpunkt:

| Fall | Erwartung |
|---|---|
| ohne Token | 401 |
| Token ohne Rolle `EEG_ADMIN` | 403 |
| eigene Gemeinschaft | 200 / 201 / 204 |
| fremde Gemeinschaft im Header oder Datensatz | 403 (heute 500, F9) |
| fehlender `Tenant`-Header | 403 |
| unbekannte ID | 404 |
| ungültiger Body | 400 mit `fieldErrors` |

Dazu: F8, F10, F16 und das Fehlerformat von `RestExceptionHandler`. Die Tests für die
Tenant-Prüfung liegen auf `fix-tenant-claim` schon vor (`JwtRequestFilterTests`,
`JwtTokenServiceTests`, `BillingConfigResourceTests`); sie kommen mit dem Merge.

### Phase 3 – Abrechnungs-Szenarien (Integration, Testcontainers)

Eine Szenario-Matrix, je Szenario ein kleiner Datensatz über die Builder aus Phase 0. Geprüft werden
Belegarten, Netto/USt/Brutto je Beleg, Summen je Satz, Belegnummern, Status des Laufs **und** der
Ergebnistext.

| # | Szenario | Deckt ab |
|---|---|---|
| S1 | nur Verbraucher, ein USt-Satz | Grundfall |
| S2 | Verbraucher und Erzeuger, Reverse Charge vs. Info-Gutschrift | Belegart-Wahl |
| S3 | zwei USt-Sätze (10 % / 20 %) | zweite USt-Summe |
| S4 | drei USt-Sätze | Fehlerpfad; **F1** (nichts darf gespeichert bleiben) |
| S5 | gleiche Sätze mit unterschiedlicher Skala (20 / 20.00) | **F5** |
| S6 | Freie kWh größer, gleich, kleiner als der Verbrauch | Freie kWh |
| S7 | Rabatt, Teilnahmegebühr, Zählpunktgebühr, Gebühr ohne USt-Satz | **F6** |
| S8 | Teilnehmer mit Betrag 0 | **F2** |
| S9 | Belegdatum in der Zukunft; Lauf bereits abgeschlossen | Fehlerpfade, **F1** |
| S10 | Vorschau, dann Endabrechnung, dann erneute Vorschau | Löschen alter Belege, Nummern nur final |
| S11 | Rundungsgrenzen (x,xx5 €, sehr kleine Mengen) | **F12**, nach fachlicher Klärung |
| S12 | Lauf löschen nach Endabrechnung | **F11** |

Ausgaben: PDF-Text mit PDFBox extrahieren und Kernwerte prüfen (neue Test-Bibliothek, Entscheidung
B-12); XLSX mit POI lesen und Summen prüfen (T2).

### Phase 4 – Umbauten für Testbarkeit (Code-Änderung, eigene Entscheidung)

- Rechenlogik aus `BillingService` in eine reine Klasse ziehen (Positionen, USt-Summen,
  Belegart); danach die Szenarien S3 – S7, S11 zusätzlich als schnelle Unit-Tests.
- `Clock` als Bean (F14, T7); Sperre in den Service und über das Interface (F3).
- `BillingService` dadurch unter die Größengrenze (`open-points.md` B-6).

### Phase 5 – Nebenläufigkeit und Robustheit

- Zwei gleichzeitige Läufe derselben Gemeinschaft (F3, F4); zwei verschiedener Gemeinschaften
  parallel (dürfen sich nicht blockieren).
- Mailversand mit teilweise fehlerhaften Adressen und SMTP-Fehler (F7) gegen einen Test-SMTP-Server
  (z. B. GreenMail; neue Quelle, Entscheidung B-12).

### Phase 6 – Verträge mit den Nachbarn

- **Stammdaten-Ansicht:** Test, der die Ansicht `base.billing_masterdata` aus den Migrationen von
  `eegfaktura-backend` aufbaut und prüft, dass jede Spalte existiert, die `BillingMasterdata` liest
  (`known-errors.md` #10).
- **Aufrufer:** die DTOs, die `eegfaktura-web` und eegfaktura-v3 senden, als JSON-Fixtures; ein Test
  je Endpunkt, dass sie angenommen werden.

## 7. Ziele und Kennzahlen

| Kennzahl | heute | nach Phase 2 | nach Phase 3 | nach Phase 4 |
|---|---:|---:|---:|---:|
| Zeilen gesamt | 55,6 % | ≥ 65 % | ≥ 75 % | ≥ 85 % |
| Zweige gesamt | 56,1 % | ≥ 60 % | ≥ 70 % | ≥ 80 % |
| `rest` Zeilen | 17,9 % | ≥ 85 % | ≥ 85 % | ≥ 90 % |
| `security` Zweige | 0 % | 100 % | 100 % | 100 % |
| Endpunkte mit Tenant-Matrix | 0 von 31 | alle | alle | alle |
| Szenarien der Abrechnung | 1 | 1 | 12 | 12 + Unit |
| Tests in der CI | nein | ja | ja | ja |

Die Prozentwerte sind Richtwerte, keine Selbstzweck-Ziele: Ein Test ohne fachliche Prüfung zählt
nicht (T1, T2). Optional nach Phase 4: Mutationstests (PIT) für die Rechenlogik, um die Aussagekraft
der Tests zu messen (Entscheidung B-12).

## 8. Anhang

### 8.1 Messung reproduzieren

Ohne Änderung der `pom.xml`, JDK 21 im Builder-Image:

```bash
docker run --rm -v "$PWD":/src -w /src -v ~/.m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --add-host=host.docker.internal:host-gateway \
  maven:3-eclipse-temurin-21 \
  mvn -B clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report
# Bericht: target/site/jacoco/index.html, Rohdaten target/site/jacoco/jacoco.csv
```

JaCoCo 0.8.13 ist am 02.04.2025 erschienen (Maven Central), Lizenz EPL-2.0. Es wurde nur für diese
Messung geladen und ist nicht Teil des Builds (`open-points.md` B-11).

### 8.2 Quellen

Code-Durchsicht von `src/main` und `src/test` am 01.10.2026; die Sicherheitsprüfung vom selben Tag
(`known-errors.md` #1); `eegfaktura-analyze-it/state-of-testing.md` (01.09.2026).
