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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.junit.jupiter.api.Test;

class LoadRunInsightEngineTest {

  @Test
  void emitsDimLookupPreloadCandidateWhenRatioExceedsThreshold() {
    DvUpdateTableMetrics factPipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("dm-fact-f_orders")
            .tableType("fact")
            .tableName("f_orders")
            .transform(
                TransformRunMetrics.builder()
                    .transformName("lookup_d_customer")
                    .logicalRole(GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP)
                    .elementType("dimension")
                    .elementName("d_customer")
                    .parentElementName("f_orders")
                    .lookupCacheMode(GeneratedPipelineMetadataConstants.LOOKUP_CACHE_DATABASE)
                    .rowsRead(5_000_000L)
                    .build())
            .build();

    DvUpdateTableMetrics dimensionPipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("dm-dim-d_customer")
            .tableType("dimension")
            .tableName("d_customer")
            .targetRowsRead(1000L)
            .build();

    List<LoadRunInsight> insights =
        LoadRunInsightEngine.evaluate(List.of(factPipeline, dimensionPipeline));

    assertEquals(1, insights.size());
    assertEquals(LoadRunInsightEngine.CODE_DIM_LOOKUP_PRELOAD_CANDIDATE, insights.get(0).getCode());
    assertEquals("f_orders", insights.get(0).getElementName());
    assertEquals("d_customer", insights.get(0).getRelatedElementName());
    assertTrue(insights.get(0).getMessage().contains("preloadLookupCache"));
  }

  @Test
  void skipsPreloadCandidateWhenCacheAlreadyPreloaded() {
    DvUpdateTableMetrics factPipeline =
        DvUpdateTableMetrics.builder()
            .tableType("fact")
            .tableName("f_orders")
            .transform(
                TransformRunMetrics.builder()
                    .logicalRole(GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP)
                    .elementName("d_customer")
                    .parentElementName("f_orders")
                    .lookupCacheMode(GeneratedPipelineMetadataConstants.LOOKUP_CACHE_PRELOAD)
                    .rowsRead(5_000_000L)
                    .build())
            .build();

    DvUpdateTableMetrics dimensionPipeline =
        DvUpdateTableMetrics.builder()
            .tableType("dimension")
            .tableName("d_customer")
            .targetRowsRead(1000L)
            .build();

    assertTrue(LoadRunInsightEngine.evaluate(List.of(factPipeline, dimensionPipeline)).isEmpty());
  }

  @Test
  void emitsHighTargetReadRatioForHubPipeline() {
    DvUpdateTableMetrics hubPipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("hub_customer-CRM-customer")
            .tableType("hub")
            .tableName("hub_customer")
            .sourceName("CRM-customer")
            .sourceRowsRead(1000L)
            .targetRowsRead(50_000L)
            .build();

    List<LoadRunInsight> insights = LoadRunInsightEngine.evaluate(List.of(hubPipeline));

    assertEquals(1, insights.size());
    assertEquals(LoadRunInsightEngine.CODE_HIGH_TARGET_READ_RATIO, insights.get(0).getCode());
    assertEquals("hub_customer", insights.get(0).getElementName());
    assertTrue(insights.get(0).getMessage().contains("CDC/filter"));
  }

  @Test
  void emitsSortMemoryRiskForLargeSortTransform() {
    DvUpdateTableMetrics pipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("sat_customer_details-CRM-customer")
            .tableType("satellite")
            .tableName("sat_customer_details")
            .transform(
                TransformRunMetrics.builder()
                    .transformName("sort_changes")
                    .logicalRole(GeneratedPipelineMetadataConstants.ROLE_SORT)
                    .elementName("sat_customer_details")
                    .rowsRead(1_000_000L)
                    .build())
            .build();

    List<LoadRunInsight> insights = LoadRunInsightEngine.evaluate(List.of(pipeline));

    assertEquals(1, insights.size());
    assertEquals(LoadRunInsightEngine.CODE_SORT_MEMORY_RISK, insights.get(0).getCode());
    assertTrue(insights.get(0).getMessage().contains("sortRowsSize"));
  }

