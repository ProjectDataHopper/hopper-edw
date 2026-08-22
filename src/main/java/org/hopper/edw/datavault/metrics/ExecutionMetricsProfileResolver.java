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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/** Resolves execution metrics settings from a profile reference and legacy action fields. */
public final class ExecutionMetricsProfileResolver {

  private ExecutionMetricsProfileResolver() {}

  public static ResolvedExecutionMetrics resolve(
      String profileName,
      String legacyMetricsOutputFolder,
      String actionCatalogConnection,
      String modelTargetDatabase,
      String modelType,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String resolvedLegacyFolder = resolveValue(legacyMetricsOutputFolder, variables);

    if (Utils.isEmpty(profileName)) {
      return resolveLegacy(
          resolvedLegacyFolder,
          actionCatalogConnection,
          modelTargetDatabase,
          modelType,
          parentWorkflow);
    }

    ExecutionMetricsProfileMeta profile =
        metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).load(profileName);
    if (profile == null) {
      throw new HopException("Execution metrics profile not found: " + profileName);
    }
    if (!profile.isEnabled()) {
      return ResolvedExecutionMetrics.disabled();
    }

    String metricsFolder = resolveValue(profile.getMetricsOutputFolder(), variables);
    if (Utils.isEmpty(metricsFolder)) {
      return ResolvedExecutionMetrics.disabled();
    }

    String catalogConnection =
        firstNonEmpty(
            resolveValue(profile.getDataCatalogConnection(), variables),
            resolveValue(actionCatalogConnection, variables));
    String targetDatabase =
        firstNonEmpty(
            resolveValue(profile.getTargetDatabaseConnection(), variables),
            resolveValue(modelTargetDatabase, variables));
    if (Utils.isEmpty(targetDatabase)) {
      return ResolvedExecutionMetrics.disabled();
    }

    DvUpdateMetricsCollector.LoadRunPublishContext publishContext =
        new DvUpdateMetricsCollector.LoadRunPublishContext(
            catalogConnection,
            targetDatabase,
            profile.getOperationsSchemaOrDefault(),
            resolveWorkflowName(parentWorkflow),
            modelType,
            profile.isPublishCatalogDefinitions(),
            profile.isPublishDatabaseRows(),
            profile.isAutoCreateTables(),
            profile.getDimLookupPreloadRatioThreshold(),
            profile.getTargetReadRatioThreshold(),
            profile.getSortRowsRiskThreshold(),
            profile.getHighTransformDurationMs(),
            null);

    return new ResolvedExecutionMetrics(true, metricsFolder, publishContext);
  }

  private static ResolvedExecutionMetrics resolveLegacy(
      String metricsOutputFolder,
      String actionCatalogConnection,
      String modelTargetDatabase,
      String modelType,
      IWorkflowEngine<WorkflowMeta> parentWorkflow) {
    if (Utils.isEmpty(metricsOutputFolder) || Utils.isEmpty(modelTargetDatabase)) {
      return ResolvedExecutionMetrics.disabled();
    }
    DvUpdateMetricsCollector.LoadRunPublishContext publishContext =
        DvUpdateMetricsCollector.LoadRunPublishContext.withDefaults(
            actionCatalogConnection,
            modelTargetDatabase,
            resolveWorkflowName(parentWorkflow),
            modelType);
    return new ResolvedExecutionMetrics(true, metricsOutputFolder, publishContext);
  }

  private static String resolveValue(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String firstNonEmpty(String first, String second) {
    if (!Utils.isEmpty(first)) {
      return first;
    }
    return second;
  }

  private static String resolveWorkflowName(IWorkflowEngine<WorkflowMeta> parentWorkflow) {
    if (parentWorkflow == null) {
      return null;
    }
    WorkflowMeta workflowMeta = parentWorkflow.getWorkflowMeta();
    return workflowMeta != null ? workflowMeta.getName() : null;
  }
}
