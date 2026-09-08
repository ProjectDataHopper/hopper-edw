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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.Result;
import org.hopper.edw.datavault.workflow.actions.updateresourcegroup.ActionUpdateResourceDefinitionGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UpdateRunWaveMonitorTest {

  private String waveId;

  @AfterEach
  void cleanup() {
    if (waveId != null) {
      UpdateRunLiveRegistry.removeWave(waveId);
    }
  }

  @Test
  void publishesRetainsAndFinishesWaveForCanvasAction() {
    ActionUpdateResourceDefinitionGroup action = new ActionUpdateResourceDefinitionGroup();
    action.setName("Update resource definition group");
    UpdateRunWaveMonitor monitor =
        UpdateRunWaveMonitor.start(
            action,
            "retail-sources",
            List.of(new UpdateRunWaveMonitor.PlannedModel("DATA_VAULT", "models/a.hdv")));
    waveId = monitor.getWaveId();

    assertTrue(
        UpdateRunLiveRegistry.findWaveByWorkflowAction(
                "Update resource definition group", "Update resource definition group")
            .isPresent());

    monitor.markValidating(1);
    assertEquals(
        UpdateRunWavePhase.VALIDATING,
        UpdateRunLiveRegistry.findWaveById(waveId).orElseThrow().getPhase());

    monitor.markUpdating();
    monitor.markModelStarted("models/a.hdv");
    Result result = new Result();
    result.setResult(true);
    monitor.markModelFinished("models/a.hdv", result);
    monitor.close();

    UpdateRunWaveSnapshot finished = UpdateRunLiveRegistry.findWaveById(waveId).orElseThrow();
    assertEquals(UpdateRunWavePhase.FINISHED, finished.getPhase());
    assertEquals(UpdateRunLiveState.COMPLETED, finished.getOverallState());
    assertEquals(1, finished.getModelsCompleted());
  }
}
