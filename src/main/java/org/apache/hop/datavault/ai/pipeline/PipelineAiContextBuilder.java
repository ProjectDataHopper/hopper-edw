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
package org.apache.hop.datavault.ai.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.HopAiCheckResultsSerializer;
import org.apache.hop.datavault.ai.HopAiTextUtil;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

public final class PipelineAiContextBuilder {

  private static final int MAX_TOPOLOGY_XML_CHARS = 120_000;
  private static final int MAX_LOG_CHARS = 20_000;
  private static final int MAX_CATALOG_ENTRIES = 180;

  private PipelineAiContextBuilder() {}

  public static PipelineAiContextBundle build(
      PipelineMeta pipelineMeta,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      PipelineAiRequest request)
      throws HopException {
    if (pipelineMeta == null) {
      throw new HopException("No pipeline is open");
    }
    if (request == null || Utils.isEmpty(request.getUserPrompt())) {
      throw new HopException("Please enter a question for the AI advisor");
    }

    PipelineAiScenario scenario =
        request.getScenario() != null ? request.getScenario() : PipelineAiScenario.PIPELINE_GENERAL;

    String topologyXml = "";
    if (request.isIncludeTopologyXml()) {
      topologyXml = HopAiTextUtil.truncate(pipelineMeta.getXml(variables), MAX_TOPOLOGY_XML_CHARS);
    }

    String checkResultsJson = "";
    if (request.isIncludeCheckResults() && metadataProvider != null) {
      List<ICheckResult> results = new ArrayList<>();
      pipelineMeta.checkTransforms(results, false, null, variables, metadataProvider);
      checkResultsJson = HopAiCheckResultsSerializer.serialize(results);
    }

    String transformCatalogJson = "";
    if (request.isIncludeTransformCatalog()) {
      transformCatalogJson = serializeTransformCatalog();
    }

    String focus =
        !Utils.isEmpty(request.getFocusTransformName()) ? request.getFocusTransformName() : null;

    return PipelineAiContextBundle.builder()
        .scenario(scenario)
        .userPrompt(request.getUserPrompt())
        .structureJson(serializeStructure(pipelineMeta, focus))
        .summaryJson(serializeSummary(pipelineMeta, variables))
        .topologyXml(topologyXml)
        .checkResultsJson(checkResultsJson)
        .logsExcerpt(
            request.isIncludeExecutionLog()
                ? HopAiTextUtil.truncate(request.getLogsExcerpt(), MAX_LOG_CHARS)
                : "")
        .transformCatalogJson(transformCatalogJson)
        .focusTransformName(focus)
        .followUp(request.isFollowUp())
        .appliedChangeSummaries(
            request.getAppliedChangeSummaries() != null
                ? List.copyOf(request.getAppliedChangeSummaries())
                : List.of())
        .build();
  }

  public static String serializeStructure(PipelineMeta pipelineMeta, String focusTransformName) {
    StringBuilder json = new StringBuilder();
    json.append("{\"transforms\":[");
    List<TransformMeta> transforms = pipelineMeta.getTransforms();
    for (int i = 0; i < transforms.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      TransformMeta transform = transforms.get(i);
      json.append("{\"name\":").append(HopAiTextUtil.jsonString(transform.getName()));
      json.append(",\"pluginId\":")
          .append(HopAiTextUtil.jsonString(transform.getTransformPluginId()));
      json.append(",\"copies\":").append(HopAiTextUtil.jsonString(transform.getCopiesString()));
      json.append(",\"distributes\":").append(transform.isDistributes());
      json.append('}');
    }
    json.append("],\"hops\":[");
    List<PipelineHopMeta> hops = pipelineMeta.getHops();
    for (int i = 0; i < hops.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      PipelineHopMeta hop = hops.get(i);
      json.append("{\"from\":").append(HopAiTextUtil.jsonString(hop.getFromTransform().getName()));
      json.append(",\"to\":").append(HopAiTextUtil.jsonString(hop.getToTransform().getName()));
      json.append(",\"enabled\":").append(hop.isEnabled());
      json.append('}');
    }
    json.append(']');
    if (!Utils.isEmpty(focusTransformName)) {
      json.append(",\"focusTransform\":").append(HopAiTextUtil.jsonString(focusTransformName));
    }
    json.append('}');
    return json.toString();
  }

  public static String serializeSummary(PipelineMeta pipelineMeta, IVariables variables) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":").append(HopAiTextUtil.jsonString(pipelineMeta.getName()));
    json.append(",\"filename\":").append(HopAiTextUtil.jsonString(pipelineMeta.getFilename()));
    json.append(",\"transformCount\":").append(pipelineMeta.getTransforms().size());
    json.append(",\"hopCount\":").append(pipelineMeta.getHops().size());
    json.append(",\"parameterNames\":[");
    String[] params = pipelineMeta.listParameters();
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append(HopAiTextUtil.jsonString(params[i]));
    }
    json.append("]}");
    return json.toString();
  }

  static String serializeTransformCatalog() {
    StringBuilder json = new StringBuilder();
    json.append("{\"transforms\":[");
    PluginRegistry registry = PluginRegistry.getInstance();
    List<IPlugin> plugins = new ArrayList<>(registry.getPlugins(TransformPluginType.class));
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
