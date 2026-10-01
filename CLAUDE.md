# CLAUDE.md

The working agreement for this repository lives in [AGENTS.md](AGENTS.md).
Read it in full before changing anything. It covers standards, tenant isolation, mandatory tests,
the migration concept, logging, external sources, size limits and the tracking files you must
update (`AGENT_LOG.md`, `known-errors.md`, `open-points.md`, `EXTERNAL_SOURCES.md`).

## Strict rules

- **Tenant isolation:** every endpoint compares the record's tenant with the `Tenant` header, and
  the header must be in the token's `tenant` claim (AGENTS.md section 6).
- **Dependencies:** every version fixed exactly, no release younger than **7 days** — Maven
  dependencies, plugins, the JDK, Docker images and CI actions alike (AGENTS.md section 12.1).
- **Build with JDK 21** (AGENTS.md section 9).
