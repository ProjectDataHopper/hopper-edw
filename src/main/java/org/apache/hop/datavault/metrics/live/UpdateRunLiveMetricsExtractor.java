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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.apache.hop.datavault.metrics.DvUpdateMetricsConstants;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IEngineMetric;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Samples live engine metrics from the orchestrator and its active child pipelines. */
public final class UpdateRunLiveMetricsExtractor {

  private UpdateRunLiveMetricsExtractor() {}

  public static List<PipelineLiveMetrics> extractActivePipelines(
      IPipelineEngine<PipelineMeta> engine) {
    List<PipelineLiveMetrics> pipelines = new ArrayList<>();
    if (engine == null) {
      return pipelines;
    }
    collectChildPipelines(engine, pipelines);
    return pipelines;
  }

  private static void collectChildPipelines(
      IPipelineEngine<PipelineMeta> engine, List<PipelineLiveMetrics> pipelines) {
    if (!(engine instanceof Pipeline pipeline)) {
      return;
    }
    Map<String, IPipelineEngine> activeSubPipelines = pipeline.getActiveSubPipelines();
    if (activeSubPipelines == null || activeSubPipelines.isEmpty()) {
      return;
    }
    for (IPipelineEngine subPipeline : activeSubPipelines.values()) {
      if (subPipeline == null || subPipeline.getPipelineMeta() == null) {
        continue;
      }
      String pipelineName = subPipeline.getPipelineMeta().getName();
      if (Utils.isEmpty(pipelineName)
          || pipelineName.startsWith(DvUpdateMetricsConstants.ORCHESTRATOR_NAME_PREFIX)) {
        continue;
      }
      pipelines.add(extractPipeline(subPipeline));
    }
  }

  public static PipelineLiveMetrics extractPipeline(IPipelineEngine<PipelineMeta> engine) {
    PipelineMeta pipelineMeta = engine.getPipelineMeta();
    UpdateRunLiveState state = resolvePipelineState(engine);
    return PipelineLiveMetrics.builder()
        .pipelineName(pipelineMeta.getName())
        .elementType(pipelineElementType(pipelineMeta))
        .elementName(pipelineElementName(pipelineMeta))
        .sourceName(pipelineSourceName(pipelineMeta))
        .state(state)
        .transforms(extractTransforms(engine))
        .build();
  }

  public static List<TransformLiveMetrics> extractTransforms(IPipelineEngine<PipelineMeta> engine) {
    if (engine == null || engine.getPipelineMeta() == null) {
      return List.of();
    }
    PipelineMeta pipelineMeta = engine.getPipelineMeta();
    EngineMetrics metrics = engine.getEngineMetrics();
    List<TransformLiveMetrics> result = new ArrayList<>();
    for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
      if (transformMeta == null) {
        continue;
      }
      IEngineComponent component = findComponent(metrics, transformMeta.getName());
      result.add(
          TransformLiveMetrics.builder()
              .transformName(transformMeta.getName())
              .pluginId(transformMeta.getTransformPluginId())
              .logicalRole(
                  GeneratedPipelineMetadataSupport.getTransformAttribute(
                      transformMeta, GeneratedPipelineMetadataConstants.LOGICAL_ROLE))
              .rowsRead(metricValue(metrics, component, Pipeline.METRIC_INPUT))
              .rowsWritten(metricValue(metrics, component, Pipeline.METRIC_OUTPUT))
              .bufferIn(metricValue(metrics, component, Pipeline.METRIC_BUFFER_IN))
              .bufferOut(metricValue(metrics, component, Pipeline.METRIC_BUFFER_OUT))
              .running(isRunning(metrics, component))
              .status(statusText(metrics, component))
              .secondsSinceLastProgress(0L)
              .build());
    }
    return result;
  }

  private static UpdateRunLiveState resolvePipelineState(IPipelineEngine<PipelineMeta> engine) {
    if (engine.isFinished()) {
      if (engine.getErrors() > 0) {
        return UpdateRunLiveState.FAILED;
      }
      return UpdateRunLiveState.COMPLETED;
    }
    if (engine.getErrors() > 0) {
      return UpdateRunLiveState.FAILED;
    }
    return UpdateRunLiveState.RUNNING;
  }

  private static String pipelineElementType(PipelineMeta pipelineMeta) {
    return GeneratedPipelineMetadataSupport.getPipelineAttribute(
        pipelineMeta, GeneratedPipelineMetadataConstants.ELEMENT_TYPE);
  }

  private static String pipelineElementName(PipelineMeta pipelineMeta) {
    return GeneratedPipelineMetadataSupport.getPipelineAttribute(
        pipelineMeta, GeneratedPipelineMetadataConstants.ELEMENT_NAME);
  }

  private static String pipelineSourceName(PipelineMeta pipelineMeta) {
    String sourceName =
        GeneratedPipelineMetadataSupport.getPipelineAttribute(
            pipelineMeta, GeneratedPipelineMetadataConstants.SOURCE_NAME);
    return sourceName != null ? sourceName : "";
  }

  private static IEngineComponent findComponent(EngineMetrics metrics, String transformName) {
    if (metrics == null || Utils.isEmpty(transformName)) {
      return null;
    }
    for (IEngineComponent component : metrics.getComponents()) {
      if (component != null && transformName.equals(component.getName())) {
        return component;
      }
    }
    return null;
  }

  private static long metricValue(
      EngineMetrics metrics, IEngineComponent component, IEngineMetric metric) {
    if (metrics == null || component == null || metric == null) {
      return 0L;
    }
    Long value = metrics.getComponentMetric(component, metric);
    return value != null ? value : 0L;
  }

  private static boolean isRunning(EngineMetrics metrics, IEngineComponent component) {
    if (metrics == null || component == null) {
      return false;
    }
    Boolean running = metrics.getComponentRunningMap().get(component);
    return running != null && running;
  }

  private static String statusText(EngineMetrics metrics, IEngineComponent component) {
    if (metrics == null || component == null) {
      return "";
    }
    String status = metrics.getComponentStatusMap().get(component);
    return status != null ? status : "";
  }
}
