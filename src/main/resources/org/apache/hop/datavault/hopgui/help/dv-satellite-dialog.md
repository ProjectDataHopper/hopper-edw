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

# Satellite editor

A **Satellite** stores descriptive attributes that change over time for a **Hub** or a **Link**. Classic examples are customer name and address, product descriptions, order status history, or relationship-level quantities and terms on a link satellite.

Satellites are **insert-only**: when tracked attribute values change for a parent key, the load pipeline inserts a new row with the new values and the current load timestamp. Previous rows are kept, giving you a complete history.

## How satellite updates work

For each load, the generated pipeline:

1. Reads parent business keys (to compute the parent hash key), satellite attributes, and (when **Store record source indicator** is enabled) the record source indicator from your catalog feed.
2. Calculates the parent hash key (from the parent hub, or from the parent link's participating hubs).
3. Compares incoming attribute values against existing satellite rows for that parent (and driving key, when used).
4. Inserts only rows that are new or that contain **changed** attribute values.
5. Adds the batch load date to every inserted row.

Because change detection compares actual attribute values, you do not get duplicate "no-change" rows. The attribute list in this dialog is the same list used for DDL, loading, and change detection.

## Dialog layout

The editor opens from a satellite box on the Data Vault model canvas (double-click or context menu → **Edit**).

| Area | Contents |
|------|----------|
| **Header** | Name and description (apply to the satellite definition everywhere in the model) |
| **General tab** | Integration mode, physical table, default record source, store record source indicator, parent hub or link, driving keys |
| **Attributes tab** | Columns to historize and the **Load from source** action |
| **Status tracking tab** | Optional Status Tracking Satellite (STS) for full-snapshot deletion detection |
| **Footer** | **OK**, **Validate**, **Cancel**, and **Help** |

Use **Validate** to run model checks against your current edits without closing the dialog. Use **Check model** on the graph toolbar for a full-model validation pass.

## Three meanings of "record source"

As with hubs, "record source" appears in more than one place:

| Concept | Where you set it | Example |
|---------|------------------|---------|
| **Catalog feed** | **Default record source** on the General tab | `CRM-customer` (a DV_SOURCE in the Data Catalog) |
| **Value in the vault row** | Catalog source → source indicator or indicator field | `CRM` or a column from the feed |
| **Target column name** | Data Vault model configuration (global default); only written when **Store record source indicator** is on | `RECORD_SOURCE` |

The **Default record source** dropdown lists **catalog DV_SOURCE entries** (logical feeds), not individual source columns. Configure the value written to each loaded row on the catalog source definition when the satellite stores the indicator column.

A default record source is **required** for Hop-managed satellites, even when the satellite reads the **same physical table** as its parent hub.

## General tab

| Field | Description |
|-------|-------------|
| **Integration mode** | **Hop managed** (default) — Hop generates DDL and update pipelines. **External read-only** — the table is loaded elsewhere (e.g. dbt); Hop documents structure for Business Vault and dimensional publishing only. **Custom pipelines** — Hop runs your own `.hpl` files from `customUpdatePipelinePaths` instead of generating pipelines. |
| **Physical table name** | Target table in the vault database. Leave blank to default to the satellite **Name**. |
| **Default record source** | Catalog **DV_SOURCE** that feeds this satellite. Required for generated pipelines. Changing this refreshes the **Driving key source field** dropdown. |
| **Store record source indicator** | When checked (default), the satellite table includes the model record-source column and loads write the feed's source indicator into it. Uncheck for **VaultSpeed-style** mono-source satellites that omit the source-indicator column from the physical table. The catalog feed is still required. Hubs and links always store record source. |
| **Parent hub** | Dropdown of hubs defined in this model. Select the hub this satellite describes (normal hub satellites). Choose **either** a parent hub **or** a parent link, not both. If the current value is not in the model (e.g. after an external reference), it is still listed and pre-selected. |
| **Parent link** | Dropdown of links defined in this model. Select the link for **link satellites** that historize relationship-level attributes. Leave **Parent hub** empty when using a link satellite. |
| **Driving key column** | Target column name that, together with the parent hash key and load date, makes each row unique. Leave empty for a standard single-active satellite. Set both driving key fields for **multi-active** satellites (e.g. multiple current phone numbers per customer). |
| **Driving key source field** | Source column in the default record source that supplies the driving key value. Renamed to the driving key column in the update pipeline and stored in the target table. Populated from fields declared on the selected catalog source. |

### Hub satellites vs link satellites

| Satellite type | Parent | Typical use |
|----------------|--------|-------------|
| **Hub satellite** | **Parent hub** set, **Parent link** empty | Descriptive attributes of a core business entity (customer name, product category, …) |
| **Link satellite** | **Parent link** set, **Parent hub** empty | Attributes that belong to the **relationship** (quantity, discount, contract status on an order line link) |

For link satellites, configure source-to-attribute mappings on the link editor's **Satellite sources** tab after setting **Parent link** here.

## Attributes tab

The attributes table defines which descriptive columns are historized. Each row has:

| Column | Description |
|--------|-------------|
| **Name** | Physical column name in the satellite table (and default source field name when mappings are 1:1). |
| **Description** | Business meaning of the attribute. |
| **Data type** | Target type for DDL and loading (String, Integer, Date, Timestamp, Decimal, …). |
| **Length** | Maximum length where applicable. |
| **Precision** | Decimal precision for numeric types. |
| **Include in CDC** | When **Y**, changes to this column can trigger a new satellite row. Set **N** for audit columns, technical fields, or columns you do not want to version (e.g. source `last_updated` timestamps). |

### Empty attribute list

If you leave the attributes table **empty**, the plugin **auto-includes** all non-key fields from the default record source at pipeline generation time. For hub satellites this excludes:

- Business key source fields from the parent hub
- The driving key source field and driving key column (when multi-active mode is configured)

**Check model** reports this as an informational comment so you can confirm the behaviour is intentional.

### Load from source

**Load from source** on the Attributes tab appends rows from the **default record source** catalog definition:

1. Select a **Default record source** on the General tab first.
2. Click **Load from source**.
3. Review the added rows; remove columns you do not want to historize, adjust types/lengths, and set **Include in CDC** as needed.

For hub satellites, fields that match the parent hub's business key mappings (and the driving key source field, when set) are skipped automatically. The button does not remove existing attribute rows — it only appends new ones.

## Status tracking tab

Enable a separate **Status Tracking Satellite (STS)** when your feed is a **full snapshot** and you need to detect keys that disappeared from the latest extract (logical deletes).

| Field | Description |
|-------|-------------|
| **Enable status tracking satellite** | Turns on STS pipeline and table generation alongside the main satellite load. |
| **Status table name** | Physical STS table. Defaults to `sts_` + parent hub or link name when empty (e.g. `sts_hub_customer`). |
| **Status field name** | Column storing active/deleted status per load. Default: `record_status`. |
| **Active status value** | Value written for keys **present** in the current snapshot. Default: `ACTIVE`. |
| **Deleted status value** | Value written for keys **missing** from the current snapshot. Default: `DELETED`. |

STS requirements enforced by **Check model**:

- Default record source must be set.
- The catalog source must be configured as a **full snapshot** feed (`FULL_SNAPSHOT`), not changes-only.
- Active and deleted status values must both be set and must differ.
- A resolvable status table name and status field name are required.

Detail fields on this tab are disabled until **Enable status tracking satellite** is checked.

## Target table layout

Hop-managed DDL and pipelines typically produce a satellite table with:

1. **Parent hash key** — column name taken from the parent hub's hash key field (or from the parent link's link hash key for link satellites).
2. **Driving key column** — when multi-active mode is configured.
3. **Attribute columns** — in the order listed on the Attributes tab (or all qualifying source fields when the list is empty).
4. **Record source column** — name from Data Vault model configuration (or per-parent overrides where applicable).
5. **Load date column** — from model configuration.

