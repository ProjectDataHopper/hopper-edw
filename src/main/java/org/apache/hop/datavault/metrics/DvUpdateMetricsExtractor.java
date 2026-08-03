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
package org.apache.hop.datavault.metrics;

import java.util.Date;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IEngineMetric;
import org.apache.hop.pipeline.engine.IPipelineEngine;

/**
 * Reads per-transform engine metrics from a completed update pipeline and maps them to source read,
 * target read, and target insert counts.
 */
public final class DvUpdateMetricsExtractor {

  private DvUpdateMetricsExtractor() {}

  public static DvUpdateTableMetrics extract(
      IPipelineEngine<PipelineMeta> engine,
      String runId,
      String modelName,
      DvUpdateMetricsParser.ParsedPipeline identity) {
    EngineMetrics metrics = engine.getEngineMetrics();
    String tableName = identity.tableName();
    long sourceRowsRead =
        sumMetric(metrics, name -> isSourceTransform(name), Pipeline.METRIC_INPUT);
    long targetRowsRead =
        sumMetric(metrics, name -> isTargetTransform(name, tableName), Pipeline.METRIC_INPUT);
    long targetRowsInserted =
        sumMetric(metrics, name -> isWriteTransform(name, tableName), Pipeline.METRIC_OUTPUT);

    Date executionStartDate = engine.getExecutionStartDate();
    Date executionEndDate = engine.getExecutionEndDate();
    return DvUpdateTableMetrics.builder()
        .runId(runId)
        .modelName(modelName)
        .pipelineName(engine.getPipelineMeta().getName())
        .tableType(identity.tableType())
        .tableName(tableName)
        .sourceName(identity.sourceName())
        .sourceRowsRead(sourceRowsRead)
        .targetRowsRead(targetRowsRead)
        .targetRowsInserted(targetRowsInserted)
        .errors(engine.getErrors())
        .success(engine.getErrors() == 0)
        .executionStartDate(executionStartDate)
        .executionEndDate(executionEndDate)
        .durationMs(DvUpdateTableMetrics.resolveDurationMs(executionStartDate, executionEndDate))
        .build();
  }

  private static long sumMetric(
      EngineMetrics metrics,
      java.util.function.Predicate<String> nameFilter,
      IEngineMetric metric) {
    long total = 0L;
    for (IEngineComponent component : metrics.getComponents()) {
      if (component == null || !nameFilter.test(component.getName())) {
        continue;
      }
      Long value = metrics.getComponentMetric(component, metric);
      if (value != null) {
        total += value;
      }
    }
    return total;
  }

  private static boolean isSourceTransform(String name) {
    if (name == null) {
      return false;
    }
    return name.startsWith(DvUpdateMetricsConstants.SOURCE_TRANSFORM_PREFIX)
        || name.startsWith(DvUpdateMetricsConstants.DIMENSIONAL_SOURCE_TRANSFORM_PREFIX);
  }

  private static boolean isTargetTransform(String name, String tableName) {
    if (name == null || tableName == null) {
      return false;
    }
    if (name.equals(DvUpdateMetricsConstants.TARGET_TRANSFORM_PREFIX + tableName)) {
      return true;
    }
    if (name.equals(DvUpdateMetricsConstants.DIMENSIONAL_TARGET_TRANSFORM_PREFIX + tableName)) {
      return true;
    }
    if (name.equals(DvUpdateMetricsConstants.STS_TARGET_TRANSFORM_PREFIX + tableName)) {
      return true;
    }
    if (name.startsWith(DvUpdateMetricsConstants.TARGET_TRANSFORM_DB_PREFIX)) {
      return name.endsWith("." + tableName);
    }
    return false;
  }

  private static boolean isWriteTransform(String name, String tableName) {
    if (name == null || tableName == null) {
      return false;
    }
    return name.equals(DvUpdateMetricsConstants.WRITE_TRANSFORM_PREFIX + tableName)
        || name.equals(DvUpdateMetricsConstants.BULK_WRITE_TRANSFORM_PREFIX + tableName)
        || name.equals(DvUpdateMetricsConstants.STAGING_WRITE_TRANSFORM_PREFIX + tableName);
  }
}
