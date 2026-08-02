/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

-- Wipe Vault EDW targets before a clean retail initial load.
-- Drop dependents before parents (satellites/links/facts before hubs/dims).
-- CASCADE covers FK edges that may exist after optional PK/FK DDL.

-- Drop views first to avoid dependency errors
DROP VIEW IF EXISTS satb_product_hb CASCADE;
DROP VIEW IF EXISTS customer_mail_phone CASCADE;

-- Dimensional model tables (facts before dims)
DROP TABLE IF EXISTS f_sales_aggs CASCADE;
DROP TABLE IF EXISTS f_sales CASCADE;
DROP TABLE IF EXISTS f_order_lifecycle CASCADE;
DROP TABLE IF EXISTS f_daily_balance CASCADE;
DROP TABLE IF EXISTS f_coverage CASCADE;
DROP TABLE IF EXISTS f_pit_inventory CASCADE;
DROP TABLE IF EXISTS f_order_lines CASCADE;
DROP TABLE IF EXISTS f_orders CASCADE;
DROP TABLE IF EXISTS bridge_customer_product CASCADE;
DROP TABLE IF EXISTS d_order_junk CASCADE;
DROP TABLE IF EXISTS d_orders_junk CASCADE;
DROP TABLE IF EXISTS d_order CASCADE;
DROP TABLE IF EXISTS d_warehouse CASCADE;
DROP TABLE IF EXISTS d_date CASCADE;
DROP TABLE IF EXISTS d_product CASCADE;
DROP TABLE IF EXISTS d_customer CASCADE;

-- Business vault tables
DROP TABLE IF EXISTS customer_360_bv CASCADE;

-- Data vault: link satellites, then links, then satellites, then hubs
DROP TABLE IF EXISTS sat_lnk_order_line CASCADE;
DROP TABLE IF EXISTS sat_lnk_warehouse_product CASCADE;
DROP TABLE IF EXISTS lnk_order_line CASCADE;
DROP TABLE IF EXISTS lnk_warehouse_product CASCADE;
DROP TABLE IF EXISTS lnk_order_rep CASCADE;
DROP TABLE IF EXISTS lnk_order CASCADE;

DROP TABLE IF EXISTS sat_order CASCADE;
DROP TABLE IF EXISTS sat_warehouse CASCADE;
DROP TABLE IF EXISTS sat_product CASCADE;
DROP TABLE IF EXISTS sat_customer_prefs CASCADE;
DROP TABLE IF EXISTS sat_customer_address CASCADE;
DROP TABLE IF EXISTS sat_customer_contact CASCADE;
DROP TABLE IF EXISTS sat_customer_demo CASCADE;
DROP TABLE IF EXISTS sat_customer CASCADE;

DROP TABLE IF EXISTS hub_order CASCADE;
DROP TABLE IF EXISTS hub_warehouse CASCADE;
DROP TABLE IF EXISTS hub_product CASCADE;
DROP TABLE IF EXISTS hub_sales_rep CASCADE;
DROP TABLE IF EXISTS hub_secondary_rep CASCADE;
DROP TABLE IF EXISTS hub_customer CASCADE;

-- Staging views (dimensional / vault staging)
DROP VIEW IF EXISTS stg_e2e_sales_agg CASCADE;
DROP VIEW IF EXISTS stg_e2e_sales CASCADE;
DROP VIEW IF EXISTS stg_e2e_order_lifecycle CASCADE;
DROP VIEW IF EXISTS stg_e2e_daily_balance CASCADE;
DROP VIEW IF EXISTS stg_e2e_coverage CASCADE;
DROP VIEW IF EXISTS stg_e2e_customer_product_bridge CASCADE;
DROP VIEW IF EXISTS stg_e2e_order_flags CASCADE;
DROP VIEW IF EXISTS stg_e2e_order_lines CASCADE;
DROP VIEW IF EXISTS stg_e2e_orders CASCADE;
DROP VIEW IF EXISTS stg_e2e_warehouse CASCADE;
DROP VIEW IF EXISTS stg_e2e_product CASCADE;
DROP VIEW IF EXISTS stg_e2e_customer CASCADE;
DROP VIEW IF EXISTS stg_e2e_date CASCADE;

-- Load control (names used by different retail seeds)
DROP TABLE IF EXISTS retail_load_control CASCADE;
DROP TABLE IF EXISTS load_control CASCADE;
