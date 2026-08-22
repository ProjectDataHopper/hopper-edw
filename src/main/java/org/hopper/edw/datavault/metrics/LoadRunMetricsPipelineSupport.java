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

import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.PipelineMeta;

/** Applies pipeline-level settings required for load-run transform metrics collection. */
public final class LoadRunMetricsPipelineSupport {

  private LoadRunMetricsPipelineSupport() {}

  public static boolean isMetricsCollectionEnabled(
      String metricsOutputFolder, DvUpdateMetricsCollector.LoadRunPublishContext publishContext) {
    if (!Utils.isEmpty(metricsOutputFolder)) {
      return true;
    }
    return publishContext != null;
  }

  public static void enableTransformPerformanceCapture(List<PipelineMeta> pipelines) {
    if (pipelines == null) {
      return;
    }
    for (PipelineMeta pipeline : pipelines) {
      enableTransformPerformanceCapture(pipeline);
    }
  }

  public static void enableTransformPerformanceCapture(PipelineMeta pipeline) {
    if (pipeline == null) {
      return;
    }
    pipeline.setCapturingTransformPerformanceSnapShots(true);
  }
}
