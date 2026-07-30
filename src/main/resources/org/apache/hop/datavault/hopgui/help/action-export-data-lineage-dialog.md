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

# Export data lineage

Exports **model-derived** OpenLineage events for every Data Vault, Business Vault, and dimensional table in a **resource definition group**.

Events include table-level inputs/outputs and, when enabled, **column-level lineage** facets. Destinations:

- **FILE** — one JSON `RunEvent` per target table under the output folder, plus `export-summary.json`
- **HTTP** — `POST` each event to an OpenLineage endpoint (for example Marquez `http://localhost:5001/api/v1/lineage`)
- **FILE_AND_HTTP** — both

## Namespaces

- **Job namespace** — OpenLineage jobs (default `hop-data-vault` + project key)
- **Dataset namespace** — optional override for **all** input/output datasets; leave empty to use Hop connection names (e.g. `Vault`), catalog sources, or staging labels

## Tips

- Start Marquez locally with `scripts/run-marquez.sh up` before HTTP export.
- Column lineage is derived from the same collectors that power the modeler **Lineage** tab — models remain the source of truth.
- Operational metrics enrichment is optional and reads `load_pipeline_metric` when an ops database is configured.

See `docs/openlineage-export.adoc` for dataset naming conventions and Marquez setup.
