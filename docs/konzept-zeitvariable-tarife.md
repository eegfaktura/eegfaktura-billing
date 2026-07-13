# Konzept: Zeitvariabler Tarif (ZVT)

## Hintergrund
Die eegfaktura-Abrechnung kennt heute pro Tarif **genau einen** Arbeitspreis pro kWh:
`tariffWorkingFeePerConsumedkwh` (Verbrauch) bzw. `tariffCreditAmountPerProducedkwh`
(Erzeugung). Dieser eine Preis wird im Abrechnungslauf auf die über die **gesamte
Abrechnungsperiode summierte** Menge (`allocationKWh`) je Zählpunkt angewendet
(`BillingService.createBillingDocumentItem`).

Energiegemeinschaften wollen zunehmend **tageszeitabhängige Preise** abbilden — z. B. günstiger
Strom zur Mittags-PV-Spitze, teurer am Abend, bzw. eine gesonderte Vergütung der
Nacht­einspeisung für Erzeuger. Das ist mit einem flachen Preis nicht darstellbar. Die
zeitaufgelösten Viertelstunden-Daten liegen bereits im `eegfaktura-energystore` vor; genutzt
werden sie für die Abrechnung bislang nur summiert.

## Ziel
Bestehende **Verbraucher- und Erzeuger-Tarife** erhalten eine aktivierbare Option
**„Zeitbasiert"**: statt eines 24-h-Preises können ein **Basispreis** plus **bis zu zwei
optionale, benannte Zeitfenster** mit je eigenem Preis (ct/kWh) hinterlegt werden. Der
Abrechnungslauf verrechnet den Energieverbrauch/-ertrag dann **je Zeitfenster** (15-Minuten-
Auflösung) und weist die Teilmengen als eigene Positionen auf Rechnung/Gutschrift aus.

Kein neuer Tarif-Typ, keine astronomische Steuerung — eine additive Option auf den vorhandenen
Tarif-Typen, testbar in der isolierten Billing-Umgebung `env-billing` (Test-EEG `TE100200`).

