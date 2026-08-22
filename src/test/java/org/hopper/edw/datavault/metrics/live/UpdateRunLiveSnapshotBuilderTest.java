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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateRunLiveSnapshotBuilderTest {

  @Test
  void usesFallbackElementWhenNoPipelineMetrics() {
    UpdateRunLiveRunContext context =
        UpdateRunLiveRunContext.builder()
            .metricsRunId("run-1")
            .modelName("retail-f-orders")
            .workflowFilename("/tmp/update-retail.hwf")
            .workflowName("update-retail")
            .actionName("retail-f-orders.hdm")
            .startedAt(new Date())
            .build();

    UpdateRunLiveSnapshot snapshot =
        UpdateRunLiveSnapshotBuilder.build(
            context, new UpdateRunLiveStallDetector(), List.of(), false, 0L, "f_orders");

    assertEquals("f_orders", snapshot.getCurrentElementName());
    assertEquals(UpdateRunLiveState.RUNNING, snapshot.getOverallState());
  }

  @Test
  void keepsRunningWhenStagingSinkProgressesDespiteIdleSideBranches() {
    UpdateRunLiveRunContext context =
        UpdateRunLiveRunContext.builder()
            .metricsRunId("run-2")
            .modelName("retail-f-orders")
            .workflowFilename("/tmp/update-retail.hwf")
            .workflowName("update-retail")
            .actionName("retail-f-orders.hdm")
            .startedAt(new Date())
            .build();
    UpdateRunLiveStallDetector detector = new UpdateRunLiveStallDetector(60_000L);
    long threshold = UpdateRunLiveStallDetector.DEFAULT_STALL_THRESHOLD_MS;

    TransformLiveMetrics source =
        TransformLiveMetrics.builder()
            .transformName("source_f_orders")
            .pluginId("TableInput")
            .rowsRead(300_000L)
            .rowsWritten(0L)
            .bufferOut(10_000L)
            .running(true)
            .build();
    detector.annotateTransforms("0001-dm-fact-f_orders", List.of(source), 0L);
    TransformLiveMetrics stalledJunk =
        TransformLiveMetrics.builder()
            .transformName("junk_d_orders_junk_key")
            .pluginId("JunkDimension")
            .rowsRead(1L)
            .rowsWritten(1L)
            .bufferIn(10_000L)
            .running(true)
            .build();
    detector.annotateTransforms("0001-dm-fact-f_orders", List.of(stalledJunk), 0L);
    TransformLiveMetrics activeSink =
        TransformLiveMetrics.builder()
            .transformName("stage_to_f_orders")
            .pluginId("TextFileOutput")
            .rowsRead(0L)
            .rowsWritten(100_000L)
            .running(true)
            .build();
    detector.annotateTransforms("0001-dm-fact-f_orders", List.of(activeSink), 0L);

    TransformLiveMetrics progressedSource =
        TransformLiveMetrics.builder()
            .transformName("source_f_orders")
            .pluginId("TableInput")
            .rowsRead(600_261L)
            .rowsWritten(0L)
            .bufferOut(10_000L)
            .running(true)
            .build();
    TransformLiveMetrics quietJunk =
        TransformLiveMetrics.builder()
            .transformName("junk_d_orders_junk_key")
            .pluginId("JunkDimension")
            .rowsRead(1L)
            .rowsWritten(1L)
            .bufferIn(10_000L)
            .running(true)
            .build();
    TransformLiveMetrics progressedSink =
        TransformLiveMetrics.builder()
            .transformName("stage_to_f_orders")
            .pluginId("TextFileOutput")
            .rowsRead(0L)
            .rowsWritten(147_567L)
            .running(true)
            .build();

    PipelineLiveMetrics pipeline =
        PipelineLiveMetrics.builder()
            .pipelineName("0001-dm-fact-f_orders")
            .elementType("fact")
            .elementName("f_orders")
            .state(UpdateRunLiveState.RUNNING)
            .transforms(List.of(progressedSource, quietJunk, progressedSink))
            .build();

    UpdateRunLiveSnapshot snapshot =
        UpdateRunLiveSnapshotBuilder.build(
            context, detector, List.of(pipeline), false, 0L, "f_orders");

    assertEquals(UpdateRunLiveState.RUNNING, snapshot.getOverallState());
    assertNotEquals(UpdateRunLiveState.STALLED, snapshot.getPipelines().get(0).getState());

    List<TransformLiveMetrics> annotated =
        detector.annotateTransforms(
            "0001-dm-fact-f_orders", List.of(quietJunk), threshold + 80_000L);
    assertEquals(140L, annotated.get(0).getSecondsSinceLastProgress());
    assertFalse(detector.isPipelineStalled(List.of(quietJunk, progressedSink)));
  }
}
