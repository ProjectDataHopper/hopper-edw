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

import org.hopper.edw.datavault.config.DataVaultConfigSingleton;
import org.junit.jupiter.api.Test;

class UpdateRunLiveMonitorTest {

  @Test
  void buildsTooltipForUpdatingTable() {
    String tooltip =
        UpdateRunLiveMonitor.buildTooltipText(
            "d_customer", "retail-360", UpdateRunLiveState.RUNNING);
    assertTrue(tooltip.contains("d_customer"));
    assertTrue(tooltip.contains("retail-360"));
  }

  @Test
  void pollIntervalMsUsesConfiguredSeconds() {
    DataVaultConfigSingleton.getConfig().setLiveUpdatePollIntervalSeconds(25);
    assertEquals(25_000L, UpdateRunLiveMonitor.pollIntervalMs());
  }

  @Test
  void buildsTooltipForStalledTable() {
    String tooltip =
        UpdateRunLiveMonitor.buildTooltipText(
            "d_customer", "retail-360", UpdateRunLiveState.STALLED);
    assertTrue(tooltip.toLowerCase().contains("stalled"));
    assertTrue(tooltip.contains("d_customer"));
  }
}
