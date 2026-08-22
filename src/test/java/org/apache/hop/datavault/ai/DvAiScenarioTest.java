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
package org.apache.hop.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class DvAiScenarioTest {

  @Test
  void descriptionsResolveFromMessageBundle() {
    assertEquals("General help", DvAiScenario.GENERAL.getDescription());
    assertEquals("Data Vault modeling", DvAiScenario.DV_MODELING.getDescription());
    assertEquals("Source analysis", DvAiScenario.SOURCE_ANALYSIS.getDescription());
    assertEquals("Performance tuning", DvAiScenario.PERFORMANCE_TUNING.getDescription());
  }

  @Test
  void getDescriptionsReturnsLocalizedLabels() {
    String[] descriptions = DvAiScenario.getDescriptions();
    assertEquals(DvAiScenario.values().length, descriptions.length);
    for (String description : descriptions) {
      assertFalse(description.isBlank());
      assertFalse(description.startsWith("!"), "Missing i18n key: " + description);
      assertFalse(description.startsWith("DvAiScenario."), "Unresolved key: " + description);
    }
    assertEquals("General help", descriptions[DvAiScenario.GENERAL.ordinal()]);
  }

  @Test
  void lookupDescriptionResolvesLocalizedLabel() {
    assertEquals(DvAiScenario.DV_MODELING, DvAiScenario.lookupDescription("Data Vault modeling"));
  }

  @Test
  void resolveAcceptsLocalizedDescription() {
    assertEquals(DvAiScenario.ERROR_DIAGNOSIS, DvAiScenario.resolve("Error diagnosis"));
  }
}
