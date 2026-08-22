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
package org.apache.hop.datavault.ai.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class PipelineAiScenarioTest {

  @Test
  void descriptionsResolveFromMessageBundle() {
    assertEquals("General help", PipelineAiScenario.PIPELINE_GENERAL.getDescription());
    assertEquals("Pipeline design", PipelineAiScenario.PIPELINE_DESIGN.getDescription());
    assertEquals("Transform selection", PipelineAiScenario.TRANSFORM_SELECTION.getDescription());
  }

  @Test
  void getDescriptionsReturnsLocalizedLabels() {
    String[] descriptions = PipelineAiScenario.getDescriptions();
    assertEquals(PipelineAiScenario.values().length, descriptions.length);
    for (String description : descriptions) {
      assertFalse(description.isBlank());
      assertFalse(description.startsWith("!"), "Missing i18n key: " + description);
      assertFalse(description.startsWith("PipelineAiScenario."), "Unresolved key: " + description);
    }
    assertEquals("General help", descriptions[PipelineAiScenario.PIPELINE_GENERAL.ordinal()]);
  }

  @Test
  void lookupDescriptionResolvesLocalizedLabel() {
    assertEquals(
        PipelineAiScenario.PIPELINE_DESIGN,
        PipelineAiScenario.lookupDescription("Pipeline design"));
  }

  @Test
  void resolveAcceptsLocalizedDescription() {
    assertEquals(
        PipelineAiScenario.PIPELINE_ERROR_DIAGNOSIS, PipelineAiScenario.resolve("Error diagnosis"));
  }
}
