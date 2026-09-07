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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.EngineComponent;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileOutputMeta;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetadataMetricsResolverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void extractsWriteMetricsFromEveryParallelStagingCopy() {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("dm-fact-f_orders");
    TransformMeta write =
        new TransformMeta("TextFileOutput", "stage_to_f_orders", new TextFileOutputMeta());
    GeneratedPipelineMetadataSupport.stampWriteTarget(
        write, "fact", "fact_orders", "f_orders", "vault");
    pipelineMeta.addTransform(write);

    EngineMetrics metrics = new EngineMetrics();
    addCopy(metrics, "stage_to_f_orders", 0, 1_000L, 1_000L, 120L);
    addCopy(metrics, "stage_to_f_orders", 1, 1_200L, 1_200L, 250L);
    addCopy(metrics, "stage_to_f_orders", 2, 800L, 800L, 180L);

    List<TransformRunMetrics> transforms =
        MetadataMetricsResolver.extractTransformMetrics(pipelineMeta, metrics);

    assertEquals(1, transforms.size());
    TransformRunMetrics writeMetrics = transforms.get(0);
    assertEquals("stage_to_f_orders", writeMetrics.getTransformName());
    assertEquals(3_000L, writeMetrics.getRowsRead());
    assertEquals(3_000L, writeMetrics.getRowsWritten());
    assertEquals(250L, writeMetrics.getDurationMs());

    MetadataMetricsResolver.AggregatedPipelineTotals totals =
        MetadataMetricsResolver.aggregateRoleTotals(transforms);
    assertEquals(3_000L, totals.targetRowsInserted());
  }

  @Test
  void aggregatesSourceRowsAcrossMultipleSourceReadTransforms() {
    List<TransformRunMetrics> transforms =
        List.of(
            TransformRunMetrics.builder()
                .transformName("read_sat_customer")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_SOURCE_READ)
                .rowsRead(10_000L)
                .build(),
            TransformRunMetrics.builder()
                .transformName("read_sat_customer_demo")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_SOURCE_READ)
                .rowsRead(8_000L)
                .build(),
            TransformRunMetrics.builder()
                .transformName("write_to_customer_360_bv")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_WRITE_TARGET)
                .rowsWritten(40_000L)
                .build());

    MetadataMetricsResolver.AggregatedPipelineTotals totals =
        MetadataMetricsResolver.aggregateRoleTotals(transforms);

    assertEquals(18_000L, totals.sourceRowsRead());
    assertEquals(40_000L, totals.targetRowsInserted());
  }

  @Test
  void sumTableInputRowsReadAggregatesUnstampedSatelliteInputs() {
    List<TransformRunMetrics> transforms =
        List.of(
            TransformRunMetrics.builder()
                .transformName("read_sat_customer")
                .pluginId("TableInput")
                .rowsRead(10_000L)
                .build(),
            TransformRunMetrics.builder()
                .transformName("read_sat_customer_demo")
                .pluginId("TableInput")
                .rowsRead(8_000L)
                .build(),
            TransformRunMetrics.builder()
                .transformName("source_sat_customer_prefs")
                .pluginId("Constant")
                .rowsRead(99L)
                .build());

    assertEquals(18_000L, MetadataMetricsResolver.sumTableInputRowsRead(transforms));
  }

  @Test
  void aggregatesPipelineTotalsFromStampedTransformRoles() {
    List<TransformRunMetrics> transforms =
        List.of(
            TransformRunMetrics.builder()
                .transformName("source_f_orders")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_SOURCE_READ)
                .rowsRead(1000L)
                .build(),
            TransformRunMetrics.builder()
                .transformName("lookup_d_customer")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP)
                .rowsRead(5_000_000L)
                .build(),
            TransformRunMetrics.builder()
                .transformName("write_to_f_orders")
                .logicalRole(GeneratedPipelineMetadataConstants.ROLE_WRITE_TARGET)
                .rowsWritten(1000L)
                .build());

    MetadataMetricsResolver.AggregatedPipelineTotals totals =
        MetadataMetricsResolver.aggregateRoleTotals(transforms);

    assertEquals(1000L, totals.sourceRowsRead());
    assertEquals(0L, totals.targetRowsRead());
    assertEquals(1000L, totals.targetRowsInserted());
  }

  @Test
  void nameHeuristicExtractorStillHandlesBulkAndStagingWrites() {
    DvUpdateMetricsParser.ParsedPipeline identity =
        new DvUpdateMetricsParser.ParsedPipeline("hub", "customer", "crm");
    assertEquals("hub", identity.tableType());
    assertEquals("customer", identity.tableName());
    assertEquals(
        "bulk_load_to_customer", DvUpdateMetricsConstants.BULK_WRITE_TRANSFORM_PREFIX + "customer");
    assertEquals(
        "stage_to_customer", DvUpdateMetricsConstants.STAGING_WRITE_TRANSFORM_PREFIX + "customer");
  }

  private static void addCopy(
      EngineMetrics metrics,
      String transformName,
      int copyNr,
      long rowsRead,
      long rowsWritten,
      long durationMs) {
    EngineComponent component = new EngineComponent(transformName, copyNr);
    component.setExecutionDuration(durationMs);
    metrics.addComponent(component);
    metrics.setComponentMetric(component, Pipeline.METRIC_INPUT, rowsRead);
    metrics.setComponentMetric(component, Pipeline.METRIC_OUTPUT, rowsWritten);
  }
}
