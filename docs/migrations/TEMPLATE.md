# V1_<N> — <title>

<!-- Copy to docs/migrations/V1_<N>-<slug>.md. Answer every heading (AGENTS.md section 8.1). -->

## 1. What changes

The tables, columns, constraints and indexes in schema `billingj` the migration creates or
changes, and why. Say whether `base.billing_masterdata` is read differently.

## 2. Data migration

What happens to existing rows (backfill, conversion, nothing), how long it takes on production
size, and whether it locks anything during a billing run.

## 3. Compatibility

Does the previous application version still run against the migrated schema? What must be
deployed together (backend view, `eegfaktura-web`, eegfaktura-v3)?

## 4. Rollback

The statements, run by hand; Flyway Community has no undo. Then:

```sql
DELETE FROM billingj.flyway_schema_history WHERE version = '1.<N>';
```

Name the data that cannot be restored (documents, numbers already issued).

## 5. Verification

The tests that prove it (class names) and the queries the operator runs after applying it.
