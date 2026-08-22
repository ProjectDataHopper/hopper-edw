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

-- Retail-example source, EDW, and operations/metrics databases (integration-tests uses bootstrap DB "test").
-- POSTGRES_USER (test) owns these databases and has full access; explicit OWNER keeps intent clear.
CREATE DATABASE test_source OWNER test;
CREATE DATABASE test_edw OWNER test;
CREATE DATABASE test_ops OWNER test;

\connect test_ops;

CREATE TABLE IF NOT EXISTS hop_executions
(
  id VARCHAR(100)
, name VARCHAR(1024)
, execution_type VARCHAR(32)
, parent_id VARCHAR(100)
, registration_date TIMESTAMP
, execution_start_date TIMESTAMP
, execution_end_date TIMESTAMP
, failed BOOLEAN
, status_description VARCHAR(128)
, duration_ms BIGINT
, json TEXT
)
;
CREATE INDEX IF NOT EXISTS idx_hop_exec_start ON hop_executions(execution_start_date)
;

CREATE INDEX IF NOT EXISTS idx_hop_exec_name ON hop_executions("name")
;

CREATE INDEX IF NOT EXISTS idx_hop_exec_type ON hop_executions(execution_type)
;

CREATE INDEX IF NOT EXISTS idx_hop_exec_failed ON hop_executions(failed)
;

CREATE INDEX IF NOT EXISTS idx_hop_exec_parent ON hop_executions(parent_id)
;

CREATE INDEX IF NOT EXISTS idx_hop_exec_status ON hop_executions(status_description)
;

