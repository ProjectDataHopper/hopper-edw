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
package org.hopper.edw.datavault.metrics.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateRunLiveStallDetectorTest {

  @Test
  void detectsStallAfterQuietPeriod() {
    UpdateRunLiveStallDetector detector =
        new UpdateRunLiveStallDetector(UpdateRunLiveStallDetector.DEFAULT_STALL_THRESHOLD_MS);
    TransformLiveMetrics running =
        TransformLiveMetrics.builder()
            .transformName("source customer")
            .pluginId("TableInput")
            .rowsRead(0L)
            .rowsWritten(0L)
            .bufferIn(0L)
            .bufferOut(0L)
            .running(true)
            .build();

    List<TransformLiveMetrics> first =
        detector.annotateTransforms("hub-customer", List.of(running), 0L);
    assertEquals(0L, first.get(0).getSecondsSinceLastProgress());

    List<TransformLiveMetrics> stalled =
        detector.annotateTransforms(
            "hub-customer",
            List.of(running),
            UpdateRunLiveStallDetector.DEFAULT_STALL_THRESHOLD_MS);
    assertTrue(detector.isStalled(stalled.get(0)));
    assertEquals(60L, stalled.get(0).getSecondsSinceLastProgress());
  }

  @Test
  void progressResetsStallTimer() {
    UpdateRunLiveStallDetector detector = new UpdateRunLiveStallDetector(60_000L);
    TransformLiveMetrics initial =
        TransformLiveMetrics.builder()
            .transformName("sort rows")
            .pluginId("SortRows")
            .rowsRead(0L)
            .rowsWritten(0L)
            .running(true)
            .build();
    detector.annotateTransforms("sat-order", List.of(initial), 0L);

    TransformLiveMetrics progressed =
        TransformLiveMetrics.builder()
            .transformName("sort rows")
            .pluginId("SortRows")
            .rowsRead(10L)
            .rowsWritten(0L)
            .running(true)
            .build();
    List<TransformLiveMetrics> annotated =
        detector.annotateTransforms("sat-order", List.of(progressed), 90_000L);
    assertFalse(detector.isStalled(annotated.get(0)));
  }

  @Test
  void finishedTransformIsNotStalled() {
    UpdateRunLiveStallDetector detector = new UpdateRunLiveStallDetector(60_000L);
    TransformLiveMetrics finished =
        TransformLiveMetrics.builder()
            .transformName("write_to_hub")
            .pluginId("TableOutput")
            .rowsRead(100L)
            .rowsWritten(100L)
            .running(false)
            .secondsSinceLastProgress(120L)
            .build();
    assertFalse(detector.isStalled(finished));
  }

  @Test
  void pipelineNotStalledWhenAnyRunningTransformStillProgresses() {
    UpdateRunLiveStallDetector detector = new UpdateRunLiveStallDetector(60_000L);
    TransformLiveMetrics stalledBranch =
        TransformLiveMetrics.builder()
            .transformName("junk_d_orders_junk_key")
            .pluginId("JunkDimension")
            .rowsRead(1L)
            .rowsWritten(1L)
            .running(true)
            .secondsSinceLastProgress(140L)
            .build();
    TransformLiveMetrics activeSink =
        TransformLiveMetrics.builder()
            .transformName("stage_to_f_orders")
            .pluginId("TextFileOutput")
            .rowsRead(0L)
            .rowsWritten(147_567L)
            .running(true)
            .secondsSinceLastProgress(0L)
            .build();

    assertFalse(detector.isPipelineStalled(List.of(stalledBranch, activeSink)));
  }

  @Test
  void pipelineStalledWhenEveryRunningTransformIsQuiet() {
    UpdateRunLiveStallDetector detector = new UpdateRunLiveStallDetector(60_000L);
    TransformLiveMetrics stalledSource =
        TransformLiveMetrics.builder()
            .transformName("source_f_orders")
            .pluginId("TableInput")
            .rowsRead(0L)
            .rowsWritten(0L)
            .running(true)
            .secondsSinceLastProgress(90L)
            .build();
    TransformLiveMetrics stalledLookup =
        TransformLiveMetrics.builder()
            .transformName("lookup_d_customer")
            .pluginId("DimensionLookup")
            .rowsRead(0L)
            .rowsWritten(0L)
            .running(true)
            .secondsSinceLastProgress(75L)
            .build();

    assertTrue(detector.isPipelineStalled(List.of(stalledSource, stalledLookup)));
  }

  @Test
  void pipelineNotStalledWhenNoTransformsAreRunning() {
    UpdateRunLiveStallDetector detector = new UpdateRunLiveStallDetector(60_000L);
    TransformLiveMetrics finished =
        TransformLiveMetrics.builder()
            .transformName("stage_to_f_orders")
            .pluginId("TextFileOutput")
            .rowsRead(0L)
            .rowsWritten(600_261L)
            .running(false)
            .secondsSinceLastProgress(300L)
            .build();

    assertFalse(detector.isPipelineStalled(List.of(finished)));
  }
}
