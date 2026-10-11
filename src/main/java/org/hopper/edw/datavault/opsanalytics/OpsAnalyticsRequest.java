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
package org.hopper.edw.datavault.opsanalytics;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.variables.IVariables;

/** Connection and schema choices for an operational analytics source model. */
@Getter
@Setter
public class OpsAnalyticsRequest {

  private String connectionName = "OPS";
  private String schemaName = "dv_ops";

  /** Folder under which models and workflows are written. */
  private String outputFolder;

  /** Resource definition group name. Blank keeps {@code ops-analytics}. */
  private String groupName;

  private String dvTargetConnection = "ops-analytics-dv";
  private String bvTargetConnection = "ops-analytics-bv";
  private String dmTargetConnection = "ops-analytics-dm";

  /** Schema of {@code hop_executions}. Empty uses the connection default schema. */
  private String executionSchema = "";

  /** Pipeline run configuration for the operational model update. */
  private String pipelineRunConfiguration = OpsAnalyticsRunConfigurationSupport.PIPELINE_RUN_CONFIGURATION;

  /** Workflow run configuration for the operational model update. */
  private String workflowRunConfiguration = OpsAnalyticsRunConfigurationSupport.WORKFLOW_RUN_CONFIGURATION;

  private boolean includeExecutions;
  private DatabaseMeta databaseMeta;
  private IVariables variables;

  public static OpsAnalyticsRequest defaults() {
    return new OpsAnalyticsRequest();
  }
}
