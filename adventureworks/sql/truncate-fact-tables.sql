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

-- Fact pipelines insert without truncate. A no-change incremental would
-- duplicate sales/purchase lines unless these tables are emptied first.
-- TYPE1 dimensions upsert; BV SCD2/PIT rebuild themselves.

TRUNCATE TABLE f_sales_order_line;
TRUNCATE TABLE f_purchase_order_line;
