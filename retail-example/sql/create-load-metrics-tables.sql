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

-- Load-run metrics tables for catalog-backed operations telemetry (Phase 1).
-- Target database: OPS connection (${DB_OPS_NAME}, e.g. test_ops). Not the Vault EDW.
-- Optional Postgres bootstrap for the retail sample. Prefer Generate SQL on the
-- Execution metrics profile for dialect-aware DDL (auto-create also runs on first publish).

CREATE SCHEMA IF NOT EXISTS dv_ops;

CREATE TABLE IF NOT EXISTS dv_ops.load_run (
  run_id           VARCHAR(64)  NOT NULL,
  started_at       TIMESTAMP    NULL,
  finished_at      TIMESTAMP    NULL,
  model_type       VARCHAR(16)  NULL,
  model_name       VARCHAR(255) NULL,
  workflow_name    VARCHAR(255) NULL,
  workflow_execution_id VARCHAR(64) NULL,
  log_channel_id   VARCHAR(64)  NULL,
  pipeline_run_configuration VARCHAR(255) NULL,
  success          BOOLEAN      NULL,
  error_count      BIGINT       NULL,
  PRIMARY KEY (run_id)
);

CREATE TABLE IF NOT EXISTS dv_ops.load_pipeline_metric (
  run_id                 VARCHAR(64)  NOT NULL,
  pipeline_name          VARCHAR(255) NOT NULL,
  element_type           VARCHAR(64)  NULL,
  element_name           VARCHAR(255) NULL,
  source_name            VARCHAR(255) NULL,
  source_rows_read       BIGINT       NULL,
  target_rows_read       BIGINT       NULL,
  target_rows_inserted   BIGINT       NULL,
  errors                 BIGINT       NULL,
  execution_start_date   TIMESTAMP    NULL,
  execution_end_date     TIMESTAMP    NULL,
  duration_ms            BIGINT       NULL,
  PRIMARY KEY (run_id, pipeline_name)
);

CREATE TABLE IF NOT EXISTS dv_ops.load_transform_metric (
  run_id                VARCHAR(64)  NOT NULL,
  pipeline_name         VARCHAR(255) NOT NULL,
  transform_name        VARCHAR(255) NOT NULL,
  logical_role          VARCHAR(64)  NULL,
  element_type          VARCHAR(64)  NULL,
  element_name          VARCHAR(255) NULL,
  parent_element_name   VARCHAR(255) NULL,
  rows_read             BIGINT       NULL,
  rows_written          BIGINT       NULL,
  rows_updated          BIGINT       NULL,
  rows_rejected         BIGINT       NULL,
  errors                BIGINT       NULL,
  duration_ms           BIGINT       NULL,
  PRIMARY KEY (run_id, pipeline_name, transform_name)
);

CREATE TABLE IF NOT EXISTS dv_ops.load_insight (
  run_id               VARCHAR(64)  NOT NULL,
  insight_seq          BIGINT       NOT NULL,
  severity             VARCHAR(16)  NULL,
  code                 VARCHAR(64)  NULL,
  message              VARCHAR(2000) NULL,
  element_name         VARCHAR(255) NULL,
  related_element_name VARCHAR(255) NULL,
  metric_json          VARCHAR(4000) NULL,
  PRIMARY KEY (run_id, insight_seq)
);

CREATE TABLE IF NOT EXISTS dv_ops.workflow_load_overview (
  overview_id              VARCHAR(64)   NOT NULL,
  workflow_execution_id    VARCHAR(64)   NULL,
  root_workflow_name       VARCHAR(255)  NULL,
  metrics_workflow_name    VARCHAR(255)  NULL,
  started_at               TIMESTAMP     NULL,
  finished_at              TIMESTAMP     NULL,
  duration_ms              BIGINT        NULL,
  model_count              BIGINT        NULL,
  pipeline_count           BIGINT        NULL,
  insight_count            BIGINT        NULL,
  total_source_rows_read   BIGINT        NULL,
  total_target_rows_inserted BIGINT      NULL,
  total_errors             BIGINT        NULL,
  success                  BOOLEAN       NULL,
  PRIMARY KEY (overview_id)
);

CREATE TABLE IF NOT EXISTS dv_ops.workflow_load_overview_model (
  overview_id              VARCHAR(64)   NOT NULL,
  sequence_no              BIGINT        NOT NULL,
  load_run_id              VARCHAR(64)   NULL,
  model_type               VARCHAR(16)   NULL,
  model_name               VARCHAR(255)  NULL,
  pipeline_count           BIGINT        NULL,
  source_rows_read         BIGINT        NULL,
  target_rows_read         BIGINT        NULL,
  target_rows_inserted     BIGINT        NULL,
  errors                   BIGINT        NULL,
  duration_ms              BIGINT        NULL,
  insight_count            BIGINT        NULL,
  success                  BOOLEAN       NULL,
  PRIMARY KEY (overview_id, sequence_no)
);

/*
 * Create a table to store Hop execution information in.
 *
 */
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

