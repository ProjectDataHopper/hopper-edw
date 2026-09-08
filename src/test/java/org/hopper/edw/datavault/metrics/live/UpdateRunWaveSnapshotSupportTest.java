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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.apache.hop.core.Result;
import org.junit.jupiter.api.Test;

class UpdateRunWaveSnapshotSupportTest {

  @Test
  void tracksValidationThenModelProgressAndStall() {
    Date started = new Date(1_700_000_000_000L);
    UpdateRunWaveSnapshot wave =
        UpdateRunWaveSnapshotSupport.initial(
            "wave-1",
            "retail-sources",
            "/tmp/update.hwf",
            "update",
            "Update resource definition group",
            null,
            List.of(
                pending("DATA_VAULT", "models/retail-360.hdv"),
                pending("DIMENSIONAL", "models/retail-360.hdm")),
            started);

    wave = UpdateRunWaveSnapshotSupport.markValidating(wave, 2);
    assertEquals(UpdateRunWavePhase.VALIDATING, wave.getPhase());
    assertTrue(wave.getTooltipText().contains("2"));

    wave = UpdateRunWaveSnapshotSupport.markUpdating(wave);
    wave = UpdateRunWaveSnapshotSupport.markModelStarted(wave, "models/retail-360.hdv");
    assertEquals(UpdateRunLiveState.RUNNING, wave.getModels().get(0).getState());
    assertTrue(wave.getTooltipText().contains("1/2"));

    UpdateRunLiveSnapshot child =
        UpdateRunLiveSnapshot.builder()
            .metricsRunId("run-1")
            .waveId("wave-1")
            .modelName("retail-360")
            .modelFilename("models/retail-360.hdv")
            .overallState(UpdateRunLiveState.STALLED)
            .currentElementName("sat_customer")
            .updatedAt(new Date())
            .primaryBottleneck(UpdateRunLiveBottleneck.builder().message("Sort Rows idle").build())
            .pipelines(List.of())
            .build();
    wave = UpdateRunWaveSnapshotSupport.mergeChild(wave, child);
    assertEquals(UpdateRunLiveState.STALLED, wave.getOverallState());
    assertEquals("sat_customer", wave.getModels().get(0).getCurrentElementName());
    assertEquals("retail-360", wave.getModels().get(0).getModelName());
    assertTrue(wave.getTooltipText().contains("sat_customer"));
    assertEquals(child, wave.getCurrentLiveSnapshot());

    Result ok = new Result();
    ok.setResult(true);
    ok.setNrErrors(0);
    ok.setNrLinesRead(10);
    ok.setNrLinesOutput(8);
    wave = UpdateRunWaveSnapshotSupport.markModelFinished(wave, "models/retail-360.hdv", ok);
    assertEquals(UpdateRunLiveState.COMPLETED, wave.getModels().get(0).getState());
    assertNull(wave.getCurrentLiveSnapshot());
    assertEquals(1, wave.getModelsCompleted());

    wave =
        UpdateRunWaveSnapshotSupport.markModelSkipped(wave, "models/retail-360.hdm", "read-only");
    assertTrue(wave.getModels().get(1).isSkipped());
    assertEquals(UpdateRunLiveState.COMPLETED, wave.getModels().get(1).getState());

    wave = UpdateRunWaveSnapshotSupport.finish(wave);
    assertEquals(UpdateRunWavePhase.FINISHED, wave.getPhase());
    assertEquals(UpdateRunLiveState.COMPLETED, wave.getOverallState());
    assertEquals(2, wave.getModelsCompleted());
    assertFalse(wave.getTooltipText().isBlank());
  }

  @Test
  void failedModelRollsUpAndRetainsAfterFinish() {
    UpdateRunWaveSnapshot wave =
        UpdateRunWaveSnapshotSupport.initial(
            "wave-2",
            "g",
            "wf.hwf",
            "wf",
            "Update group",
            "exec-1",
            List.of(pending("DATA_VAULT", "a.hdv"), pending("BUSINESS_VAULT", "b.hbv")),
            new Date());
    wave = UpdateRunWaveSnapshotSupport.markModelStarted(wave, "a.hdv");
    Result failed = new Result();
    failed.setResult(false);
    failed.setNrErrors(2);
    wave = UpdateRunWaveSnapshotSupport.markModelFinished(wave, "a.hdv", failed);
    wave = UpdateRunWaveSnapshotSupport.finish(wave);
    assertEquals(UpdateRunLiveState.FAILED, wave.getOverallState());
    assertEquals(1, wave.getModelsFailed());
    assertEquals(UpdateRunLiveState.PENDING, wave.getModels().get(1).getState());
  }

  @Test
  void clearCurrentLiveOnlyDropsMatchingChild() {
    UpdateRunLiveSnapshot child =
        UpdateRunLiveSnapshot.builder()
            .metricsRunId("run-9")
            .waveId("wave-9")
            .modelFilename("a.hdv")
            .overallState(UpdateRunLiveState.RUNNING)
            .pipelines(List.of())
            .build();
    UpdateRunWaveSnapshot wave =
        UpdateRunWaveSnapshotSupport.initial(
                "wave-9",
                "g",
                "wf.hwf",
                "wf",
                "Update group",
                null,
                List.of(pending("DATA_VAULT", "a.hdv")),
                new Date())
            .toBuilder()
            .currentLiveSnapshot(child)
            .build();
    assertEquals(
        child,
        UpdateRunWaveSnapshotSupport.clearCurrentLive(wave, "other").getCurrentLiveSnapshot());
    assertNull(
        UpdateRunWaveSnapshotSupport.clearCurrentLive(wave, "run-9").getCurrentLiveSnapshot());
  }

  private static UpdateRunWaveModelProgress pending(String layer, String file) {
    return UpdateRunWaveModelProgress.builder()
        .layer(layer)
        .modelFile(file)
        .state(UpdateRunLiveState.PENDING)
        .build();
  }
}
