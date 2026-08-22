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

-- Minimal staging view stubs for dimensional model source SQL.
-- Replace with richer projections as the example matures.

CREATE OR REPLACE VIEW stg_e2e_date AS
SELECT
  TO_CHAR(d, 'YYYYMMDD')::INTEGER AS date_key,
  d::DATE AS full_date,
  EXTRACT(YEAR FROM d)::INTEGER AS year
FROM generate_series(DATE '2024-01-01', DATE '2026-12-31', INTERVAL '1 day') AS d;

CREATE OR REPLACE VIEW stg_e2e_customer AS
SELECT
  0::INTEGER AS customer_id,
  'Unknown Customer'::VARCHAR(50) AS customer_name,
  'N/A'::VARCHAR(50) AS city;

CREATE OR REPLACE VIEW stg_e2e_product AS
SELECT
  'P000000'::VARCHAR(7) AS product_id,
  'Unknown Product'::VARCHAR(50) AS product_name;

CREATE OR REPLACE VIEW stg_e2e_warehouse AS
SELECT
  0::INTEGER AS warehouse_id,
  'Unknown Warehouse'::VARCHAR(50) AS warehouse_name,
  'N/A'::VARCHAR(50) AS city;

CREATE OR REPLACE VIEW stg_e2e_orders AS
SELECT
  'O000000'::VARCHAR(7) AS order_id,
  TO_CHAR(CURRENT_DATE, 'YYYYMMDD')::INTEGER AS order_date_key,
  TO_CHAR(CURRENT_DATE, 'YYYYMMDD')::INTEGER AS shipping_date_key,
  TO_CHAR(CURRENT_DATE, 'YYYYMMDD')::INTEGER AS delivery_date_key,
  0::NUMERIC(12, 2) AS order_amount;

CREATE OR REPLACE VIEW stg_e2e_order_lines AS
SELECT
  'O000000'::VARCHAR(7) AS order_id,
  'P000000'::VARCHAR(7) AS product_id,
  1::INTEGER AS line_number,
  0::INTEGER AS quantity,
  0::NUMERIC(12, 2) AS line_amount;

CREATE OR REPLACE VIEW stg_e2e_order_flags AS
SELECT
  'N'::VARCHAR(1) AS promo_flag,
  'CARD'::VARCHAR(10) AS payment_type,
  'STANDARD'::VARCHAR(10) AS ship_method;

CREATE OR REPLACE VIEW stg_e2e_customer_product_bridge AS
SELECT
  0::BIGINT AS customer_key,
  0::BIGINT AS product_key,
  1.0::NUMERIC(5, 4) AS allocation_weight;

CREATE OR REPLACE VIEW stg_e2e_coverage AS
SELECT
  0::INTEGER AS customer_id,
  'P000000'::VARCHAR(7) AS product_id;

CREATE OR REPLACE VIEW stg_e2e_daily_balance AS
SELECT
  CURRENT_DATE AS snapshot_date,
  0::INTEGER AS customer_id,
  'P000000'::VARCHAR(7) AS product_id,
  0::NUMERIC(12, 2) AS balance_amount;

CREATE OR REPLACE VIEW stg_e2e_order_lifecycle AS
SELECT
  'O000000'::VARCHAR(7) AS order_id,
  0::INTEGER AS customer_id,
  'P000000'::VARCHAR(7) AS product_id,
  0::NUMERIC(12, 2) AS order_amount;

CREATE OR REPLACE VIEW stg_e2e_sales AS
SELECT
  0::INTEGER AS customer_id,
  'P000000'::VARCHAR(7) AS product_id,
  0::INTEGER AS quantity,
  0::NUMERIC(12, 2) AS amount;

CREATE OR REPLACE VIEW stg_e2e_sales_agg AS
SELECT
  0::INTEGER AS customer_id,
  'P000000'::VARCHAR(7) AS product_id,
  0::NUMERIC(12, 2) AS total_amount;