  @Test
  void emitsHighTransformDurationWhenDurationExceedsThreshold() {
    DvUpdateTableMetrics pipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("sat_customer_details-CRM-customer")
            .tableType("satellite")
            .tableName("sat_customer_details")
            .transform(
                TransformRunMetrics.builder()
                    .transformName("merge_changes")
                    .logicalRole(GeneratedPipelineMetadataConstants.ROLE_CDC_MERGE)
                    .elementName("sat_customer_details")
                    .durationMs(120_000L)
                    .build())
            .build();

    List<LoadRunInsight> insights = LoadRunInsightEngine.evaluate(List.of(pipeline));

    assertEquals(1, insights.size());
    assertEquals(LoadRunInsightEngine.CODE_HIGH_TRANSFORM_DURATION, insights.get(0).getCode());
    assertEquals("sat_customer_details", insights.get(0).getElementName());
    assertEquals("merge_changes", insights.get(0).getRelatedElementName());
    assertTrue(insights.get(0).getMessage().contains("120000"));
  }

  @Test
  void suppressesHighTransformDurationWhenCustomThresholdIsHigher() {
    DvUpdateTableMetrics pipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("sat_customer_details-CRM-customer")
            .tableType("satellite")
            .tableName("sat_customer_details")
            .transform(
                TransformRunMetrics.builder()
                    .transformName("merge_changes")
                    .logicalRole(GeneratedPipelineMetadataConstants.ROLE_CDC_MERGE)
                    .elementName("sat_customer_details")
                    .durationMs(120_000L)
                    .build())
            .build();

    List<LoadRunInsight> insights =
        LoadRunInsightEngine.evaluate(
            List.of(pipeline),
            LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_TARGET_READ_RATIO_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_SORT_ROWS_RISK_THRESHOLD,
            300_000L);

    assertTrue(insights.isEmpty());
  }

  @Test
  void respectsCustomTargetReadRatioThreshold() {
    DvUpdateTableMetrics hubPipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("hub_customer-CRM-customer")
            .tableType("hub")
            .tableName("hub_customer")
            .sourceName("CRM-customer")
            .sourceRowsRead(1000L)
            .targetRowsRead(15_000L)
            .build();

    List<LoadRunInsight> insights =
        LoadRunInsightEngine.evaluate(
            List.of(hubPipeline),
            LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD,
            20L,
            LoadRunInsightEngine.DEFAULT_SORT_ROWS_RISK_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_HIGH_TRANSFORM_DURATION_MS);

    assertTrue(insights.isEmpty());
  }

  @Test
  void respectsCustomSortRowsRiskThreshold() {
    DvUpdateTableMetrics pipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("sat_customer_details-CRM-customer")
            .tableType("satellite")
            .tableName("sat_customer_details")
            .transform(
                TransformRunMetrics.builder()
                    .transformName("sort_changes")
                    .logicalRole(GeneratedPipelineMetadataConstants.ROLE_SORT)
                    .elementName("sat_customer_details")
                    .rowsRead(600_000L)
                    .build())
            .build();

    List<LoadRunInsight> insights =
        LoadRunInsightEngine.evaluate(
            List.of(pipeline),
            LoadRunInsightEngine.DEFAULT_LOOKUP_RATIO_THRESHOLD,
            LoadRunInsightEngine.DEFAULT_TARGET_READ_RATIO_THRESHOLD,
            1_000_000L,
            LoadRunInsightEngine.DEFAULT_HIGH_TRANSFORM_DURATION_MS);

    assertTrue(insights.isEmpty());
  }

  @Test
  void emitsBulkLoadUsedForBulkWriteTransform() {
    DvUpdateTableMetrics pipeline =
        DvUpdateTableMetrics.builder()
            .pipelineName("hub_customer-CRM-customer")
            .tableType("hub")
            .tableName("hub_customer")
            .transform(
                TransformRunMetrics.builder()
                    .transformName("bulk_load_to_hub_customer")
                    .pluginId(DvBulkLoadPluginSupport.PG_BULK_LOADER_ID)
                    .logicalRole(GeneratedPipelineMetadataConstants.ROLE_WRITE_TARGET)
                    .elementName("hub_customer")
                    .rowsWritten(10_000L)
                    .build())
            .build();

    List<LoadRunInsight> insights = LoadRunInsightEngine.evaluate(List.of(pipeline));

    assertEquals(1, insights.size());
    assertEquals(LoadRunInsightEngine.CODE_BULK_LOAD_USED, insights.get(0).getCode());
    assertEquals(LoadRunInsightSupport.SEVERITY_NOTE, insights.get(0).getSeverity());
    assertEquals("hub_customer", insights.get(0).getElementName());
    assertTrue(insights.get(0).getMessage().contains("Bulk load path"));
    assertTrue(LoadRunInsightSupport.isReportable(insights.get(0)) == false);
  }
}
