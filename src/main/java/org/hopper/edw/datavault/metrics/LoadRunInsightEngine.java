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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;

/** Deterministic insight rules over collected load-run metrics. */
public final class LoadRunInsightEngine {

  public static final String CODE_DIM_LOOKUP_PRELOAD_CANDIDATE = "DIM_LOOKUP_PRELOAD_CANDIDATE";
  public static final String CODE_HIGH_TARGET_READ_RATIO = "HIGH_TARGET_READ_RATIO";
  public static final String CODE_SORT_MEMORY_RISK = "SORT_MEMORY_RISK";
  public static final String CODE_BULK_LOAD_USED = "BULK_LOAD_USED";
  public static final String CODE_HIGH_TRANSFORM_DURATION = "HIGH_TRANSFORM_DURATION";

  public static final long DEFAULT_LOOKUP_RATIO_THRESHOLD = 100L;
  public static final long DEFAULT_TARGET_READ_RATIO_THRESHOLD = 10L;
  public static final long DEFAULT_SORT_ROWS_RISK_THRESHOLD = 500_000L;
  public static final long DEFAULT_HIGH_TRANSFORM_DURATION_MS = 60_000L;

  private static final Set<String> HIGH_TARGET_READ_TYPES =
      Set.of("hub", "satellite", "link", "scd2");

  private LoadRunInsightEngine() {}

  public static List<LoadRunInsight> evaluate(List<DvUpdateTableMetrics> pipelines) {
    return evaluate(pipelines, DEFAULT_LOOKUP_RATIO_THRESHOLD);
  }

  public static List<LoadRunInsight> evaluate(
      List<DvUpdateTableMetrics> pipelines, long lookupRatioThreshold) {
    return evaluate(
        pipelines,
        lookupRatioThreshold,
        DEFAULT_TARGET_READ_RATIO_THRESHOLD,
        DEFAULT_SORT_ROWS_RISK_THRESHOLD,
        DEFAULT_HIGH_TRANSFORM_DURATION_MS);
  }

  public static List<LoadRunInsight> evaluate(
      List<DvUpdateTableMetrics> pipelines,
      long lookupRatioThreshold,
      long targetReadRatioThreshold,
      long sortRowsRiskThreshold) {
    return evaluate(
        pipelines,
        lookupRatioThreshold,
        targetReadRatioThreshold,
        sortRowsRiskThreshold,
        DEFAULT_HIGH_TRANSFORM_DURATION_MS);
  }

  public static List<LoadRunInsight> evaluate(
      List<DvUpdateTableMetrics> pipelines,
      long lookupRatioThreshold,
      long targetReadRatioThreshold,
      long sortRowsRiskThreshold,
      long highTransformDurationMs) {
    if (pipelines == null || pipelines.isEmpty()) {
      return List.of();
    }
    List<LoadRunInsight> insights = new ArrayList<>();
    Map<String, Long> dimensionTargetReads = indexDimensionTargetReads(pipelines);
    insights.addAll(
        evaluateDimensionLookupPreloadCandidates(
            pipelines, dimensionTargetReads, lookupRatioThreshold));
    insights.addAll(evaluateHighTargetReadRatio(pipelines, targetReadRatioThreshold));
    insights.addAll(evaluateSortMemoryRisk(pipelines, sortRowsRiskThreshold));
    insights.addAll(evaluateBulkLoadUsed(pipelines));
    insights.addAll(evaluateHighTransformDuration(pipelines, highTransformDurationMs));
    return insights;
  }

  private static Map<String, Long> indexDimensionTargetReads(List<DvUpdateTableMetrics> pipelines) {
    Map<String, Long> readsByDimension = new HashMap<>();
    for (DvUpdateTableMetrics pipeline : pipelines) {
      if (pipeline == null || !"dimension".equals(pipeline.getTableType())) {
        continue;
      }
      long targetRead = pipeline.getTargetRowsRead();
      if (targetRead <= 0) {
        targetRead = sumTargetReadTransforms(pipeline);
      }
      if (targetRead > 0 && !Utils.isEmpty(pipeline.getTableName())) {
        readsByDimension.put(pipeline.getTableName(), targetRead);
      }
    }
    return readsByDimension;
  }

  private static long sumTargetReadTransforms(DvUpdateTableMetrics pipeline) {
    if (pipeline.getTransforms() == null) {
      return 0L;
    }
    long total = 0L;
    for (TransformRunMetrics transform : pipeline.getTransforms()) {
      if (transform == null
          || !GeneratedPipelineMetadataConstants.ROLE_TARGET_READ.equals(
              transform.getLogicalRole())) {
        continue;
      }
      total += transform.getRowsRead();
    }
    return total;
  }

