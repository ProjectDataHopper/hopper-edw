# Target type mapping metadata

Hop folder: `metadata/target-type-mapping/<name>.json`

Project-level Hop type → native SQL type preferences for generated DDL (issue #127). Inverse of data type mapping (source → Hop).

## Fields

| Field | Role |
|-------|------|
| `name` | Unique metadata name |
| `description` | Optional |
| `targetDatabase` | Optional Hop `rdbms` connection name (auto-match) |
| `rules[]` | Ordered first-match rules |

### Rule

| Field | Role |
|-------|------|
| `enabled` | default true |
| `matchHopType` | `String`, `Integer`, `Timestamp`, … |
| `matchMinLength` / `matchMaxLength` | Inclusive; variables allowed |
| `matchMinPrecision` / `matchMaxPrecision` | Inclusive |
| `matchLengthAbsent` | boolean |
| `matchFieldNamePattern` | glob / regex |
| `targetSqlType` | Native template: `${VAR}`, `{length}`, `{precision}` |

Consumers: Record Definition DDL `target_type_mapping`, Update resource definition group / DV / BV / DM Update `targetTypeMapping`. Empty consumer + unique `targetDatabase` match → auto-apply.
