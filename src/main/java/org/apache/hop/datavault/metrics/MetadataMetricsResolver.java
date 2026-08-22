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
package org.apache.hop.datavault.metrics;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IEngineMetric;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Resolves pipeline and transform metrics from stamped metadata attributes, with name-heuristic
 * fallback for unstamped pipelines.
 */
public final class MetadataMetricsResolver {

  private MetadataMetricsResolver() {}

  public static DvUpdateTableMetrics resolve(
      IPipelineEngine<PipelineMeta> engine,
      String runId,
      String modelName,
      Optional<DvUpdateMetricsParser.ParsedPipeline> parsed) {
    if (engine == null || engine.getPipelineMeta() == null) {
      throw new IllegalArgumentException("Pipeline engine and metadata are required");
    }

    PipelineMeta pipelineMeta = engine.getPipelineMeta();
    List<TransformRunMetrics> transforms = extractTransformMetrics(engine);
    boolean metadataStamped = hasStampedMetadata(pipelineMeta);

    DvUpdateMetricsParser.ParsedPipeline identity =
        parsed.orElseGet(() -> resolveIdentityFromMetadata(pipelineMeta).orElse(null));

    long sourceRowsRead;
    long targetRowsRead;
    long targetRowsInserted;
    if (metadataStamped && !transforms.isEmpty()) {
      AggregatedPipelineTotals roleTotals = aggregateRoleTotals(transforms);
      sourceRowsRead = roleTotals.sourceRowsRead();
      if (sourceRowsRead == 0L) {
        sourceRowsRead = sumTableInputRowsRead(transforms);
      }
      targetRowsRead = roleTotals.targetRowsRead();
      targetRowsInserted = roleTotals.targetRowsInserted();
    } else if (identity != null) {
      DvUpdateTableMetrics heuristic =
          DvUpdateMetricsExtractor.extract(engine, runId, modelName, identity);
      sourceRowsRead = heuristic.getSourceRowsRead();
      targetRowsRead = heuristic.getTargetRowsRead();
      targetRowsInserted = heuristic.getTargetRowsInserted();
    } else {
      sourceRowsRead = 0;
      targetRowsRead = 0;
      targetRowsInserted = 0;
    }

    String tableType = identity != null ? identity.tableType() : pipelineElementType(pipelineMeta);
    String tableName = identity != null ? identity.tableName() : pipelineElementName(pipelineMeta);
    String sourceName = identity != null ? identity.sourceName() : pipelineSourceName(pipelineMeta);
    String resolvedModelName =
        Utils.isEmpty(modelName)
            ? GeneratedPipelineMetadataSupport.getPipelineAttribute(
                pipelineMeta, GeneratedPipelineMetadataConstants.MODEL_NAME)
            : modelName;
    String modelType =
        GeneratedPipelineMetadataSupport.getPipelineAttribute(
            pipelineMeta, GeneratedPipelineMetadataConstants.MODEL_TYPE);

    Date executionStartDate = engine.getExecutionStartDate();
    Date executionEndDate = engine.getExecutionEndDate();
    return DvUpdateTableMetrics.builder()
        .runId(runId)
        .modelName(resolvedModelName)
        .modelType(modelType)
        .pipelineName(pipelineMeta.getName())
        .tableType(tableType)
        .tableName(tableName)
        .sourceName(sourceName)
        .sourceRowsRead(sourceRowsRead)
        .targetRowsRead(targetRowsRead)
        .targetRowsInserted(targetRowsInserted)
        .errors(engine.getErrors())
        .success(engine.getErrors() == 0)
        .executionStartDate(executionStartDate)
        .executionEndDate(executionEndDate)
        .durationMs(DvUpdateTableMetrics.resolveDurationMs(executionStartDate, executionEndDate))
        .transforms(transforms)
        .build();
  }

