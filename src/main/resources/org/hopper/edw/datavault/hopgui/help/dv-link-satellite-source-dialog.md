<!--
Copyright 2026 i-Bridge bv

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Link satellite source

A **link satellite source** connects one catalog **DV_SOURCE** feed to the **link satellites** attached to a link. It answers: *from this source, which columns supply the descriptive attributes (and driving keys) for each link satellite?*

Link hub sources load **who is related to whom**. Link satellite sources load **what we know about that relationship** over time.

## When you need this dialog

Use it when a link has descriptive attributes historized on a **link satellite**:

1. Create a satellite with **Parent link** set (not parent hub).
2. List that satellite under **Participating link satellites** on the link Options tab.
3. On the link **Satellite sources** tab, add a catalog feed and click **Edit satellite source mappings…**

If the link only stores keys with no changing attributes, you can skip satellite sources entirely.

## Data Vault Source

Select the catalog **DV_SOURCE** that carries link-satellite attributes. Often this is the **same feed** as the link hub source (e.g. an order-line table with keys plus quantity and price). It can also be a separate enrichment feed.

Configure the physical layout and record source indicator in the Data Catalog before mapping.

## Link satellite attribute mappings

For each **participating link satellite** on the link Options tab, add a satellite mapping row and click **Edit mappings**.

### Attribute mappings

Map each satellite attribute to a source column:

| Satellite attribute field | Source field name |
|---------------------------|-------------------|
| Attribute on the link satellite (e.g. `quantity`) | Column in this source (e.g. `qty_ordered`) |

Rules:

- Map attributes you want historized. Leave the satellite Attributes tab empty to auto-include non-key fields from the feed when using **Load from source** on the satellite editor.
- Mappings are **per source** — different feeds can use different column names for the same attribute.
- **Include in CDC** on the satellite controls whether attribute changes create new satellite rows.

### Driving key mappings (multi-active link satellites)

When a link satellite is **multi-active** (several current rows per link key), define a **driving key** on the satellite's General tab. Then map it here:

| Driving key | Source field name |
|-------------|-------------------|
| Driving key on the satellite (e.g. `line_number`) | Source column (e.g. `line_number`) |

Driving keys let one order–product link carry multiple order lines as separate satellite timelines.

## Hub sources vs satellite sources

| Concern | Configured on | Loads into |
|---------|---------------|------------|
| Hub business keys from relationship row | **Hub sources** | Link table |
| Descriptive attributes on the relationship | **Satellite sources** | Link satellite table |

Both can reference the same catalog feed and the same physical table. Configure **hub key mappings** on the hub source dialog and **attribute mappings** on this dialog.

Typical bridge-table pattern (`order_line`):

| Mapping type | Examples |
|--------------|----------|
| Hub source | `order_id` → hub_order, `product_id` → hub_product |
| Satellite source | `quantity`, `unit_price`, `discount_pct` → satellite attributes; `line_number` → driving key |

Load order in a vault update: hubs first, then links, then link satellites (orchestrated by the Data Vault Update action).

## Step-by-step workflow

1. Create link satellite with **Parent link** set to your link.
2. Define attributes (or plan to load from source) on the satellite **Attributes** tab.
3. On the link **Options** tab, add the satellite under **Participating link satellites**.
4. On **Satellite sources**, add a row and select the catalog **DV_SOURCE**.
5. Click **Edit satellite source mappings…**
6. **Add satellite mapping** for each link satellite fed by this source.
7. **Edit mappings** — map each attribute (and driving key if multi-active) to source columns.
8. Set the satellite **Default record source** to the same catalog feed name.

Repeat for additional feeds when different attributes come from different systems.

## Validation

**Check model** verifies:

- Mapped source columns exist in the catalog field list
- Attribute fields exist on the referenced link satellite
- Driving keys referenced in mappings exist on the link or link satellite as configured

## Tips

- Link satellites require a **default record source** on the satellite editor even when keys and attributes come from the same table.
- Use one link satellite per catalog feed when feeds disagree on attribute columns (same pattern as multi-source hub satellites).
- Check **Has descriptive attributes** on the link Options tab as documentation when you attach link satellites.
- Run **Check model** after mapping to catch missing catalog fields before the first load.