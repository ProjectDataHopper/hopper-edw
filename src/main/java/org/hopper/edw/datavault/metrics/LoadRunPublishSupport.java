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

import org.apache.hop.core.util.Utils;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/** Builds catalog/database publish context when metrics collection is enabled. */
public final class LoadRunPublishSupport {

  private LoadRunPublishSupport() {}

  public static DvUpdateMetricsCollector.LoadRunPublishContext contextWhenEnabled(
      String metricsOutputFolder,
      String catalogConnectionName,
      String targetDatabaseName,
      String modelType,
      IWorkflowEngine<WorkflowMeta> parentWorkflow) {
    if (Utils.isEmpty(metricsOutputFolder) || Utils.isEmpty(targetDatabaseName)) {
      return null;
    }
    return DvUpdateMetricsCollector.LoadRunPublishContext.withDefaults(
        catalogConnectionName, targetDatabaseName, resolveWorkflowName(parentWorkflow), modelType);
  }

  private static String resolveWorkflowName(IWorkflowEngine<WorkflowMeta> parentWorkflow) {
    if (parentWorkflow == null) {
      return null;
    }
    WorkflowMeta workflowMeta = parentWorkflow.getWorkflowMeta();
    return workflowMeta != null ? workflowMeta.getName() : null;
  }
}
