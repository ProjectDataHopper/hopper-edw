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
package org.hopper.edw.datavault.ai.workflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.ai.HopAiCheckResultsSerializer;
import org.hopper.edw.datavault.ai.HopAiTextUtil;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;

public final class WorkflowAiContextBuilder {

  private static final int MAX_TOPOLOGY_XML_CHARS = 120_000;
  private static final int MAX_LOG_CHARS = 20_000;
  private static final int MAX_CATALOG_ENTRIES = 120;

  private WorkflowAiContextBuilder() {}

  public static WorkflowAiContextBundle build(
      WorkflowMeta workflowMeta,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      WorkflowAiRequest request)
      throws HopException {
    if (workflowMeta == null) {
      throw new HopException("No workflow is open");
    }
    if (request == null || Utils.isEmpty(request.getUserPrompt())) {
      throw new HopException("Please enter a question for the AI advisor");
    }

    WorkflowAiScenario scenario =
        request.getScenario() != null ? request.getScenario() : WorkflowAiScenario.WORKFLOW_GENERAL;

    String topologyXml = "";
    if (request.isIncludeTopologyXml()) {
      topologyXml = HopAiTextUtil.truncate(workflowMeta.getXml(variables), MAX_TOPOLOGY_XML_CHARS);
    }

    String checkResultsJson = "";
    if (request.isIncludeCheckResults() && metadataProvider != null) {
      List<ICheckResult> results = new ArrayList<>();
      workflowMeta.checkActions(results, false, null, variables, metadataProvider);
      checkResultsJson = HopAiCheckResultsSerializer.serialize(results);
    }

    String actionCatalogJson = "";
    if (request.isIncludeActionCatalog()) {
      actionCatalogJson = serializeActionCatalog();
    }

    String focus =
        !Utils.isEmpty(request.getFocusActionName()) ? request.getFocusActionName() : null;

    return WorkflowAiContextBundle.builder()
        .scenario(scenario)
        .userPrompt(request.getUserPrompt())
        .structureJson(serializeStructure(workflowMeta, focus))
        .summaryJson(serializeSummary(workflowMeta))
        .topologyXml(topologyXml)
        .checkResultsJson(checkResultsJson)
        .logsExcerpt(
            request.isIncludeExecutionLog()
                ? HopAiTextUtil.truncate(request.getLogsExcerpt(), MAX_LOG_CHARS)
                : "")
        .actionCatalogJson(actionCatalogJson)
        .focusActionName(focus)
        .followUp(request.isFollowUp())
        .appliedChangeSummaries(
            request.getAppliedChangeSummaries() != null
                ? List.copyOf(request.getAppliedChangeSummaries())
                : List.of())
        .build();
  }

  public static String serializeStructure(WorkflowMeta workflowMeta, String focusActionName) {
    StringBuilder json = new StringBuilder();
    json.append("{\"actions\":[");
    List<ActionMeta> actions = workflowMeta.getActions();
    for (int i = 0; i < actions.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      ActionMeta action = actions.get(i);
      json.append("{\"name\":").append(HopAiTextUtil.jsonString(action.getName()));
      json.append(",\"pluginId\":")
          .append(HopAiTextUtil.jsonString(action.getAction().getPluginId()));
      json.append(",\"parallel\":").append(action.isLaunchingInParallel());
      json.append('}');
    }
    json.append("],\"hops\":[");
    List<WorkflowHopMeta> hops = workflowMeta.getWorkflowHops();
    for (int i = 0; i < hops.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      WorkflowHopMeta hop = hops.get(i);
      json.append("{\"from\":").append(HopAiTextUtil.jsonString(hop.getFromAction().getName()));
      json.append(",\"to\":").append(HopAiTextUtil.jsonString(hop.getToAction().getName()));
      json.append(",\"unconditional\":").append(hop.isUnconditional());
      json.append(",\"evaluation\":").append(hop.getEvaluation());
      json.append('}');
    }
    json.append(']');
    if (!Utils.isEmpty(focusActionName)) {
      json.append(",\"focusAction\":").append(HopAiTextUtil.jsonString(focusActionName));
    }
    json.append('}');
    return json.toString();
  }

  public static String serializeSummary(WorkflowMeta workflowMeta) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":").append(HopAiTextUtil.jsonString(workflowMeta.getName()));
    json.append(",\"filename\":").append(HopAiTextUtil.jsonString(workflowMeta.getFilename()));
    json.append(",\"actionCount\":").append(workflowMeta.getActions().size());
    json.append(",\"hopCount\":").append(workflowMeta.getWorkflowHops().size());
    json.append(",\"parameterNames\":[");
    String[] params = workflowMeta.listParameters();
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append(HopAiTextUtil.jsonString(params[i]));
    }
    json.append("]}");
    return json.toString();
  }

  static String serializeActionCatalog() {
    StringBuilder json = new StringBuilder();
    json.append("{\"actions\":[");
    PluginRegistry registry = PluginRegistry.getInstance();
    List<IPlugin> plugins = new ArrayList<>(registry.getPlugins(ActionPluginType.class));
    plugins.sort(Comparator.comparing(IPlugin::getCategory).thenComparing(IPlugin::getName));
    int count = 0;
    for (IPlugin plugin : plugins) {
      if (count >= MAX_CATALOG_ENTRIES) {
        break;
      }
      if (count > 0) {
        json.append(',');
      }
      json.append("{\"id\":").append(HopAiTextUtil.jsonString(plugin.getIds()[0]));
      json.append(",\"name\":").append(HopAiTextUtil.jsonString(plugin.getName()));
      json.append(",\"category\":").append(HopAiTextUtil.jsonString(plugin.getCategory()));
      json.append('}');
      count++;
    }
    json.append("]}");
    return json.toString();
  }
}
