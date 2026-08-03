<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at
    
         http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

# Reference table editor

A **Reference table** holds code / catalog / lookup data in the vault database using **natural keys** (no hub hash key, no satellite hashdiff). Typical names look like `ref_country` or `ref_ami_land`.

This is **not** a cross-model **Table reference** (pointer or hub alias).

## When to use Reference vs Hub + Satellite

| Use **Reference** when… | Use **Hub + Satellite** when… |
|--------------------------|-------------------------------|
| Codes, labels, static catalogs | First-class business entities |
| Single system of record is enough | Multi-source identity integration |
| Full replace or delete-insert fits ops | Insert-only audit + hashdiff CDC |
| Physical table has no hash key | You need hash keys and sat history |

## Options tab

- **Integration mode** — Hop managed, external read-only, or custom pipelines (same as hubs).
- **Physical table name** — Target table; defaults to the canvas name.
- **Load mode** — Full replace (truncate + insert) or delete-insert (refresh keys present in the delta). Merge is reserved for a later release.

## Record sources tab

List the catalog **DV_SOURCE** feeds that load this reference table (one or more).

## Natural keys tab

Ordered natural key columns that define the grain (e.g. `code`, or `code` + CDC timestamp for multi-version rows). Map source field names per record source like hub business keys.

Use **Import keys from sources** to pull primary-key fields (or pick columns) from feeds not yet mapped.

## Attributes tab

Descriptive columns stored on the reference table (labels, flags, source audit fields). **Include in CDC** is reserved for later compare-before-rewrite logic; v1 loads rewrite rows for keys in the feed regardless.

## Load behaviour (overview)

- **Full replace** — Truncate (or delete all) then insert the full feed.
- **Delete-insert** — For each natural key in the delta, delete existing target rows then insert the delta rows. Keys not in the delta stay unchanged.
  - **Same database as the vault target:** Hop generates `DELETE … WHERE EXISTS (… source …)` then an insert pipeline (no truncate).
  - **File / other DB sources:** Falls back to full replace for that source (Check model warns). Staging-table portable path is not implemented yet.
- **Merge** — Not implemented yet.
