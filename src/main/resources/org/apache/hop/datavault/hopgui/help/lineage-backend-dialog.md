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

# Lineage backend

A **lineage backend** is the server or folder a Hop Lineage View (`.hlv`) queries. It is **not** the lineage graph itself.

## Types

- **Marquez** — `GET /api/v1/lineage` on a Marquez 0.50 API. Set the **base URL** to the host (`http://localhost:5001` or `${MARQUEZ_BASE_URL}`). If you paste `${MARQUEZ_API}` (`…/api/v1/lineage`), the suffix is stripped.
- **Export folder** — folder of RunEvent JSON written by **Export data lineage**.
- **Local models** — walk current DV/BV/DM collectors. Set **job / dataset namespace** to the same values as the export action (`${MARQUEZ_NAMESPACE_JOB}` / `${MARQUEZ_NAMESPACE_DATASET}`) so seeds match Marquez.

API keys stay on this metadata object, not in `.hlv` files.

Use **Test connection** to verify namespaces, the folder, or the resource definition group.
