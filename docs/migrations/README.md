# Migration concepts

One file per Flyway migration (or group of migrations) that touches persisted data, named after
the version (`V1_<N>-<slug>.md`, a group `V1_<N>-V1_<M>-<slug>.md`). Mandatory content: AGENTS.md
section 8.1 and [TEMPLATE.md](TEMPLATE.md) — what changes, data migration, compatibility,
rollback (run by hand, Flyway Community has no undo), verification.

The rule starts on 2026-10-01. The migrations before it, `V1_0` … `V1_14`, have no concept
(`known-errors.md` #11, `open-points.md` B-7).