When status tracking is enabled, a separate STS table is created with the parent hash key, status field, record source, and load date.

## Multi-source hubs

When a hub is fed by **multiple catalog sources**, the recommended pattern is **one satellite per source**, each with:

- The same **Parent hub**
- Its own **Default record source** (the feed it loads from)
- Its own attribute list (often identical across satellites)

This keeps record source values and change history cleanly separated per origin system while still describing one business concept.

**Check model** warns when a hub satellite's default record source is **not** listed on the parent hub's **Record sources** tab — add the feed there, or use a dedicated satellite per source as above.

## Checks that involve satellites

**Validate** (in this dialog) and **Check model** (on the graph) report problems such as:

| Check | Severity | Meaning |
|-------|----------|---------|
| No default record source | Error | Required for Hop-managed pipeline generation |
| Not linked to hub or link | Error | Set **Parent hub** or **Parent link** |
| Parent hub/link missing from model | Error | Referenced parent does not exist |
| No attributes defined | Comment | All qualifying non-key source fields will be auto-included |
| Attribute with no name | Error | Every attribute row needs a name |
| Attribute not in record source | Error | Named attribute is not declared on the catalog source |
| Driving key only partly set | — | Both **Driving key column** and **Driving key source field** are required together for multi-active mode |
| Record source not on parent hub | Warning | Hub satellite feed not listed on the hub's Record sources tab |
| Source-to-target type mismatch | Error / warning | Mapped source column incompatible with attribute definition |
| STS on non-full-snapshot feed | Error | Status tracking requires a full snapshot catalog source |
| STS misconfiguration | Error | Missing table name, status field, or distinct active/deleted values |

