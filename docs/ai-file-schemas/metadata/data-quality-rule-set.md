# `metadata/data-quality-rule-set` — Data quality rule set

**Java:** `org.apache.hop.quality.metadata.DataQualityRuleSetMeta`  
**Schema:** [data-quality-rule-set.schema.json](data-quality-rule-set.schema.json)  
**Example:** `retail-example/metadata/data-quality-rule-set/retail-source-quality.json`

## Purpose

Named set of **data quality rules** used by measure/gate workflow actions (NOT_NULL, ALLOWED_VALUES, MIN_ROW_COUNT, regex, SQL assertion, …).

## Structure

```json
{
  "name": "retail-source-quality",
  "description": "…",
  "rule": [ { "id": "…", "type": "NOT_NULL", "severity": "BLOCKING", "fieldName": "customer_id", "enabled": true, "parameters": {} } ]
}
```

Note: Hop serializes the list under key **`rule`** (singular group items).

## Anti-patterns

- Field names that do not exist on the target dataset/stream.  
- BLOCKING rules for optional exploratory columns without product intent.  
