# Changelog

Alle nennenswerten Änderungen an **eegfaktura-billing (Java Rechnungs-/Abrechnungs-Service)** werden hier dokumentiert.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
die Versionierung an den Deployment-Release-Tags. Detail-Diffs bleiben im `git log`;
dieser Changelog hebt die für Überblick und Betrieb relevanten Änderungen hervor.

## [Unreleased]

## [1.0.0] – 2026-06-28

Teil des einheitlichen Source-Build-Cutovers der eegfaktura-Suite.

### Changed
- CI: Push in den Development-Tier der Registry (ADR-0005). (#20)
- Multi-Stage-Docker-Build mit Version-Glob im Runtime-Stage. (#18)
- README mit Service-Überblick und Tech-Stack ergänzt. (#21)

## Frühere Releases

Vor dem 1.0.0-Cutover wurde billing eigenständig versioniert (`v0.1.9`–`v0.1.23`,
2024-04 bis 2025-06). Vollständige Liste: [GitHub Releases](https://github.com/eegfaktura/eegfaktura-billing/releases).
Auswahl:

- **v0.1.23** (2025-06-28): JRXML-Template zeigt issuer/bank creditorId; u. a.
  creditorId, SEPA-Lastschrift, Tarif-ID/Version in Rechnungen/XLSX-Export.
- **v0.1.22** (2025-04-16): zusätzliche Mail-Header (Reply-To, Return-Path).
- **v0.1.21** (2025-04-14) … **v0.1.9** (2024-04-04): siehe Releases.
