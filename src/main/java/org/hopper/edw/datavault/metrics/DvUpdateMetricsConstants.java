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
package org.hopper.edw.datavault.metrics;

/** Variables and naming conventions for Data Vault update pipeline metrics collection. */
public final class DvUpdateMetricsConstants {

  /** Sentinel variable set on the orchestrator; inherited by staged update pipelines. */
  public static final String VAR_RUN_ID = "DV_UPDATE_METRICS_RUN_ID";

  /** Optional model name for log context (set together with {@link #VAR_RUN_ID}). */
  public static final String VAR_MODEL_NAME = "DV_UPDATE_MODEL_NAME";

  /** Model layer for metrics publishing: {@code dv}, {@code bv}, or {@code dm}. */
  public static final String VAR_MODEL_TYPE = "DV_UPDATE_MODEL_TYPE";

  /** Workflow name recorded on the load run row. */
  public static final String VAR_WORKFLOW_NAME = "DV_UPDATE_WORKFLOW_NAME";

  /** Correlates DV/BV/DM load runs within one vault update wave (set by Begin Vault Update). */
  public static final String VAR_WORKFLOW_EXECUTION_ID = "DV_WORKFLOW_EXECUTION_ID";

  /** ISO-8601 start timestamp for the vault update wave (set by Begin Vault Update). */
  public static final String VAR_WORKFLOW_EXECUTION_STARTED_AT = "DV_WORKFLOW_EXECUTION_STARTED_AT";

  /**
   * Target database connection for load metrics tables (defaults to model target DB when unset).
   */
  public static final String VAR_METRICS_DATABASE = "DV_UPDATE_METRICS_DATABASE";

  /** Data catalog connection for operations record definitions (optional; skipped when empty). */
  public static final String VAR_METRICS_CATALOG_CONNECTION =
      "DV_UPDATE_METRICS_CATALOG_CONNECTION";

  public static final String ORCHESTRATOR_NAME_PREFIX = "DV Update Orchestrator - ";

  public static final String BULK_WRITE_TRANSFORM_PREFIX = "bulk_load_to_";

  public static final String STAGING_WRITE_TRANSFORM_PREFIX = "stage_to_";

  public static final String STS_TARGET_TRANSFORM_PREFIX = "sts_target_";

  public static final String SOURCE_TRANSFORM_PREFIX = "source ";

  /** Dimensional model pipelines use {@code source_<tableName>} table inputs. */
  public static final String DIMENSIONAL_SOURCE_TRANSFORM_PREFIX = "source_";

  public static final String TARGET_TRANSFORM_PREFIX = "target_";

  /** Dimensional SCD2 pipelines use {@code target_<tableName>} table inputs. */
  public static final String DIMENSIONAL_TARGET_TRANSFORM_PREFIX = "target_";

  public static final String TARGET_TRANSFORM_DB_PREFIX = "target ";

  public static final String WRITE_TRANSFORM_PREFIX = "write_to_";

  private DvUpdateMetricsConstants() {}
}
