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
package org.hopper.edw.datavault.hopgui.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveState;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveModelProgress;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshotSupport;
import org.junit.jupiter.api.Test;

class UpdateRunWaveDiagnosticsFormatterTest {

  @Test
  void formatsWaveHeaderAndCurrentModel() {
    UpdateRunWaveSnapshot wave =
        UpdateRunWaveSnapshotSupport.initial(
            "wave-1",
            "retail-sources",
            "/tmp/update.hwf",
            "update",
            "Update resource definition group",
            null,
            List.of(
                UpdateRunWaveModelProgress.builder()
                    .layer("DATA_VAULT")
                    .modelFile("models/retail-360.hdv")
                    .state(UpdateRunLiveState.PENDING)
                    .build()),
            new Date(System.currentTimeMillis() - 5_000L));
    wave = UpdateRunWaveSnapshotSupport.markModelStarted(wave, "models/retail-360.hdv");
    wave =
        UpdateRunWaveSnapshotSupport.mergeChild(
            wave,
            UpdateRunLiveSnapshot.builder()
                .metricsRunId("run-1")
                .waveId("wave-1")
                .modelName("retail-360")
                .modelFilename("models/retail-360.hdv")
                .overallState(UpdateRunLiveState.RUNNING)
                .currentElementName("hub_customer")
                .pipelines(List.of())
                .startedAt(new Date())
                .updatedAt(new Date())
                .build());

    String markdown = UpdateRunWaveDiagnosticsFormatter.formatMarkdown(wave);
    assertTrue(markdown.contains("retail-sources"));
    assertTrue(markdown.contains("hub_customer"));
    assertTrue(markdown.contains("retail-360"));
  }
}