  public static List<TransformRunMetrics> extractTransformMetrics(
      IPipelineEngine<PipelineMeta> engine) {
    if (engine == null || engine.getPipelineMeta() == null) {
      return List.of();
    }
    PipelineMeta pipelineMeta = engine.getPipelineMeta();
    EngineMetrics metrics = engine.getEngineMetrics();
    List<TransformRunMetrics> result = new ArrayList<>();
    for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
      if (transformMeta == null) {
        continue;
      }
      IEngineComponent component = findComponent(metrics, transformMeta.getName());
      result.add(
          TransformRunMetrics.builder()
              .transformName(transformMeta.getName())
              .pluginId(transformMeta.getTransformPluginId())
              .logicalRole(
                  GeneratedPipelineMetadataSupport.getTransformAttribute(
                      transformMeta, GeneratedPipelineMetadataConstants.LOGICAL_ROLE))
              .elementType(
                  GeneratedPipelineMetadataSupport.getTransformAttribute(
                      transformMeta, GeneratedPipelineMetadataConstants.ELEMENT_TYPE))
              .elementName(
                  GeneratedPipelineMetadataSupport.getTransformAttribute(
                      transformMeta, GeneratedPipelineMetadataConstants.ELEMENT_NAME))
              .parentElementName(
                  GeneratedPipelineMetadataSupport.getTransformAttribute(
                      transformMeta, GeneratedPipelineMetadataConstants.PARENT_ELEMENT_NAME))
              .lookupCacheMode(
                  GeneratedPipelineMetadataSupport.getTransformAttribute(
                      transformMeta, GeneratedPipelineMetadataConstants.LOOKUP_CACHE_MODE))
              .rowsRead(metricValue(metrics, component, Pipeline.METRIC_INPUT))
              .rowsWritten(metricValue(metrics, component, Pipeline.METRIC_OUTPUT))
              .rowsUpdated(metricValue(metrics, component, Pipeline.METRIC_UPDATED))
              .rowsRejected(metricValue(metrics, component, Pipeline.METRIC_REJECTED))
              .errors(metricValue(metrics, component, Pipeline.METRIC_ERROR))
              .durationMs(resolveDurationMs(component))
              .build());
    }
    return result;
  }

  private static boolean hasStampedMetadata(PipelineMeta pipelineMeta) {
    return !Utils.isEmpty(
        GeneratedPipelineMetadataSupport.getPipelineAttribute(
            pipelineMeta, GeneratedPipelineMetadataConstants.MODEL_TYPE));
  }

  private static Optional<DvUpdateMetricsParser.ParsedPipeline> resolveIdentityFromMetadata(
      PipelineMeta pipelineMeta) {
    String elementType = pipelineElementType(pipelineMeta);
    String elementName = pipelineElementName(pipelineMeta);
    if (Utils.isEmpty(elementType) || Utils.isEmpty(elementName)) {
      return Optional.empty();
    }
    return Optional.of(
        new DvUpdateMetricsParser.ParsedPipeline(
            elementType, elementName, pipelineSourceName(pipelineMeta)));
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

  static AggregatedPipelineTotals aggregateRoleTotals(List<TransformRunMetrics> transforms) {
    return new AggregatedPipelineTotals(
        sumRoleMetric(transforms, GeneratedPipelineMetadataConstants.ROLE_SOURCE_READ, true),
        sumRoleMetric(transforms, GeneratedPipelineMetadataConstants.ROLE_TARGET_READ, true),
        sumRoleMetric(transforms, GeneratedPipelineMetadataConstants.ROLE_WRITE_TARGET, false));
  }

  static long sumTableInputRowsRead(List<TransformRunMetrics> transforms) {
    long total = 0L;
    for (TransformRunMetrics transform : transforms) {
      if (transform == null || !"TableInput".equals(transform.getPluginId())) {
        continue;
      }
      total += Math.max(0L, transform.getRowsRead());
    }
    return total;
  }

  record AggregatedPipelineTotals(
      long sourceRowsRead, long targetRowsRead, long targetRowsInserted) {}

  private static long sumRoleMetric(
      List<TransformRunMetrics> transforms, String role, boolean readMetric) {
    long total = 0L;
    for (TransformRunMetrics transform : transforms) {
      if (transform == null || !role.equals(transform.getLogicalRole())) {
        continue;
      }
      total += readMetric ? transform.getRowsRead() : transform.getRowsWritten();
    }
    return total;
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

  private static long resolveDurationMs(IEngineComponent component) {
    if (component == null) {
      return 0L;
    }
    return Math.max(0L, component.getExecutionDuration());
  }
}