Type validation compares catalog source columns to satellite attribute definitions (and to auto-included fields when the attribute list is empty).

## Worked examples

### Hub satellite: customer details

`sat_customer_crm` describes `hub_customer` from catalog feed `CRM-customer`:

| Setting | Value |
|---------|-------|
| Parent hub | `hub_customer` |
| Parent link | *(empty)* |
| Default record source | `CRM-customer` |
| Attributes | `first_name`, `last_name`, `email`, … (via **Load from source**, then trimmed) |

Ensure `CRM-customer` appears on `hub_customer`'s **Record sources** tab.

### Multi-active hub satellite: phone numbers

Multiple active phone numbers per customer at the same time:

| Setting | Value |
|---------|-------|
| Parent hub | `hub_customer` |
| Driving key column | `phone_type` |
| Driving key source field | `phone_type` *(from the CRM feed)* |
| Attributes | `phone_number`, `is_primary`, … |

Each distinct `(parent hash key, phone_type)` pair can have its own current history chain.

### Link satellite: order line terms

`sat_lnk_order_line` describes relationship attributes on `lnk_order_line`:

| Setting | Value |
|---------|-------|
| Parent link | `lnk_order_line` |
| Parent hub | *(empty)* |
| Default record source | `E2E-order-line` |
| Attributes | `quantity`, `unit_price`, `discount_pct` |

List `sat_lnk_order_line` under **Participating link satellites** on the link editor, then map source columns on the link's **Satellite sources** tab.

### Full snapshot with status tracking

A nightly full customer extract where removed customers must be flagged:

| Setting | Value |
|---------|-------|
| Default record source | `CRM-customer-snapshot` *(catalog source delivery type: full snapshot)* |
| Enable status tracking satellite | checked |
| Status table name | *(empty → defaults to `sts_hub_customer`)* |
| Active / deleted values | `ACTIVE` / `DELETED` |

## Tips

- Define catalog **DV_SOURCE** feeds in the Data Catalog perspective before selecting a default record source.
- Use **Load from source**, then review the list. Remove large free-text or technical columns you do not need in history.
- Use **Include in CDC = N** for source audit columns that would otherwise create unnecessary new satellite versions.
- For link descriptive data, create a link satellite (**Parent link** set) rather than overloading a hub satellite.
- Run **Validate** before **OK** when you change parent, record source, or attributes — especially after renaming hubs or links in the model.
- When the parent hub has several feeds, prefer **one satellite per feed** over one satellite trying to load all sources.