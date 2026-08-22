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
package org.apache.hop.datavault.ai.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class DmAiScenarioTest {

  @Test
  void descriptionsResolveFromMessageBundle() {
    assertEquals("General help", DmAiScenario.GENERAL.getDescription());
    assertEquals("Dimensional modeling", DmAiScenario.DM_MODELING.getDescription());
    assertEquals("Hop integration", DmAiScenario.HOP_INTEGRATION.getDescription());
  }

  @Test
  void getDescriptionsReturnsLocalizedLabels() {
    String[] descriptions = DmAiScenario.getDescriptions();
    assertEquals(DmAiScenario.values().length, descriptions.length);
    for (String description : descriptions) {
      assertFalse(description.isBlank());
      assertFalse(description.startsWith("!"), "Missing i18n key: " + description);
      assertFalse(description.startsWith("DmAiScenario."), "Unresolved key: " + description);
    }
    assertEquals("General help", descriptions[DmAiScenario.GENERAL.ordinal()]);
  }

  @Test
  void lookupDescriptionResolvesLocalizedLabel() {
    assertEquals(DmAiScenario.DM_MODELING, DmAiScenario.lookupDescription("Dimensional modeling"));
  }

  @Test
  void resolveAcceptsLocalizedDescription() {
    assertEquals(DmAiScenario.ERROR_DIAGNOSIS, DmAiScenario.resolve("Error diagnosis"));
  }
}
