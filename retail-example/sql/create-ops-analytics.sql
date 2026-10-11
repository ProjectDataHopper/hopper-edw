/*
 * Copyright 2026 i-Bridge bv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Operational analytics warehouse.
-- Connection: OpsAnalytics (${DB_OPS_ANALYTICS_NAME}, for example test_ops_analytics).
-- Data Vault and dimensional tables use public.
-- Business Vault views and the bridge use ops_bv. The load does not create this schema.

CREATE SCHEMA IF NOT EXISTS ops_bv;
