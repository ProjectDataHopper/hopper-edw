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

# Generate Data Vault from a source model

Review the hubs, links, satellites, reference tables, and hub aliases suggested from selected source-model tables, queries, JSON extractions, and pipelines, then apply them to a new or existing `.hdv` file.

## How sources are classified

- Tables (or composed feeds) that share the same primary key become **one hub** plus **one satellite per source**.
- A table whose primary key is made of two or more foreign keys becomes a **link** (leftover key parts become dependent child keys). Extra columns become a **link satellite**.
- Other tables with their own primary key become a **hub** (and optional satellite). Leftover foreign keys become binary **links**.
- Small lookup / code tables can become **reference tables** (`ref_*`) instead of hub plus satellite.
- A self-referencing foreign key becomes a **hub alias** plus a **hierarchy link**.
- Query, JSON, and pipeline cards use the same grain rules. A feed with leftover keys to two or more hubs can become one **n-ary** transactional link.
- Isolated tables with no table-to-table relationships are skipped. JSON or pipeline children of those tables can still generate.

Relationship direction is inferred from primary-key and foreign-key columns, so inverted child/parent labels still classify correctly.

## Tips

- Uncheck a row to skip that object. Edit **Vault name** before Apply if you want a different name.
- This builds a **raw** Data Vault starter. Refine business keys and Business Vault integration afterwards.
- Use **Check model** after apply. Generated loads still need catalog feeds (enable **Publish unpublished tables** when a catalog connection is set).
- From a Data Vault canvas you can start the same review from the toolbar, the canvas menu, or the coach panel **Generate DV** button.
