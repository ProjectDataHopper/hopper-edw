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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UpdateRunLiveRegistryTest {

  @AfterEach
  void cleanup() {
    UpdateRunLiveRegistry.remove("run-1");
    UpdateRunLiveRegistry.remove("run-child");
    UpdateRunLiveRegistry.removeWave("wave-1");
  }

  @Test
  void publishesAndFindsByWorkflowAction() {
    UpdateRunLiveSnapshot snapshot =
        UpdateRunLiveSnapshot.builder()
            .metricsRunId("run-1")
            .modelName("retail-360")
            .workflowFilename("/tmp/update-retail.hwf")
            .actionName("Data Vault Update")
            .startedAt(new Date())
            .updatedAt(new Date())
            .overallState(UpdateRunLiveState.RUNNING)
            .currentElementName("d_customer")
            .tooltipText("Updating table d_customer of model retail-360")
            .pipelines(java.util.List.of())
            .build();

    UpdateRunLiveRegistry.publish(snapshot);

    assertTrue(
        UpdateRunLiveRegistry.findByWorkflowAction("/tmp/update-retail.hwf", "Data Vault Update")
            .isPresent());
    assertEquals(
        "d_customer",
        UpdateRunLiveRegistry.findByRunId("run-1").orElseThrow().getCurrentElementName());
  }

  @Test
  void indexesWaveByCanvasActionAndMergesChildWithoutDroppingWave() {
    UpdateRunWaveSnapshot wave =
        UpdateRunWaveSnapshotSupport.initial(
            "wave-1",
            "retail-sources",
            "/tmp/update-retail.hwf",
            "update-retail",
            "Update resource definition group",
            null,
            List.of(
                UpdateRunWaveModelProgress.builder()
                    .layer("DATA_VAULT")
                    .modelFile("models/retail-360.hdv")
                    .state(UpdateRunLiveState.PENDING)
                    .build()),
            new Date());
    UpdateRunLiveRegistry.publishWave(wave);

    assertTrue(
        UpdateRunLiveRegistry.findWaveByWorkflowAction(
                "/tmp/update-retail.hwf", "Update resource definition group")
            .isPresent());

    UpdateRunLiveSnapshot child =
        UpdateRunLiveSnapshot.builder()
            .metricsRunId("run-child")
            .waveId("wave-1")
            .modelName("retail-360")
            .modelFilename("models/retail-360.hdv")
            .workflowFilename("/tmp/update-retail.hwf")
            .actionName("DV models/retail-360.hdv")
            .overallState(UpdateRunLiveState.RUNNING)
            .currentElementName("hub_customer")
            .updatedAt(new Date())
            .pipelines(List.of())
            .build();
    UpdateRunLiveRegistry.publish(child);

    UpdateRunWaveSnapshot merged = UpdateRunLiveRegistry.findWaveById("wave-1").orElseThrow();
    assertEquals("hub_customer", merged.getModels().get(0).getCurrentElementName());
    assertEquals("run-child", merged.getCurrentLiveSnapshot().getMetricsRunId());

    UpdateRunLiveRegistry.remove("run-child");
    UpdateRunWaveSnapshot afterRemove =
        UpdateRunLiveRegistry.findWaveByWorkflowAction(
                "/tmp/update-retail.hwf", "Update resource definition group")
            .orElseThrow();
    assertEquals("wave-1", afterRemove.getWaveId());
    assertNull(afterRemove.getCurrentLiveSnapshot());
  }
}
