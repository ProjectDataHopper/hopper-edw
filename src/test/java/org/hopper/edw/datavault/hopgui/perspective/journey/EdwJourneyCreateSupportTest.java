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
package org.hopper.edw.datavault.hopgui.perspective.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class EdwJourneyCreateSupportTest {

  @Test
  void addPathIfAbsentAppendsOncePerLayer() {
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("sales-edw");
    String dv = "${PROJECT_HOME}/models/sales.hdv";
    String bv = "${PROJECT_HOME}/models/sales.hbv";
    assertTrue(
        EdwJourneyCreateSupport.addPathIfAbsent(
            group, dv, EdwJourneyCreateSupport.LAYER_DATA_VAULT));
    assertFalse(
        EdwJourneyCreateSupport.addPathIfAbsent(
            group, dv, EdwJourneyCreateSupport.LAYER_DATA_VAULT));
    assertTrue(
        EdwJourneyCreateSupport.addPathIfAbsent(
            group, bv, EdwJourneyCreateSupport.LAYER_BUSINESS_VAULT));
    assertEquals(1, group.getDataVaultModelFiles().size());
    assertEquals(dv, group.getDataVaultModelFiles().get(0));
    assertEquals(1, group.getBusinessVaultModelFiles().size());
    assertFalse(EdwJourneyCreateSupport.addPathIfAbsent(group, dv, "unknown"));
    assertFalse(
        EdwJourneyCreateSupport.addPathIfAbsent(
            null, dv, EdwJourneyCreateSupport.LAYER_DATA_VAULT));
  }

  @Test
  void proposedModelsPathUsesProjectHome() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", "/tmp/edw");
    assertEquals(
        "/tmp/edw/models/sales-dv.hdv",
        EdwJourneyCreateSupport.proposedModelsPath(variables, "sales-dv.hdv"));
  }
}
