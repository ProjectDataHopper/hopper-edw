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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.pipeline.PipelineMeta;
import org.junit.jupiter.api.Test;

class LoadRunMetricsPipelineSupportTest {

  @Test
  void enablesTransformPerformanceCaptureWhenMetricsFolderConfigured() {
    PipelineMeta pipeline = new PipelineMeta();
    pipeline.setName("hub_customer-CRM-customer");

    LoadRunMetricsPipelineSupport.enableTransformPerformanceCapture(List.of(pipeline));

    assertTrue(pipeline.isCapturingTransformPerformanceSnapShots());
  }

  @Test
  void detectsMetricsCollectionFromFolderOrPublishContext() {
    assertTrue(LoadRunMetricsPipelineSupport.isMetricsCollectionEnabled("/tmp/metrics", null));
    assertTrue(
        LoadRunMetricsPipelineSupport.isMetricsCollectionEnabled(
            "",
            new DvUpdateMetricsCollector.LoadRunPublishContext(
                "local-catalog",
                "OPS",
                "dv_ops",
                "wf",
                "dv",
                true,
                true,
                true,
                100,
                LoadRunInsightEngine.DEFAULT_TARGET_READ_RATIO_THRESHOLD,
                LoadRunInsightEngine.DEFAULT_SORT_ROWS_RISK_THRESHOLD,
                LoadRunInsightEngine.DEFAULT_HIGH_TRANSFORM_DURATION_MS,
                null)));
    assertFalse(LoadRunMetricsPipelineSupport.isMetricsCollectionEnabled("", null));
  }
}