  private static List<LoadRunInsight> evaluateDimensionLookupPreloadCandidates(
      List<DvUpdateTableMetrics> pipelines,
      Map<String, Long> dimensionTargetReads,
      long lookupRatioThreshold) {
    List<LoadRunInsight> insights = new ArrayList<>();
    for (DvUpdateTableMetrics pipeline : pipelines) {
      if (pipeline == null || pipeline.getTransforms() == null) {
        continue;
      }
      for (TransformRunMetrics transform : pipeline.getTransforms()) {
        if (transform == null
            || !GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP.equals(
                transform.getLogicalRole())) {
          continue;
        }
        if (!GeneratedPipelineMetadataConstants.LOOKUP_CACHE_DATABASE.equals(
            transform.getLookupCacheMode())) {
          continue;
        }
        long factRows = transform.getRowsRead();
        if (factRows <= 0) {
          continue;
        }
        String dimensionName = transform.getElementName();
        Long dimRows = dimensionTargetReads.get(dimensionName);
        if (dimRows == null || dimRows <= 0) {
          continue;
        }
        long ratio = factRows / dimRows;
        if (ratio < lookupRatioThreshold) {
          continue;
        }
        String factName =
            !Utils.isEmpty(transform.getParentElementName())
                ? transform.getParentElementName()
                : pipeline.getTableName();
        insights.add(
            LoadRunInsight.builder()
                .severity("info")
                .code(CODE_DIM_LOOKUP_PRELOAD_CANDIDATE)
                .elementName(factName)
                .relatedElementName(dimensionName)
                .message(
                    "Dimension "
                        + dimensionName
                        + " has ~"
                        + dimRows
                        + " rows; fact "
                        + factName
                        + " performed ~"
                        + factRows
                        + " lookups via "
                        + transform.getTransformName()
                        + ". Enable preloadLookupCache on the fact role.")
                .metricJson(
                    "{\"factRows\":"
                        + factRows
                        + ",\"dimRows\":"
                        + dimRows
                        + ",\"ratio\":"
                        + ratio
                        + ",\"transform\":\""
                        + escapeJson(transform.getTransformName())
                        + "\"}")
                .build());
      }
    }
    return insights;
  }

  private static List<LoadRunInsight> evaluateHighTargetReadRatio(
      List<DvUpdateTableMetrics> pipelines, long targetReadRatioThreshold) {
    List<LoadRunInsight> insights = new ArrayList<>();
    for (DvUpdateTableMetrics pipeline : pipelines) {
      if (pipeline == null || !isHighTargetReadCandidate(pipeline.getTableType())) {
        continue;
      }
      long sourceRows = pipeline.getSourceRowsRead();
      long targetRows = pipeline.getTargetRowsRead();
      if (sourceRows <= 0 || targetRows <= 0) {
        continue;
      }
      long ratio = targetRows / sourceRows;
      if (ratio < targetReadRatioThreshold) {
        continue;
      }
      insights.add(
          LoadRunInsight.builder()
              .severity("warning")
              .code(CODE_HIGH_TARGET_READ_RATIO)
              .elementName(pipeline.getTableName())
              .relatedElementName(pipeline.getSourceName())
              .message(
                  pipeline.getTableType()
                      + " "
                      + pipeline.getTableName()
                      + " re-read ~"
                      + targetRows
                      + " target rows vs ~"
                      + sourceRows
                      + " source rows (ratio "
                      + ratio
                      + "). Check CDC/filter path and target read scope.")
              .metricJson(
                  "{\"sourceRows\":"
                      + sourceRows
                      + ",\"targetRows\":"
                      + targetRows
                      + ",\"ratio\":"
                      + ratio
                      + ",\"pipeline\":\""
                      + escapeJson(pipeline.getPipelineName())
                      + "\"}")
              .build());
    }
    return insights;
  }

  private static boolean isHighTargetReadCandidate(String tableType) {
    return tableType != null && HIGH_TARGET_READ_TYPES.contains(tableType.toLowerCase());
  }

  private static List<LoadRunInsight> evaluateSortMemoryRisk(
      List<DvUpdateTableMetrics> pipelines, long sortRowsRiskThreshold) {
    List<LoadRunInsight> insights = new ArrayList<>();
    for (DvUpdateTableMetrics pipeline : pipelines) {
      if (pipeline == null || pipeline.getTransforms() == null) {
        continue;
      }
      for (TransformRunMetrics transform : pipeline.getTransforms()) {
        if (transform == null
            || !GeneratedPipelineMetadataConstants.ROLE_SORT.equals(transform.getLogicalRole())) {
          continue;
        }
        long rowsRead = transform.getRowsRead();
        long rowsRejected = transform.getRowsRejected();
        boolean largeSort = rowsRead >= sortRowsRiskThreshold;
        boolean rejectedRows = rowsRejected > 0;
        if (!largeSort && !rejectedRows) {
          continue;
        }
        String elementName =
            !Utils.isEmpty(transform.getElementName())
                ? transform.getElementName()
                : pipeline.getTableName();
        insights.add(
            LoadRunInsight.builder()
                .severity(largeSort ? "warning" : "info")
                .code(CODE_SORT_MEMORY_RISK)
                .elementName(elementName)
                .relatedElementName(pipeline.getTableName())
                .message(
                    "Sort transform "
                        + transform.getTransformName()
                        + " processed ~"
                        + rowsRead
                        + " rows"
                        + (rowsRejected > 0 ? " with " + rowsRejected + " rejected rows" : "")
                        + ". Review sortRowsSize and performance-tuning guidance.")
                .metricJson(
                    "{\"rowsRead\":"
                        + rowsRead
                        + ",\"rowsRejected\":"
                        + rowsRejected
                        + ",\"transform\":\""
                        + escapeJson(transform.getTransformName())
                        + "\",\"pipeline\":\""
                        + escapeJson(pipeline.getPipelineName())
                        + "\"}")
                .build());
      }
    }
    return insights;
  }

