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
package org.hopper.edw.datavault.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DataVaultConfigTest {

  @Test
  void metricsPanelMaxRunsDefaultsToTwentyAndCopies() {
    DataVaultConfig config = new DataVaultConfig();
    assertEquals(DataVaultConfig.DEFAULT_METRICS_PANEL_MAX_RUNS, config.getMetricsPanelMaxRuns());

    config.setMetricsPanelMaxRuns(0);
    assertEquals(DataVaultConfig.DEFAULT_METRICS_PANEL_MAX_RUNS, config.getMetricsPanelMaxRuns());

    config.setMetricsPanelMaxRuns(8);
    DataVaultConfig copy = new DataVaultConfig(config);
    assertEquals(8, copy.getMetricsPanelMaxRuns());
  }
}
