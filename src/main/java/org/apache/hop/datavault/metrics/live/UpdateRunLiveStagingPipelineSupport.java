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
 */
package org.apache.hop.datavault.metrics.live;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.hop.core.Result;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.workflow.IActionListener;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/** Tracks running pipeline actions inside generated staging-file bulk workflows. */
public final class UpdateRunLiveStagingPipelineSupport {

  private static final String PIPELINE_ACTION_PLUGIN_ID = "PIPELINE";
  private static final String RUN_ACTION_PREFIX = "run_";
  private static final String BULK_LOAD_ACTION_PREFIX = "bulk_load_";

  private static final ConcurrentMap<IWorkflowEngine<WorkflowMeta>, ConcurrentMap<String, IAction>>
      ACTIVE_PIPELINE_ACTIONS = new ConcurrentHashMap<>();

  private UpdateRunLiveStagingPipelineSupport() {}

  public static IActionListener<WorkflowMeta> pipelineTrackingListener() {
    return new IActionListener<>() {
      @Override
      public void beforeExecution(
          IWorkflowEngine<WorkflowMeta> workflow, ActionMeta actionMeta, IAction cloneAction) {
        if (!isPipelineAction(cloneAction) || workflow == null || actionMeta == null) {
          return;
        }
        ACTIVE_PIPELINE_ACTIONS
            .computeIfAbsent(workflow, ignored -> new ConcurrentHashMap<>())
            .put(actionMeta.getName(), cloneAction);
      }

      @Override
      public void afterExecution(
          IWorkflowEngine<WorkflowMeta> workflow,
          ActionMeta actionMeta,
          IAction cloneAction,
          Result result) {
        if (workflow == null || actionMeta == null) {
          return;
        }
        ConcurrentMap<String, IAction> activeActions = ACTIVE_PIPELINE_ACTIONS.get(workflow);
        if (activeActions == null) {
          return;
        }
        activeActions.remove(actionMeta.getName());
        if (activeActions.isEmpty()) {
          ACTIVE_PIPELINE_ACTIONS.remove(workflow, activeActions);
        }
      }
    };
  }

  public static List<PipelineLiveMetrics> extractActivePipelines(
      IWorkflowEngine<WorkflowMeta> workflowEngine) {
    List<PipelineLiveMetrics> pipelines = new ArrayList<>();
    if (workflowEngine == null) {
      return pipelines;
    }
    ConcurrentMap<String, IAction> activeActions = ACTIVE_PIPELINE_ACTIONS.get(workflowEngine);
    if (activeActions == null || activeActions.isEmpty()) {
      return pipelines;
    }
    for (IAction action : activeActions.values()) {
      IPipelineEngine<PipelineMeta> pipelineEngine = resolvePipelineEngine(action);
      if (pipelineEngine == null || pipelineEngine.getPipelineMeta() == null) {
        continue;
      }
      pipelines.add(UpdateRunLiveMetricsExtractor.extractPipeline(pipelineEngine));
    }
    return pipelines;
  }

  public static String resolveFallbackElementName(IWorkflowEngine<WorkflowMeta> workflowEngine) {
    if (workflowEngine == null) {
      return null;
    }
    java.util.Set<ActionMeta> activeActions = workflowEngine.getActiveActions();
    if (activeActions == null || activeActions.isEmpty()) {
      return null;
    }
    for (ActionMeta actionMeta : activeActions) {
      if (actionMeta == null || Utils.isEmpty(actionMeta.getName())) {
        continue;
      }
      String actionName = actionMeta.getName();
      if (actionName.startsWith(RUN_ACTION_PREFIX)) {
        return elementNameFromGeneratedPipeline(actionName.substring(RUN_ACTION_PREFIX.length()));
      }
      if (actionName.startsWith(BULK_LOAD_ACTION_PREFIX)) {
        String tablePart = actionName.substring(BULK_LOAD_ACTION_PREFIX.length());
        int shardSeparator = tablePart.lastIndexOf('_');
        if (shardSeparator > 0 && isNumericSuffix(tablePart.substring(shardSeparator + 1))) {
          tablePart = tablePart.substring(0, shardSeparator);
        }
        return tablePart.replace('_', '-');
      }
    }
    return null;
  }

  static boolean isPipelineAction(IAction action) {
    if (action == null) {
      return false;
    }
    String pluginId = action.getPluginId();
    if (PIPELINE_ACTION_PLUGIN_ID.equals(pluginId)) {
      return true;
    }
    return "org.apache.hop.workflow.actions.pipeline.ActionPipeline"
        .equals(action.getClass().getName());
  }

  static IPipelineEngine<PipelineMeta> resolvePipelineEngine(IAction action) {
    if (action == null) {
      return null;
    }
    try {
      Method getPipeline = action.getClass().getMethod("getPipeline");
      Object value = getPipeline.invoke(action);
      if (value instanceof IPipelineEngine<?> engine) {
        @SuppressWarnings("unchecked")
        IPipelineEngine<PipelineMeta> pipelineEngine = (IPipelineEngine<PipelineMeta>) engine;
        return pipelineEngine;
      }
    } catch (ReflectiveOperationException ignored) {
      // Not a pipeline action or plugin not present.
    }
    return null;
  }

  static void clearWorkflow(IWorkflowEngine<WorkflowMeta> workflowEngine) {
    if (workflowEngine != null) {
      ACTIVE_PIPELINE_ACTIONS.remove(workflowEngine);
    }
  }

  private static String elementNameFromGeneratedPipeline(String pipelineName) {
    if (Utils.isEmpty(pipelineName)) {
      return null;
    }
    String normalized = pipelineName;
    if (normalized.matches("^\\d{4}-.*")) {
      normalized = normalized.substring(normalized.indexOf('-') + 1);
    }
    String prefix = DimensionalConfiguration.DEFAULT_FACT_PIPELINE_NAME_PREFIX;
    if (normalized.startsWith(prefix)) {
      return normalized.substring(prefix.length());
    }
    prefix = DimensionalConfiguration.DEFAULT_DIMENSION_PIPELINE_NAME_PREFIX;
    if (normalized.startsWith(prefix)) {
      return normalized.substring(prefix.length());
    }
    prefix = DimensionalConfiguration.DEFAULT_JUNK_DIMENSION_PIPELINE_NAME_PREFIX;
    if (normalized.startsWith(prefix)) {
      return normalized.substring(prefix.length());
    }
    prefix = DimensionalConfiguration.DEFAULT_BRIDGE_PIPELINE_NAME_PREFIX;
    if (normalized.startsWith(prefix)) {
      return normalized.substring(prefix.length());
    }
    return normalized;
  }

  private static boolean isNumericSuffix(String value) {
    if (Utils.isEmpty(value)) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