  private static List<LoadRunInsight> evaluateBulkLoadUsed(List<DvUpdateTableMetrics> pipelines) {
    List<LoadRunInsight> insights = new ArrayList<>();
    for (DvUpdateTableMetrics pipeline : pipelines) {
      if (pipeline == null || pipeline.getTransforms() == null) {
        continue;
      }
      for (TransformRunMetrics transform : pipeline.getTransforms()) {
        if (transform == null
            || !GeneratedPipelineMetadataConstants.ROLE_WRITE_TARGET.equals(
                transform.getLogicalRole())) {
          continue;
        }
        if (!isBulkLoaderPlugin(transform.getPluginId())) {
          continue;
        }
        String elementName =
            !Utils.isEmpty(transform.getElementName())
                ? transform.getElementName()
                : pipeline.getTableName();
        insights.add(
            LoadRunInsight.builder()
                .severity(LoadRunInsightSupport.SEVERITY_NOTE)
                .code(CODE_BULK_LOAD_USED)
                .elementName(elementName)
                .relatedElementName(transform.getTransformName())
                .message(
                    "Bulk load path used for "
                        + elementName
                        + " via "
                        + transform.getTransformName()
                        + " ("
                        + transform.getPluginId()
                        + "). Note capacity planning for native bulk loaders.")
                .metricJson(
                    "{\"pluginId\":\""
                        + escapeJson(transform.getPluginId())
                        + "\",\"rowsWritten\":"
                        + transform.getRowsWritten()
                        + ",\"transform\":\""
                        + escapeJson(transform.getTransformName())
                        + "\",\"pipeline\":\""
                        + escapeJson(pipeline.getPipelineName())
                        + "\"}")
                .build());
      }
    }
    return insights;
  }

  private static List<LoadRunInsight> evaluateHighTransformDuration(
      List<DvUpdateTableMetrics> pipelines, long highTransformDurationMs) {
    List<LoadRunInsight> insights = new ArrayList<>();
    for (DvUpdateTableMetrics pipeline : pipelines) {
      if (pipeline == null || pipeline.getTransforms() == null) {
        continue;
      }
      for (TransformRunMetrics transform : pipeline.getTransforms()) {
        if (transform == null || transform.getDurationMs() < highTransformDurationMs) {
          continue;
        }
        String elementName =
            !Utils.isEmpty(transform.getElementName())
                ? transform.getElementName()
                : pipeline.getTableName();
        insights.add(
            LoadRunInsight.builder()
                .severity("info")
                .code(CODE_HIGH_TRANSFORM_DURATION)
                .elementName(elementName)
                .relatedElementName(transform.getTransformName())
                .message(
                    "Transform "
                        + transform.getTransformName()
                        + " in "
                        + pipeline.getPipelineName()
                        + " ran for ~"
                        + transform.getDurationMs()
                        + " ms. Inspect upstream volume, sort/merge steps, and parallelism.")
                .metricJson(
                    "{\"durationMs\":"
                        + transform.getDurationMs()
                        + ",\"logicalRole\":\""
                        + escapeJson(transform.getLogicalRole())
                        + "\",\"transform\":\""
                        + escapeJson(transform.getTransformName())
                        + "\",\"pipeline\":\""
                        + escapeJson(pipeline.getPipelineName())
                        + "\"}")
                .build());
      }
    }
    return insights;
  }

  static boolean isBulkLoaderPlugin(String pluginId) {
    if (Utils.isEmpty(pluginId)) {
      return false;
    }
    return pluginId.equals(DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID)
        || pluginId.equals(DvBulkLoadPluginSupport.PG_BULK_LOADER_ID)
        || pluginId.equals(DvBulkLoadPluginSupport.ORA_BULK_LOADER_ID)
        || pluginId.equals(DvBulkLoadPluginSupport.SNOWFLAKE_BULK_LOADER_ID)
        || pluginId.equals(DvBulkLoadPluginSupport.MONETDB_BULK_LOADER_ID)
        || pluginId.equals(DvBulkLoadPluginSupport.VERTICA_BULK_LOADER_ID)
        || pluginId.equals(DvBulkLoadPluginSupport.DORIS_BULK_LOADER_ID)
        || pluginId.contains("BulkLoader");
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
