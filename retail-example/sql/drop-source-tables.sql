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

-- Drop CRM source landing tables (test_source database)
DROP TABLE IF EXISTS warehouse_product;
DROP TABLE IF EXISTS warehouse;
DROP TABLE IF EXISTS order_shipment_event;
DROP TABLE IF EXISTS order_line;
DROP TABLE IF EXISTS order_header;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS customer_prefs;
DROP TABLE IF EXISTS customer_address;
DROP TABLE IF EXISTS customer_contact;
DROP TABLE IF EXISTS customer_demo;
DROP TABLE IF EXISTS customer_hub;