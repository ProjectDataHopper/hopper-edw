/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.ai.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class BvAiScenarioTest {

  @Test
  void descriptionsResolveFromMessageBundle() {
    assertEquals("General help", BvAiScenario.GENERAL.getDescription());
    assertEquals("Business Vault modeling", BvAiScenario.BV_MODELING.getDescription());
    assertEquals("Hop integration", BvAiScenario.HOP_INTEGRATION.getDescription());
  }

  @Test
  void getDescriptionsReturnsLocalizedLabels() {
    String[] descriptions = BvAiScenario.getDescriptions();
    assertEquals(BvAiScenario.values().length, descriptions.length);
    for (String description : descriptions) {
      assertFalse(description.isBlank());
      assertFalse(description.startsWith("!"), "Missing i18n key: " + description);
      assertFalse(description.startsWith("BvAiScenario."), "Unresolved key: " + description);
    }
    assertEquals("General help", descriptions[BvAiScenario.GENERAL.ordinal()]);
  }

  @Test
  void lookupDescriptionResolvesLocalizedLabel() {
    assertEquals(
        BvAiScenario.BV_MODELING, BvAiScenario.lookupDescription("Business Vault modeling"));
  }

  @Test
  void resolveAcceptsLocalizedDescription() {
    assertEquals(BvAiScenario.ERROR_DIAGNOSIS, BvAiScenario.resolve("Error diagnosis"));
  }
}
