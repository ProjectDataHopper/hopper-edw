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
package org.apache.hop.datavault.executionmap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapNodeColorsTest {

  @Test
  void darkModeDataVaultModelNodesUseDarkCustomFill() {
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DATA_VAULT_MODEL, true));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DV_UPDATE, true));
  }

  @Test
  void darkModeDimensionalModelNodesUseDarkCustomFill() {
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DIMENSIONAL_MODEL, true));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DM_UPDATE, true));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DM_PUBLISH, true));
  }

  @Test
  void darkModeDatasetNodesUseDarkCustomFill() {
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.TARGET_DATASET, true));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.SOURCE_DATASET, true));
  }

  @Test
  void darkModeWorkflowNodesUseDarkCustomFill() {
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.WORKFLOW, true));
    assertNotNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.ROOT_WORKFLOW, true));
  }

  @Test
  void darkModePipelineNodesUseEnumFillOnly() {
    assertNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.PIPELINE, true));
    assertNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.ROOT_PIPELINE, true));
  }

  @Test
  void lightModeUsesPastelFillsForWorkflowAndModels() {
    int[] workflow = ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.WORKFLOW, false);
    int[] dv = ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DATA_VAULT_MODEL, false);
    int[] dm = ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.DM_UPDATE, false);
    assertNotNull(workflow);
    assertNotNull(dv);
    assertNotNull(dm);
    assertTrue(isLightPastel(workflow));
    assertTrue(isLightPastel(dv));
    assertTrue(isLightPastel(dm));
  }

  @Test
  void lightModePipelineNodesUsePastelFill() {
    int[] pipeline = ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.PIPELINE, false);
    assertNotNull(pipeline);
    assertTrue(isLightPastel(pipeline));
  }

  @Test
  void generatedPipelineNodesKeepEnumFillInLightAndDarkMode() {
    assertNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.GENERATED_PIPELINE, false));
    assertNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.GENERATED_PIPELINE, true));
    assertNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.ORCHESTRATOR_PIPELINE, false));
    assertNull(ExecutionMapNodeColors.fillRgb(ExecutionMapNodeType.BULK_MASTER_WORKFLOW, false));
  }

  private static boolean isLightPastel(int[] rgb) {
    return rgb[0] > 180 && rgb[1] > 180 && rgb[2] > 180;
  }
}
