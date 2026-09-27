# Issue 183 — Identity, cutover, and survivorship

**Issue:** [#183](https://github.com/ProjectDataHopper/hopper-edw/issues/183)

Three contracts, three homes, wired only by name. A blank pointer keeps today's `SortedSchemaMerge` pipeline.

| Concern | Where | Grain |
|---------|--------|--------|
| Identity | `BvIdentityMap` canvas object. Physical table, loaded before SCD2. | Shared by concept. SCD2 field `identityMapName`. |
| Cutover | `BvSourceCalendar` canvas object. Metadata only, not a warehouse table. | Shared dates. SCD2 field `sourceCalendarName`. Each satellite config and source-query ref stores `sourceId`. |
| Survivorship | Extra columns on `BvScd2FieldMapping`: rank, null policy. | Per SCD2 table. Empty ranks keep `SortedSchemaMerge`. |

Different SCD2 tables on one `.hbv` can disagree. Two person tables can share `map_person` and `person_sources` and still rank different fields. A product SCD2 names neither.

Do not put the rules in `BusinessVaultConfiguration`. Do not nest one profile inside a single SCD2 table. Do not add a file extension. Do not dedupe raw hubs.

## Phases

1. **Source calendar and leg filter** (done). Windows compiled into existing `TableInput` SQL. Half-open bounds. Priority, mode, operation, and null policy are stored and checked, not applied. `customer-360` without a calendar is unchanged.
2. **Identity map** (done). Generated upsert of `hk_raw → hk_durable` (freeze the first assignment; do not hash today's master key). `IdentityLookup` as-of transform. Partitions use `hk_durable`. Dependent SCD2 tables full-rebuild.
3. **`SurvivorshipMerge`** (done) in this plugin, beside `SortedSchemaMerge`. Ranked field mappings replace the merge for that table only.
4. **Present-flags** (done) for delta feeds, when a real CRUD source must tell "cleared" from "absent".
5. **Incremental SCD2** (done) when the map is stable. A changed `rule_version` replays that raw key from the earlier of `valid_from` and the watermark when the satellite connection is the Business Vault database. Otherwise a map change still needs a full rebuild. Survivorship stays a full rebuild.

`preferred_bk` updates on the map and does not open an SCD2 version. `SurvivorshipMerge` stays in hopper-edw.

## Postgres proof

`integration-tests/tests/bv-resolution/` is the warehouse check. It is wired into `tests/run-tests.hwf` on the Postgres path only. `customer-360` stays unranked.

Two load dates straddle the calendar: `2024-02-15` is Oracle, `2024-03-15` is Kafka. `O100` and `K100` are the same person. On the open row, Kafka email and city (rank 1) replace the Oracle values. `O200` has no Kafka row, so the Oracle email and city stay. `K300` is Kafka only. Values that fall outside a window (`kafka-before`, `oracle-after`, Liege, Antwerp) are absent. The second load flips the same-as master to `K100`. `hk_durable` stays the value snapshotted after the first load, and `rule_version` becomes 2. Survivorship still forces a full SCD2 rebuild, so this fixture does not exercise the incremental remap predicate.