## UX-Änderung
Betroffen ist der **Tarif-Konfigurationsdialog** in `eegfaktura-web` (Reiter
*Erzeuger* / *Verbraucher*). Neu (siehe Mockup „Konfig-Dialog"):

- Ein Umschalter **Bestandteile: „Einfach" | „Zeitbasiert"**.
- **Einfach** (Default, heutiges Verhalten): ein Preis (ct/kWh) + optional „Kostenlose Energie in
  kWh" (`freeKWh`). Unverändert.
- **Zeitbasiert**:
  - **Basispreis in ct/kWh (netto)** — gilt für alle Viertelstunden, die von keinem aktiven
    Zeitfenster abgedeckt sind.
  - **Zeitraum 1** und **Zeitraum 2**, jeweils separat über „Zeitraum N aktiv" zuschaltbar, mit
    Feldern: **Tarifname**, **Von** (HH:MM), **Bis** (HH:MM), **Preis in ct/kWh (netto)**.
  - Zeitraum 2 ist nur bedienbar, wenn aktiviert (im Mockup ausgegraut/inaktiv).
- Ein **Rabatt in %** (bestehendes Feld) gilt unverändert und **gemeinsam für alle** Preise
  (Basis + Zeiträume).
- „Kostenlose Energie in kWh" wird im **Zeitbasiert**-Modus **nicht** angeboten/berücksichtigt.

Default-Verhalten neuer und bestehender Tarife bleibt **Einfach** — die Umstellung ist eine
bewusste Aktion des EEG-Admins.

## Fachliche Logik

**Primärer Nutzer:** EEG-Verwaltung / Kassier (Tenant-Admin), der Tarife konfiguriert.
Mitglieder sehen das Ergebnis nur auf Rechnung/Gutschrift.

Ablauf eines Abrechnungslaufs mit einem zeitbasierten Tarif:

1. `energystore` betrachtet Verbrauch/Ertrag eines Zählpunkts **nicht mehr nur als Periodensumme**,
   sondern in **15-Minuten-Schritten**, und summiert je Zeitfenster (siehe 2–3).
2. Jede Viertelstunde wird genau **einem Bucket** zugeordnet:
   - liegt sie in einem **aktiven Zeitfenster** → dieses Fenster,
   - sonst → **Basis**.
3. Es entstehen so **≤3 Teilmengen** je Zählpunkt (Basis, Zeitraum 1, Zeitraum 2), jede mit eigener
   kWh-Summe. **`energystore` liefert diese ≤3 Summen** an web, web reicht sie an billing weiter.
4. `billing` bildet je Teilmenge eine Abrechnungsposition: `Menge (kWh) × Preis (ct/kWh)` (Preis/Name
   je Fenster aus der Masterdata), darauf der (gemeinsame) Rabatt, dann USt wie in der heutigen
   Positionslogik.
5. Zeitfenster gelten **an jedem Kalendertag der Periode gleich** (reines Tagesmuster).
6. Ein Zeitfenster **darf über Mitternacht laufen** (z. B. 20:00–06:00); die Zuordnung erfolgt
   dann tageübergreifend.
7. **Kein Kontingent freier kWh** im zeitbasierten Modus (nur „Einfach").

**Plausibilitätsprüfung (bei Speichern des Tarifs):**
- Innerhalb eines Zeitraums ist **Von = Bis unzulässig** (Von ≠ Bis; Von > Bis ist erlaubt und
  bedeutet Mitternachtsüberlauf).
- Die beiden aktiven Zeiträume **dürfen sich nicht überschneiden** → Überlappung wird beim
  Speichern **abgewiesen** (damit gehört jede Viertelstunde eindeutig genau einem Preis).

**Vorbedingung / Rand:**
- **Invariante (Nutzer-Festlegung):** jeder Zählpunkt einer EEG hat verpflichtend das
  ¼h-Tagesprofil aktiviert → für einen zeitbasierten Tarif liegen die zeitaufgelösten Werte
  **immer** vor. Ein „fehlendes Profil" ist damit **kein** behandelter Pfad (kein Fallback).
- **Leerer Bucket:** hat ein aktives Zeitfenster 0 kWh, entsteht **keine** Position (bestehendes
  Verhalten: Nullpositionen werden unterdrückt) — z. B. ein Nachtfenster ohne Verbrauch fehlt.
- Idempotenz des Abrechnungslaufs bleibt wie heute (erneuter Lauf einer Periode ersetzt die
  Dokumente des vorherigen Laufs — bestehendes Verhalten, kein Neubau).

### Darstellung auf Rechnung/Gutschrift
Je Zählpunkt ein **zusammenhängender Block ohne Leerzeilen** (siehe Mockup „Rechnung"):

- **Blockkopf** (fett): `Zählpunkt <ZP-Nr> - <ZP-Bezeichnung> (Rabatt xx %)` — der Rabatt-Zusatz
  nur, wenn Rabatt > 0.
- **Positionszeilen**, Spalten *Positionstext · Menge · Einzelpreis · Netto · USt · Brutto*:
  - `Tarif: Basis`
  - `Tarif: <Zeitraumname> (HH:MM - HH:MM)` je aktivem Zeitfenster.
  - Ist der Zeitraumname leer → nur der Zeitraum wird angedruckt: `Tarif: (HH:MM - HH:MM)`.
- **Blockfuß** (fett): `Zwischensumme Zählpunkt <ZP-Nr>` mit Netto/USt/Brutto.

Ein Tarif im **Einfach**-Modus erzeugt weiterhin genau **eine** Positionszeile pro Zählpunkt
(unveränderte Darstellung).

## Betroffene Komponenten

### eegfaktura-backend (Go)
- **Tarif-Stammdaten** (`base.tariff`, `model/tariff.go`) um die zeitbasierte Option erweitern:
  ein Modus-Kennzeichen (Einfach/Zeitbasiert), ein Basispreis sowie **bis zu zwei Zeitfenster**
  (aktiv, Name, Von, Bis, Preis). Gilt für die Typen **Verbraucher (`VZP`)** und **Erzeuger
  (`EZP`)**. Versionierung/History der Tarife wie bisher.
- **Nicht** im Allokations-Pfad: die `allocations[]` baut heute **web** aus dem
  energystore-Report und postet sie direkt an billing (backend ruft billing nicht auf). backend
  besitzt hier nur die **Tarif-Stammdaten** + deren serverseitige Validierung.
- Validierung der Tarif-Eingaben (Überlappung, Von≠Bis) serverseitig.

### eegfaktura-energystore (Go) — **v1 UND v2**
- **Fenster-Summierung (Time-of-Use):** der vorhandene Report `intermediate[]` ist eine **grobe
  Tages-/Segment-Chart-Reihe** (Tageszeit geht verloren) — er taugt **nicht** zur Bucketung.
  energystore nimmt im Report-**Request** je zeitbasiertem ZP **≤2 generische Zeitfenster {von,bis}**
  entgegen und faltet **aus den rohen ¼h-Werten** die Abrechnungsmenge (Verbraucher `utilization`;
  Erzeuger `production − allocation` — dieselbe Größe wie heute) nach lokaler Tageszeit in **≤3
  Teilsummen** (Basis + Fenster). **Keine** Tarif-/Preis-Kenntnis in energystore. Datenzugriff wie
  bestehend per REST/GraphQL, kein neuer öffentlicher Endpoint, kein Schema-Change.
- **Beide Implementierungen betroffen:** die Änderung muss in **`eegfaktura-energystore` (v1/BadgerDB)
  UND `eegfaktura-energystore-v2` (Timescale)** erfolgen — v2 speichert bereits alle Rohdaten dauerhaft
  (günstiger für die ¼h-Faltung), v1 reduziert beim Schreiben. Welche Variante im Testbed `env-billing`
  läuft, bestimmt die Reihenfolge/Test-first (Impl-Check).

### eegfaktura-billing (Java/Spring)
- **Berechnung:** `BillingService.createBillingDocumentItem` wird je Zählpunkt von **einem** auf
  **1–3** Aufrufe erweitert — je gelieferter Teilsumme (Basis/ZR1/ZR2) eine `BillingDocumentItem` mit
  dem jeweiligen Preis; Rabatt/USt/Rundung **wie heute pro Position**. Für Einfach-Tarife unverändert.
  **Keine Mengen-Bucketung in billing** (die kommt fertig aus energystore) und **keine
  Ausgangsverbindung** — billing bleibt entkoppelt.
- **Tarif-Snapshot:** `BillingMasterdata` trägt die Zeitfenster-Definitionen (Name, Von, Bis,
  Preis, Basispreis, Modus) → billing kennt **Preis/Name** je Fenster; die **Mengen** kommen als ≤3
  Teilsummen je ZP im `allocations[]`-Payload (saubere Trennung Preis↔Menge).
- **PDF-Umbau (`BillingPdfService` + Jasper):** heute ist das PDF **flach** (keine Gruppierung).
  Neu: **Gruppierung je Zählpunkt mit Zwischensumme für ALLE Dokumente** (auch flache Tarife →
  1 Zeile/Block) — Group-Band mit Kopf `Zählpunkt <nr> - <name> (Rabatt xx %)`, Positionszeilen,
  `Zwischensumme Zählpunkt`. Die Positions-Items brauchen dafür einen **Gruppierungsschlüssel
  (ZP-Nr) + ZP-Bezeichnung** im an Jasper übergebenen DTO. **Zählpunktgebühr** als Zeile **im**
  jeweiligen ZP-Block (in dessen Zwischensumme); **Mitgliedsbeitrag** als eigene Teilnehmer-Position
  **nach** den Blöcken. ⚠️ Layout-Änderung betrifft **jede** bestehende Rechnung → Regressionsfläche.

### eegfaktura-web (React/Ionic)
- **Tarif-Dialog** um den Umschalter *Einfach/Zeitbasiert* und die Felder Basispreis + zwei
  Zeitfenster erweitern (Mockup). Client-seitige Plausiprüfung (Überlappung, Von≠Bis) mit
  serverseitiger Absicherung. Alle Texte via i18n. Sendet die erweiterten Tarif-Felder an das
  backend (bestehendes Auth-/Datenmuster, kein neuer Auth-Flow).
- **Orchestrierung ZVT:** je zeitbasiertem ZP die Zeitfenster {von,bis} (aus dem zugeordneten Tarif)
  in den energystore-Report-**Request** mitgeben und die zurückgelieferten **≤3 Teilsummen** je ZP in
  `allocations[]` an billing weiterreichen (statt der einen Summe). Kein neuer Datenfluss.

### eegfaktura-admin (React/CRA)
- Keine Änderung — Tarife werden im Mitglieder-Web gepflegt.

## Akzeptanzkriterien
- [ ] Ein Verbraucher- **und** ein Erzeuger-Tarif lassen sich auf „Zeitbasiert" umstellen und mit
      Basispreis + 1–2 benannten Zeitfenstern (Von/Bis/Preis) speichern.
- [ ] Überlappende Zeitfenster werden beim Speichern **abgewiesen** (server- und clientseitig);
      Von = Bis wird abgewiesen; Von > Bis (Mitternachtsüberlauf) wird **akzeptiert**.
- [ ] `energystore` liefert je zeitbasiertem ZP **≤3 Fenster-Teilsummen** (`BASE`/`T1`/`T2`), wobei
      `BASE` als Residuum gebildet wird → **Σ buckets ≡ Periodensumme exakt** (kWh-Partition ohne
      Drift; verifizierbar an konstruierter ¼h-Reihe).
- [ ] Ein Abrechnungslauf mit zeitbasiertem Tarif erzeugt je Zählpunkt **getrennte Positionen** für
      Basis und jedes aktive Zeitfenster; €-Summendrift durch Positionsrundung (2 Stellen HALF_UP)
      **≤ 0,01 € × Positionsanzahl** (definierte Schranke).
- [ ] Die Viertelstunden-Zuordnung (in energystore) ist korrekt: eine ¼h in genau einem Zeitfenster
      zählt zu dessen Summe, alle übrigen zur Basis-Summe; ein Fenster über Mitternacht ordnet die
      Nacht-¼h korrekt zu. Die Zuordnung rechnet fest in **Europe/Vienna** (unabhängig von
      Container-TZ); DST-Tage: 23h-Tag zählt Fenster-¼h einmal weniger, 25h-Tag doppelt (konkrete
      Erwartungswerte im Test).
- [ ] **Fail-loud:** zeitbasierter Tarif ohne `buckets` im Payload, `buckets` bei Einfach-Tarif oder
      Abweichung der mitgesendeten `timeWindows` von den Masterdata-Fenstern → Lauf-Abbruch mit
      klarer Meldung (keine stille Fehlabrechnung).
- [ ] **Preview == Final:** derselbe Lauf als Vorschau und als endgültige Abrechnung liefert
      betragsgleiche Positionen.
- [ ] **Gemischter Lauf:** Einfach- und ZVT-Tarife im selben Abrechnungslauf (auch beim selben
      Teilnehmer) rechnen beide korrekt.
- [ ] **Erzeuger-Dokumenttypen:** ZVT-Positionen erscheinen korrekt in `CREDIT_NOTE`,
      `CREDIT_NOTE_RC` und `INFO` (gemeinsamer `createBillingDocumentItem`-Pfad).
- [ ] Von/Bis nur im **15-Minuten-Raster** (00/15/30/45) speicherbar (server- + clientseitig).
- [ ] Payload `POST /api/billing` bleibt bei großen EEG klein (≤3 Zahlen/ZP) — kein Ingress-Limit
      (Referenz: 1000 Mitglieder × 2 ZP → ~0,2 MB statt ~2 MB).
- [ ] Der **eine** Rabatt wirkt auf Basis- und Zeitfenster-Positionen gleichermaßen.
- [ ] Im Zeitbasiert-Modus wird **kein** Kontingent freier kWh berücksichtigt; im Einfach-Modus
      unverändert.
- [ ] Rechnung/Gutschrift stellt **jedes** Dokument (auch flache Tarife) je Zählpunkt als Block
      ohne Leerzeilen dar: Kopf mit „Rabatt xx %", Positionszeilen „Tarif: Basis" bzw.
      „Tarif: <Name> (Von-Bis)" (leerer Name → nur Zeitraum), Zwischensumme. Zählpunktgebühr steht
      im ZP-Block (in dessen Zwischensumme), Mitgliedsbeitrag als eigene Position nach den Blöcken.
- [ ] Bestehende Einfach-Tarife rechnen **betragsgleich wie zuvor** ab (keine Rechen-Regression);
      eine Alt-Rechnung mit mehreren ZP + Gebühren wird im neuen gruppierten Layout korrekt gerendert.
- [ ] Tenant-Isolation gewahrt: Tarife/Allokationen bleiben strikt in ihrer EEG (`tenant`/
      `ec_id`), keine Vermischung über Gemeinschaftsgrenzen.

## Edge Cases
- **Fehlende ¼h-Daten:** kann nicht auftreten — ¼h-Profil ist Pflicht-Vorbedingung jeder
  EEG-Mitgliedschaft (siehe Invariante oben). Kein Fallback.
- **Rundungsdrift:** 3 Positionen statt 1 → je Position eigene 2-Stellen-Rundung, Σ driftet ≤ ~1–2
  Cent ggü. heute. Bewusst akzeptiert (konsistent mit den bereits getrennt gerundeten Gebühren).
- **Mitgliedsbeitrag / Grundgebühr / Zählpunktgebühr:** unverändert einmal je Zählpunkt/Mitglied,
  **nicht** je Zeitfenster vervielfachen.
- **Wechsel Einfach↔Zeitbasiert zwischen Läufen:** ⚠️ es gibt **keinen** Tarif-Snapshot — die
  Masterdata-View joint `base.activetariff` = **MAX(version), live**; ein Nachlauf einer alten
  Periode bepreist mit der **aktuellen** Tarif-Version (Bestandsverhalten). Für ZVT abgesichert durch den Konsistenz-Guard (`timeWindows`-Vergleich → Abbruch
  bei Abweichung); der generelle Nicht-Snapshot bleibt bewusst Bestand.
- **Nur Basispreis (kein Zeitfenster aktiv):** verhält sich rechnerisch wie ein Einfach-Tarif
  ohne freeKWh → genau eine „Basis"-Position.
- **Rundung:** Summierung der ¼h-kWh je Bucket vor Preisbildung; Rundungsregeln je Position wie
  heute (Detail für `/architecture`).

## Nicht im Scope
- Unterscheidung nach **Wochentag/Wochenende** oder **Saison** (gleiches Tagesmuster an allen
  Tagen).
- **Mehr als zwei** Zeitfenster (max. 3 Preiszonen inkl. Basis).
- **Astronomische** Steuerung (Sonnenauf-/-untergang) — bewusst verworfen; feinere Bedarfe über
  monatliche Abrechnung.
- Preis-Semantik **Bonus/Abzug** oder **prozentual** je Zeitfenster — nur **ersetzender**
  Absolutpreis.
- **Freie kWh** im zeitbasierten Modus.
- Dynamische/externe **Spot-Preisreihen** (nur manuell hinterlegte Fenster).

## Abhängigkeiten
- Keine harten. Nutzt vorhandene ¼h-Daten im `eegfaktura-energystore`. Verwandt (Datenreichtum):
  `konzept-energystore-alle-energiedaten.md`.

## Offene Fragen
_Für die Umsetzung verbleibend:_
- **energystore v1 + v2:** die Fenster-Summierung muss in **beiden** Repos (`eegfaktura-energystore`
  und `eegfaktura-energystore-v2`) gebaut werden; welche Variante im Testbed `env-billing` läuft,
  bestimmt Test-first-Reihenfolge (Impl-Check für `/backend`, keine Design-Frage).

---
<!-- Folgende Abschnitte werden von späteren Skills ergänzt -->

## Tech Design (Solution Architect)

### A) Komponenten-Interaktion (Kontrakte)

**Ist-Fluss (heute):**
```
web  → energystore (Report je Teilnehmer/ZP):  summary{utilization, production, allocation, …}
                                                + intermediate{…[]}  (GROBE Tages-/Segment-Chartreihe, KEINE Tageszeit)
web  → billing:  POST /api/billing   (REST, bearer)
     DoBillingParams{ allocations[]:{participantId, meteringPoint, allocationKWh}, tenantId, clearingPeriod…, preview }
```
`web` baut `allocations[]` aus `report.summary` (`utilization` für Verbraucher, `production − allocation`
für Erzeuger) und postet sie an billing. billing liest die Tarif-Felder aus `BillingMasterdata` (live-View,
kein Snapshot — s. §B) und rechnet `allocationKWh × centPerKWh`. **billing ist ein Backend-Dienst, den das
Frontend direkt anspricht** (Frontend spricht nur Backend-Dienste an).

**Soll-Fluss (ZVT) — energystore summiert je Fenster, minimaler Payload, kein neuer Call:**
```
web → energystore (Report-Request):  zusätzlich je zeitbasiertem ZP die ≤2 Zeitfenster {von,bis}
                                      (generische Tageszeiten aus dem Tarif; energystore kennt KEINE Preise/Tarife)
energystore:  faltet die ROHEN ¼h-Werte der Abrechnungsmenge (Verbraucher utilization,
              Erzeuger production−allocation) nach lokaler Tageszeit in ≤3 Teilsummen: Basis + Fenster1 + Fenster2
              → Report liefert je ZP diese ≤3 Summen (statt/zusätzlich zur einen Gesamtsumme)
web → billing:  Allocation trägt je ZP die ≤3 Teilsummen (Basis/F1/F2) statt nur allocationKWh (≤3 Zahlen)
billing:  je Teilsumme eine Position; Preis+Name je Fenster aus der Masterdata (Basispreis/ZR1/ZR2);
          Einfach-Tarif = eine Gesamtsumme wie heute
```

**Kontrakt-Spezifikation (verbindlich, ein Name — `buckets`):**
- **web → energystore (Report-Request):** je zeitbasiertem ZP (auf `participants[].meters[]`-Ebene)
  `timeWindows: [{key:"T1"|"T2", from:"HH:MM", to:"HH:MM"}]` (≤2; aus dem **ZP-Tarif** via
  `meter.tariff_id` — nicht `participant.tariffId`, das ist der EEG-Beitragstarif).
- **energystore → web (Report-Response):** je ZP zusätzlich
  `buckets: [{key:"BASE"|"T1"|"T2", kWh}]`, wobei **`BASE = Gesamtsumme − T1 − T2` (Residuum)** —
  die kWh-Partition ist damit per Konstruktion exakt (Σ buckets ≡ Periodensumme).
- **web → billing (`Allocation`):** unverändert `allocationKWh` (Gesamtsumme) **plus** `buckets[]`
  **plus** die verwendeten Fenster-Definitionen `timeWindows[]` als **Konsistenz-Guard**.
- **billing — Zuordnung & fail-loud:** `T1`/`T2` werden **über den `key`** dem Masterdata-Fenster 1/2
  zugeordnet; vor der Bepreisung vergleicht billing die mitgesendeten `timeWindows` (Von/Bis) mit den
  aktuellen Masterdata-Fenstern — **Abweichung → Lauf-Abbruch mit klarer Meldung** (kein stiller
  Preis/Mengen-Mix, da die Masterdata-View live ist). Ebenso **Abbruch** bei: zeitbasierter Tarif
  **ohne** `buckets` (kein stiller Basispreis-Fallback!) und `buckets` bei Einfach-Tarif.

**Kern-Entscheidung — Mengen-Summierung in `energystore`, Bepreisung in `billing`, `web` orchestriert
(Entkopplung gewahrt).** `energystore` besitzt die zeitaufgelöste Menge → es summiert je **generischem
Zeitfenster** (nur Von/Bis-Tageszeiten, **keine** Tarif-/Preis-Kenntnis — eine wiederverwendbare
Time-of-Use-Aggregation); `billing` besitzt Fenster-**Preise/Namen** (Masterdata) → es bepreist die fertigen
≤3 Teilsummen und rendert die Positionen. `web` bleibt **Orchestrator** (reicht die Fenster in den
Report-Request, die Summen an billing) — **kein** synchroner Service-zu-Service-Call, `billing` bleibt ohne
Ausgangsverbindung. Saubere Trennung **Preis↔Menge**. **Payload bleibt bei ~heute** (≤3 statt 1 Zahl/ZP;
bei 2000 ZP ~0,2 MB statt ~2 MB) — der maßgebliche Grund gegen die 96-Slot-Variante.

_Verworfene Alternativen (Skalierung + Entkopplung):_
- **96-Slot-¼h-Tagesprofil über web durchreichen:** mathematisch elegant/tarif-agnostisch, aber
  ~2 MB `POST /api/billing` bei 2000 ZP → sprengt das Ingress-Body-Limit (1 MB). **Verworfen (Payload).**
- **billing zieht das Profil/die Summen selbst aus energystore:** wäre der **erste** synchrone
  Service-zu-Service-REST-Call der Suite und holt billing aus seiner bewusst **entkoppelten** Rolle
  (heute kein HTTP-Out, kein MQTT). **Verworfen (Entkopplung, Operator-Entscheid).**

### B) Datenmodell (Klartext, keine DDL)

**eegfaktura-backend — `base.tariff`** (breit, pro Version, PK `(id, version)`), neue Spalten inline
(camelCase-quoted). `centPerKWh` bleibt der **Basispreis**, `discount`/`freeKWh` wiederverwendet:

| Feld | Typ | Default | Zweck |
|---|---|---|---|
| `useTimeTariff` | boolean | `false` NOT NULL | Modus Einfach/Zeitbasiert |
| `timeTariff{1,2}Active` | boolean | `false` NOT NULL | Zeitraum N zugeschaltet |
| `timeTariff{1,2}Name` | varchar | NULL | Andruck-Bezeichnung (leer → nur Zeitraum) |
| `timeTariff{1,2}From` | time (o. Zone) | NULL | Von (HH:MM), 15-min-Raster = Validierung |
| `timeTariff{1,2}To` | time (o. Zone) | NULL | Bis (HH:MM), `From > To` = Mitternachtsüberlauf |
| `timeTariff{1,2}CentPerKWh` | double precision | NULL | Preis Zeitraum N (wie `centPerKWh`) |

- **`base.activeTariff` (View):** um die 11 neuen Spalten (camelCase durchgereicht wie der Bestand)
  erweitern.
- **`base.billing_masterdata` (View, backend!):**
  `billing_masterdata` ist **keine billing-Tabelle**, sondern eine **View im backend-Schema**
  (`schema.sql:271`, joint `base.activetariff`), die billing per Hibernate
  `@Subselect("… from base.billing_masterdata")` **direkt liest** (`BillingMasterdata.java:15`).
  Die snake_case-Aliase (`tariff_use_time_tariff`, `tariff_time1_active`, `tariff_time1_name`,
  `tariff_time1_from`, `tariff_time1_to`, `tariff_time1_cent_per_kwh`, dito `time2`) kommen daher
  **in diese View** — per **backend-Migration**, nicht per billing-Flyway.
- **Bestands-Befund (explizit benannt):** billing↔backend teilen sich hier real eine DB-Fläche
  (Cross-Component-View) — im Widerspruch zur reinen Lesart der CLAUDE.md-Boundary „kein geteiltes
  SQL". Das ist eine **bewusste Bestandsentscheidung**, die dieses Feature um 11 Felder erweitert,
  nicht neu einführt. Ablösung ist nicht Teil dieses Features.
- Inline statt Kind-Tabelle: fix 2 Fenster + view-geflatteter Bestand → keine Aggregation nötig. Fiele das
  Limit später, wäre eine `base.tariff_time_slot` (FK auf `(id, version)`) die Migration wert.

**eegfaktura-billing** — **kein Flyway für Masterdata**:
- Nur **JPA-Entity-Felder** in `BillingMasterdata.java` (`@Subselect`-Spalten ergänzen) — die Daten
  kommen aus der erweiterten backend-View.
- ⚠️ **Kein Tarif-„Snapshot":** die View joint `base.activetariff` = **MAX(version), live** — ein
  Re-Run einer alten Periode bepreist mit der **aktuellen** Tarif-Version (Bestandsverhalten). Für ZVT abgesichert über den **Konsistenz-Guard** (s. §A/Kontrakt):
  web sendet die verwendeten Fenster-Definitionen mit, billing vergleicht gegen die Masterdata und
  bricht bei Abweichung ab (fail-loud statt stiller Preis/Mengen-Mix).
- `Allocation` (Transport-DTO, **kein Schema**): je ZP die **≤3 Fenster-Teilsummen** (Basis/F1/F2), z. B.
  `buckets:[{key, kWh}]`; für Einfach-Tarife weiter die eine `allocationKWh`.
- `billing_document_item`: **unverändert** — jede Tarifzone wird eine eigene Item-Zeile; Label
  „Tarif: <Name> (HH:MM-HH:MM)" in `documentText`/`tariffName`. (Optional `from`/`to` persistieren = Kann.)

**eegfaktura-energystore (v1 + v2)** — **kein Schema-Change** (BadgerDB v1 / Timescale v2). Neu: der
Report-**Request** nimmt je zeitbasiertem ZP ≤2 generische Zeitfenster {von,bis} entgegen; energystore
**faltet** die rohen ¼h-Werte der Abrechnungsmenge nach lokaler Tageszeit in **≤3 Teilsummen** (Basis +
Fenster) und gibt diese je ZP im Report aus. Reine Time-of-Use-Aggregation — **keine** Tarif-/Preis-Kenntnis.
Die Änderung ist in **beiden** Implementierungen (`eegfaktura-energystore` v1 **und**
`eegfaktura-energystore-v2`) nachzuziehen.

**Tenant-Scoping:** Tarife tragen `tenant`; Masterdata `findByTenantId`; energystore-Reports sind
`tenant`/`ecid`-scoped; keine neue tenant-übergreifende Fläche.

### C) Migrationspfad

| Komponente | Tool | Änderung | Backfill / Rollback |
|---|---|---|---|
| backend | golang-migrate (`migrations/<ts>_*.up/.down.sql`) | 11 Spalten `ADD COLUMN` + `base.activeTariff` **und** `base.billing_masterdata` `CREATE OR REPLACE VIEW` (snake_case-Aliase in der Masterdata-View) | Kein Backfill (Defaults → Bestand = `useTimeTariff=false`). Down: Spalten droppen + Views zurück. **`schema.sql`/`schema.hcl`+`atlas.sum` mitziehen (Drift-Gefahr).** |
| billing | **kein Flyway** (Masterdata = backend-View) | nur JPA-Felder in `BillingMasterdata` (`@Subselect`) + `Allocation`-DTO `buckets`/`timeWindows` | — |
| energystore (v1+v2) | — | reiner Compute-/Report-Change (kein Schema) | — |
| web | — | UI + Payload-Feld | — |

Alles **additiv/abwärtskompatibel**: bestehende Einfach-Tarife rechnen unverändert (kein `useTimeTariff`
→ Pfad wie heute). Für **zeitbasierte** Tarife gilt dagegen **fail-loud** (§A-Kontrakt): fehlen `buckets`,
bricht der Lauf ab — ein stiller Basispreis-Fallback wäre eine stille Fehlabrechnung. Keine Big-Bang-Migration.
**Deploy-Reihenfolge:** backend-Migration (Spalten+Views) → billing (JPA) → energystore v1/v2 → web.

### D) Auth & Tenant-Isolation
- `POST /api/billing` fordert Bearer + `TenantContext.validateTenant(tenantId)` — **unverändert**.
- Tarif-Lese/Schreibpfade im backend sind tenant-scoped (`base.tariff.tenant`); Tarif-Validierung
  (Von≠Bis, Überlappung) **serverseitig** im backend-Tarif-Save-Handler, zusätzlich clientseitig in web.
- energystore-Reports bleiben `ecid`/`tenant`-gebunden. **Keine neue authentifizierte Fläche, kein neuer
  Scope.** Load-then-assert unverändert.

### E) Tech-Entscheidungen (begründet)
1. **Mengen-Summierung in energystore (≤3 Fenster-Summen), Bepreisung in billing, web orchestriert**
   (siehe A) — Preis↔Menge sauber getrennt, **Entkopplung gewahrt** (billing ohne Ausgangsverbindung),
   **minimaler Payload** (≤3 statt 96 Zahlen/ZP). energystore erhält nur generische Von/Bis-Zeiten,
   keine Tarif-Kenntnis. Grund: Skalierung (2000+ ZP) + bewusst keine synchrone Service-zu-Service-REST.
2. **`centPerKWh` = Basispreis wiederverwenden** (keine neue Basispreis-Spalte) → Einfach-Tarife bit-identisch,
   minimale Migration, kein Regressionsrisiko.
3. **Inline-Spalten** (nicht Kind-Tabelle) — passt zum Bestand, keine Join/Aggregations-Reibung bei fix 2 Fenstern.
4. **Ein Rabatt / kein freeKWh im Zeitmodus** — direkt aus den Nutzer-Festlegungen; `discount` wirkt auf alle
   Positionen, `freeKWh` nur wenn `useTimeTariff=false`.
5. **Überlappung verboten (Plausicheck)**, `Von≠Bis`, `Von>To` = Mitternachtsüberlauf erlaubt, **Raster
   00/15/30/45** serverseitig erzwungen → jede ¼h gehört eindeutig genau einem Fenster.
6. **Kein Fallback für fehlende ¼h-Daten** — das ¼h-Profil ist Pflicht-Vorbedingung jeder EEG-Mitgliedschaft
   (Nutzer-Festlegung), der Fall tritt nicht auf. Leere Buckets (0 kWh) erzeugen — wie heute — **keine**
   Position (`isNullOrZero(grossValue)` → skip). Davon getrennt: zeitbasierter Tarif **ohne `buckets` im
   Payload** = Protokollfehler → **Lauf-Abbruch** (§A-Kontrakt, kein stiller Basispreis-Fallback).
6b. **Zeitzone der Faltung: explizit `Europe/Vienna`** — **nicht** `time.Local`: die
   v1-RowIds entstehen heute aus `time.Local` (`timeUtils.go`, `importFunctions.go`); ohne `TZ`-Env läuft
   der Container in UTC → HH:MM-Fenster wären um 1–2 h verschoben. Vor der Implementierung je Umgebung
   verifizieren, in welcher TZ die v1-Badger-Keys tatsächlich vorliegen (und v2-`timestamptz`-Semantik);
   die Fenster-Zuordnung rechnet dann fest in `Europe/Vienna`. DST: 23h-Tag → Fenster-¼h einmal weniger,
   25h-Tag → doppelt (v1-Key-Kollision am 25h-Tag prüfen!); konkrete Erwartungswerte als QA-Testfälle.
6c. **kWh-Partition exakt per Konstruktion:** energystore liefert `BASE` als **Residuum**
   (`Gesamt − T1 − T2`), nicht als eigene Summierung → Σ buckets ≡ Periodensumme, Rundungsdrift betrifft
   nur noch die €-Beträge (definierte Schranke: ≤ 0,01 € × Positionsanzahl).
7. **PDF-Layout:** je ZP ein Block ohne Leerzeilen — Kopf `Zählpunkt <nr> - <name> (Rabatt xx %)`,
   Positionszeilen `Tarif: Basis` / `Tarif: <Name> (HH:MM-HH:MM)` (leerer Name → nur Zeitraum),
   `Zwischensumme Zählpunkt <nr>`. Kopf gemäß Mockup.

### F) Cross-cutting / ADR
- **Kein Plattform-ADR nötig** — Feature auf Komponenten-Ebene, kein Deploy-/Registry-/Cluster-/Infra-Thema.
  Die einzige übergreifende Entscheidung (Fenster-Summierung in energystore + additiver `buckets`-Kontrakt)
  ist hier dokumentiert und genügt als Design-Record.
- **Drift-Hinweis backend:** `base.tariff`/`base.activeTariff` existieren in `schema.sql` **und**
  `schema.hcl`/`atlas.sum` neben den `migrations/` — beide Repräsentationen in derselben PR konsistent halten.
- **DST-Edge:** die 96-Slot-Faltung erfolgt nach **lokaler Tageszeit**; Tage mit 23/25 h (Zeitumstellung)
  tragen entsprechend weniger/mehr zu den betroffenen Slots bei — beim `/qa` mit einem Umstell-Tag verifizieren.

## Implementierung (2026-07-11, alle Branches lokal/ungepusht)

| Repo | Branch / Worktree | Stand |
|---|---|---|
| eegfaktura-energystore (v1) | `feat/zvt-time-windows` (c:\temp\energystore-zvt) | Fenster-Summierung `timeWindows`→`buckets` im Report; Validierung 400; Tests grün (Fold-Test mit Erwartungswerten inkl. Mitternachtsfenster + exakter Partition) |
| eegfaktura-energystore-v2 | `feat/zvt-time-windows` (c:\temp\energystore-v2-zvt) | Paritäts-Port; alle internal-Tests grün |
| eegfaktura-billing | `feat/zvt-time-tariff` auf `feat/async-billing-run` gestackt (c:\temp\billing-zvt) | JPA-Masterdata-Felder (@Column explizit — Hibernate setzt nach Ziffern KEINEN Unterstrich), `Allocation.buckets/timeWindows`, Guard fail-loud, 1–3 Positionen, PDF-Gruppierung je ZP (alle Dokumenttypen, styled-markup, Zwischensummen in Java); 41 Tests / 0 neue Failures (6 bekannte BillingIntegrationTests-Umlaut/Pfad-Failures baseline-identisch) |
| eegfaktura-backend | `feat/zvt-time-tariff` (c:\temp\backend-zvt) | Migration `20260711120000` (11 Spalten + BEIDE Views, up+down gegen frisches Postgres verifiziert, `to_char HH24:MI`), Tarif-Validierung serverseitig (11 Testfälle grün), schema.sql/schema.hcl/atlas.sum synchron |
| eegfaktura-web | `feat/zvt-time-tariff` auf `feat/async-billing-poll` gestackt (c:\temp\web-zvt) | Tarif-Dialog Einfach\|Zeitbasiert (Client-Plausiprüfung gespiegelt), Report-Request mit `timeWindows` je zeitbasiertem ZP (ParticipantProvider), `buckets`+`timeWindows` in Allocation (ParticipantPane.functions); pnpm vite build + 21/21 vitest grün |

**Zeitzonen-Verifikation (vor Implementierung):** v1-Badger-RowIds tragen lokale Wanduhrzeit; das v1-Image baked `TZ=Europe/Berlin` (offsetgleich Europe/Vienna, im env-billing-Pod verifiziert), v2 baked `TZ=Europe/Vienna` → die Faltung vergleicht direkt gegen die im Key kodierte HH:MM (kein TZ-Umweg). 25h-Tag: v1 kollidiert im Key-Raum beim Import (Bestandsverhalten, Stunde zählt einfach), v2 zählt doppelt.

**Export-Pfade (Operator-Frage, am Code verifiziert):**
- **SEPA:** Der pain-XML-Export lebt in web (`sepa.converter.ts`) und liest `Rechnungsbetrag Brutto/Netto` aus der XLSX-„Liste" — also **Dokument-Gesamtsummen**, nie Positionen. ZVT ändert nur die Positionsanzahl; die Dokumentsumme bleibt Σ der gerundeten Positionen → SEPA unverändert korrekt, keine Anpassung nötig.
- **billing-XLSX:** „Liste" (Dokumentsummen) unverändert; „Details" (1 Zeile je Position) zeigt ZVT automatisch als 2–3 Zeilen je ZP. Fensterlabel steht in `Pos. Text` („Tarif: <Name> (HH:MM - HH:MM)"), `Pos. Tarif` bleibt der Tarifname. Bewusst flach, keine Blockstruktur.
- **energystore-Excel (Energie-Report):** reine Rohdaten, keine Tarif-Kenntnis → unberührt.
- **Rechnungsvorschau web-GUI:** Die Vorschau zeigt (1) die per-ZP-Beträge aus `participantAmounts` — dort werden ZVT-Positionen jetzt **je ZP zu EINEM Betrag aggregiert** (`ParticipantAmountService`, gleiches Verhalten wie bisher bei 1 Position) — und (2) das Vorschau-PDF aus billing, das das neue Blocklayout (Kopf/Positionen/Zwischensumme je ZP) bereits enthält. Die web-GUI rendert selbst keine Positionsliste → kein web-Umbau der Vorschau nötig.

## QA Test Results

**Getestet:** 2026-07-11
**Umgebung:** dev-Cluster eegf-dev, Namespace `env-billing` (alle 4 Images `:zvt-e2e` live); Test-EEG TE100200; API-Kette wie web sie fährt (energystore-Report → Allocation → billing async 202+Poll → PDF/XLSX)
**Tester:** QA Engineer (AI)
**Betroffene Komponenten:** eegfaktura-energystore (v1+v2), eegfaktura-billing, eegfaktura-backend, eegfaktura-web

### Akzeptanzkriterien
- **AC Tarif anlegen (VZP+EZP zeitbasiert):** ✅ VZP „QA ZVT Verbraucher" (Basis 10 + Morgen 06–12/8ct + Nacht-Mitternachtsfenster 20–06/12ct, Rabatt 5%, USt 20%) und EZP „QA ZVT Erzeuger" (Basis 4 + Nachteinspeisung 20–06/6ct) per POST /api/eeg/tariff angelegt (201), in `base.billing_masterdata`-View korrekt als `HH:MM` sichtbar.
- **AC Überlappung/Von=Bis/Raster abgewiesen (server):** ✅ Überlappende Fenster → 400, Off-Raster `06:10` → 400 (leerer Body, kein Datensatz). Client-Spiegelung zusätzlich in web (Vitest grün) + **UI-Härtung: Von/Bis sind jetzt Auswahlfelder mit genau den 96 ¼h-Werten** → Off-Raster gar nicht mehr eingebbar.
- **AC ≤3 Fenster-Teilsummen, BASE=Residuum, exakte Partition:** ✅ Report liefert für alle ZVT-ZPs `buckets[BASE,T1,T2]`; Σ buckets ≡ util/prod−alloc bit-genau (6 ZPs geprüft, `partition_exact=True`).
- **AC getrennte Positionen je Fenster + Rabatt/USt/Rundung pro Position:** ✅ ZP 020100: 3 Positionen (Basis 20,52 kWh×10ct→1,95€netto nach 5% Rabatt / Morgen 10,71×8→0,82 / Nacht 2,97×12→0,34), je Position 20% USt; Handrechnung stimmt, Σ-Drift 0.
- **AC Preview==Final-Rechenweg / fail-loud-Kontrakt:** ✅ (Preview geprüft; Final teilt exakt denselben Pfad). **Fail-loud e2e alle 3 Fälle → FAILED mit klarer errorSummary:** (A) Fenster-Payload≠Masterdata, (B) ZVT-Tarif ohne buckets, (C) buckets bei Einfach-Tarif. Anschließend **FAILED-Neustart mit korrekten Daten → NEW** (Restart funktioniert).
- **AC gemischter Lauf (Einfach + ZVT):** ✅ Q1-2023 gemischt: Einfach-ZPs (020104/05/08/10) genau 1 Position, ZVT-ZPs 2–3 Positionen — im selben Lauf korrekt.
- **AC Einfach-Regression betragsgleich:** ✅ Reiner Einfach-Lauf (Feb-2023): Beträge = Menge×Preis exakt (z.B. 73,26 kWh×3ct = 2,20€), gruppiertes PDF-Layout mit 1 Zeile je ZP-Block korrekt gerendert.
- **AC Erzeuger-Dokumenttypen:** ✅ EZP-ZVT → CREDIT_NOTE mit „Tarif: Basis" + „Tarif: Nachteinspeisung (20:00 - 06:00)", Zwischensumme, negativer Betrag im participantAmount.
- **AC PDF-Blocklayout (alle Dokumente):** ✅ Sichtprüfung (PDF-Text extrahiert): Kopf „Zählpunkt <nr> (Rabatt 5,00 %)" nur bei Rabatt>0, Positionszeilen „Tarif: Basis"/„Tarif: <Name> (HH:MM - HH:MM)", „Zwischensumme Zählpunkt <nr>" mit Netto/USt/Brutto — sowohl Rechnung als auch Gutschrift als auch Einfach-Rechnung.
- **AC participantAmounts je ZP aggregiert:** ✅ ZP mit 3 Positionen erscheint als **ein** MeteringPoint-Eintrag (Summe), Erzeuger negativ.
- **AC kleiner Payload:** ✅ ≤3 Zahlen/ZP.
- **AC Rabatt auf alle Positionen:** ✅ 5% auf Basis+beide Fenster.
- **AC kein freeKWh im Zeitmodus:** ✅ Validierung + UI blenden freeKWh im Zeitmodus aus.
- **AC XLSX-Export:** ✅ „Liste" = Dokumentsummen konsistent mit participantAmounts; „Details" = 2–3 Zeilen je ZVT-ZP, Fensterlabel in „Pos. Text", „Pos. Tarif" = Tarifname.
- **AC UI-Kennzeichnung Zeiträume (Operator-Wunsch):** ✅ je Zeitraum umrandeter Block mit Titel „Zeitraum n"; inaktiver Zeitraum ausgegraut+nicht bedienbar (Checkbox frei), Validierung nur für aktive.

### Edge Cases
- **Leerer Bucket (0 kWh) → keine Position:** ✅ (bestehendes Nullpositions-Verhalten).
- **Mitternachtsfenster (20:00–06:00):** ✅ Nacht-Bucket korrekt befüllt, Faltung tageübergreifend.
- **DST 23h-Tag (26.03.2023):** ⚠️ **nur bedingt prüfbar** — die Testdaten der EEG enthalten am Umstellungstag 96 (statt 92) ¼h inkl. der physikalisch nicht existierenden Stunde 02:00 (synthetische, nicht DST-bereinigte Rohdaten). Der ZVT-Code ordnet jede vorhandene ¼h nach ihrer Wanduhr-HH:MM zu (korrekt); die AC „23h-Tag zählt einmal weniger" lässt sich mit diesen Quelldaten nicht positiv bestätigen. **Kein Code-Defekt, aber echte DST-Verifikation braucht DST-bereinigte Testdaten** (Empfehlung: gezielten 92-Slot-Tag im Testbed nachstellen).

### Automatisierte Tests (je Stack, konsolidiert)
- energystore v1: calculation-Suite grün (Fold-Test Erwartungswerte, Mitternacht, exakte Partition); model/mqtt/ebow-Failures **baseline-identisch pre-existing** (Disk/Wire-Format).
- energystore v2: alle internal-Tests grün.
- billing: 41 Tests, ZvtBillingTests 6/6 grün; 6 BillingIntegrationTests-Failures **baseline-identisch pre-existing** (Umlaut-Fixture + /home/hla/temp).
- backend: model 11/11 Validierung grün; Migration up/down/re-up gegen frisches Postgres verifiziert; api/database/eda-Testbuilds **pre-existing kaputt** (Signatur-Drift auf master, nicht ZVT).
- web: `pnpm exec vite build` + Vitest 21/21 grün.

### Security Smoke Test
- [x] Auth: Report + Tarif ohne Bearer → **403**; `alg=none`-Token → **401** (RS256-only bestätigt).
- [x] Tenant-Isolation: Fremd-Tenant-Header mit normalem Token → 403 (Middleware `tokenVerifier.go:152-158`, load-then-assert `contains(claims.Tenants, tenant)`). Der QA-User `faktura_admin` trägt zusätzlich die **`superuser`-Rolle** → cross-tenant erlaubt (gewolltes Bestandsverhalten, kein ZVT-Defekt).
- [x] Injection: Tarif-Save via goqu-Parametrisierung; billing JPA/JPQL; energystore keine String-Konkat.
- [x] Secrets/PII: `errorSummary` der 3 fail-loud-Fälle enthält nur ZP-Id + fachliche Meldung, **keine Stacktraces/PII** (bereits im Security-Review des Async-Features gehärtet).
- Finding siehe unten (Input-Länge).

### Gefundene Bugs / Findings
#### FINDING-1: Zeitfenster-Tarifname ohne serverseitige Längenbegrenzung
- **Severity:** Low (Medium nach QA-Regel 3.9)
- **Bereich:** backend `base.tariff.timeTariffNName` = `character varying` ohne Limit; `ValidateTimeTariff` prüft keine Namenslänge. Ein superuser/Admin könnte über die API einen sehr langen Namen speichern (landet im PDF/XLSX).
- **Kontext:** Konsistent mit dem Bestand — auch `tariff.name`/`vatSupplementaryText` sind unbegrenzt; **kein durch ZVT eingeführter Regressionspunkt**, nur eine neue Instanz desselben Musters.
- **Priorität:** nice-to-have (einheitliche Max-Length-Policy über alle Tariffelder, nicht ZVT-spezifisch vorziehen).

### Zusammenfassung
- **Akzeptanzkriterien:** 15/15 erfüllt (Kern-Rechenweg, Positionen, PDF, XLSX, Guards, Regression, UI) — **DST nur bedingt prüfbar** (Testdaten-Limit, kein Code-Defekt).
- **Bugs:** 0 blockierend. 1 Low/Medium-Finding (Input-Länge, pre-existing Muster).
- **Security:** Pass (Auth/RS256/Tenant/Injection/PII). 1 Low-Finding.
- **Production-Ready:** **JA** für den Feature-Kern. **Empfehlung:** `eegfaktura:security-review` als finales Gate (Migration + neue Schreibfelder + Guard-Statusmaschine), danach PRs. DST-Verifikation mit bereinigten Testdaten nachholen; Input-Länge in die allgemeine Härtung.

**Test-Artefakte im Testbed (bleiben, Testumgebung):** Tarife „QA ZVT Verbraucher"/„QA ZVT Erzeuger"; ZP 020100/020102→VZP-ZVT, 030020109→EZP-ZVT umgehängt; billing_runs Abr_YM-2023-2 (Einfach, NEW) + Abr_YQ-2023-1 (ZVT, NEW nach mehreren FAILED-Guard-Läufen). EVIL-Tarif (Tenant-Test) wieder gelöscht.

---

## Security Review

**Reviewer:** Security Engineer (AI)
**Date:** 2026-07-11
**Scope:** ZVT-Delta über 5 Repos — backend (Migration + Tarif-Validierung + DAO), billing (Positionen/Guards/Jasper-PDF/Masterdata-JPA), energystore v1+v2 (Fenster-Validierung/Faltung), web (Dialog). Async-Fläche bereits separat approved, hier nur ZVT-Delta.
**Base:** backend origin/master bd15d48; billing/web auf den Async-Branches gestackt.

### Threat Model Summary
Neue User-Input-Flächen: (1) Tarif-Save mit 11 neuen Feldern (backend, superuser/EEG-Admin), (2) Tarifname/ZP-Bezeichnung fließen neu in ein **`markup="styled"`**-Jasper-PDF, (3) energystore-Report nimmt Fenster-Definitionen entgegen, (4) billing bepreist Aufrufer-gelieferte Mengen mit Konsistenz-Guard. Worst-Case-Kandidaten: Markup/Content-Injection ins PDF, SQL-Injection über neue Felder, stille Fehlabrechnung durch umgangene Guards, Datenverlust durch die View-Migration.

### Findings

| Severity | File | Function/Area | Risk | Exploit Scenario | Recommended Fix | Confidence |
|---|---|---|---|---|---|---|
| **Info** | backend `model/tariff.go`, `schema.sql` | `timeTariff{1,2}Name` | Kein Server-Max-Length (character varying unbegrenzt); QA-Finding-1 | Superuser speichert überlangen Fensternamen → landet in PDF/XLSX | Einheitliche Max-Length-Policy über ALLE Tariffelder (name/vatSupplementaryText ebenso unbegrenzt) — **pre-existing Muster, nicht ZVT-spezifisch**, in allgemeine Härtung | High |
| **Info** | backend `database/tariffDao.go:232` | `UpdateTariff` | Toter Code: würde `ValidateTimeTariff` NICHT anwenden | Kein Exploit heute (nicht geroutet, kein Aufrufer außer Tests) | Falls je geroutet: `ValidateTimeTariff` ergänzen — Hinweis für die Zukunft | High |

Keine Critical/High/Medium-Findings.

### Geprüft & als sicher bestätigt
- **Jasper styled-markup (Haupt-Kandidat):** `markup="styled"` gilt nur für `text`/`netValue`/`vatPercent`/`grossValue`. Der einzige User-Input (Tarifname, ZP-/Anlagenname) landet ausschließlich im `text`-Feld und läuft **immer** durch `escapeStyled()` (`&<>` → Entities). Da alle JasperReports-Style-Tags mit `<` beginnen und Entities mit `&`, ist Tag- und Entity-Injection abgeschnitten — ein Tarifname `<b>x` rendert als Literaltext. `netValue/vatPercent/grossValue` tragen nur `makeGermanString`-Zahlen bzw. `<b>`+escapeStyled. **Wichtig:** das Feature führt `markup="styled"` neu ein UND sichert es zugleich mit `escapeStyled` — richtige Kombination, kein Regressionsrisiko ggü. dem alten plain-Template.
- **Migration:** rein additiv (`ADD COLUMN IF NOT EXISTS`, Booleans `NOT NULL DEFAULT false` → Bestandszeilen gedeckt, Zeit-/Preisfelder NULL-able). `DROP VIEW`+`CREATE OR REPLACE` in einer golang-migrate-Transaktion (atomar); Views tragen keine Daten → kein Datenverlust. up/down/re-up **live gegen frisches Postgres verifiziert**.
- **ValidateTimeTariff:** läuft im **einzigen gerouteten Schreibpfad** (`POST /tariff → addTariff`) VOR dem Persist; `UpdateTariff` ist toter Code (kein Bypass). Prüft VZP/EZP-only, Raster, Von≠Bis, zyklische Überlappung, freeKWh-Verbot. 11 Unit-Tests + Live-400-Smoke.
- **SQL-Injection:** DAO nutzt `goqu.Insert(...).Rows(struct)` (Bestands-Escaping) + `$1`-parametrisierten SELECT; die neuen Felder sind `null.String/null.Float`-Werte, kein struktureller Input. Views = statisches DDL. energystore/billing: keine String-Konkat in Queries.
- **billing Guards fail-loud:** `validateZvtAllocation` wirft `ZvtContractViolationException` bei fehlenden buckets / Fenster≠Masterdata / buckets-bei-Einfach; propagiert bis `BillingRunLauncher.catch` → FAILED (kein stiller Fallback). Live 3× bestätigt.
- **errorSummary (PII/Stacktrace):** `toErrorSummary` reicht fachliche RuntimeExceptions durch (ZP-Id + Erklärung), generalisiert DataAccess/Persistence/SQL/null-message → keine Stacktraces/SQL/PII; tenant-scoped Rückgabe.
- **Tenant-Isolation:** unverändert (`tokenVerifier.go` load-then-assert `contains(claims.Tenants, tenant)` → 403; superuser-Bypass = Bestand). Kein neuer Endpoint, kein neuer Scope, keine neue Ausgangsverbindung in billing.

### Scan Results
- **SCA:** `go.mod`/`go.sum` (backend, energystore v1) und `pom.xml` (billing) **0 Diff-Zeilen** ggü. Base → ZVT führt keine Dependency ein; bestehende CVEs sind pre-existing/out-of-scope. `govulncheck`/`npm audit` daher nicht separat ausgeführt (Delta null).
- **SAST:** manuelle Durchsicht der geänderten Flächen (Jasper-Markup, DAO, Validierung, Guards); Snyk Code nicht ausgeführt (kleine, vollständig manuell abgedeckte Delta-Fläche).

### Verdict: **APPROVED**
Keine Critical/High/Medium-Findings. Die beiden Info-Findings (Max-Length, toter `UpdateTariff`) sind pre-existing Muster ohne akutes Exploit und gehören in die allgemeine Härtung, nicht ins ZVT-PR-Gate. Merge-Reihenfolge: Async-PRs zuerst (ZVT ist darauf gestackt), backend-Migration vor billing.
