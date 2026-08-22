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
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class DmTableTypeTest {

  @Test
  void codeMatchesEnumName() {
    assertEquals("DIMENSION", DmTableType.DIMENSION.getCode());
    assertEquals("DIMENSION_ALIAS", DmTableType.DIMENSION_ALIAS.getCode());
    assertEquals(DmTableType.FACT.name(), DmTableType.FACT.getCode());
  }

  @Test
  void descriptionsResolveFromMessageBundle() {
    assertEquals("Dimension", DmTableType.DIMENSION.getDescription());
    assertEquals("Dimension alias", DmTableType.DIMENSION_ALIAS.getDescription());
    assertEquals("Periodic snapshot fact", DmTableType.PERIODIC_SNAPSHOT_FACT.getDescription());
  }

  @Test
  void getDescriptionsReturnsLocalizedLabels() {
    String[] descriptions = DmTableType.getDescriptions();
    assertEquals(DmTableType.values().length, descriptions.length);
    for (String description : descriptions) {
      assertFalse(description.isBlank());
      assertFalse(description.startsWith("!"), "Missing i18n key: " + description);
      assertFalse(description.startsWith("DmTableType."), "Unresolved key: " + description);
    }
    assertEquals("Fact", descriptions[DmTableType.FACT.ordinal()]);
  }

  @Test
  void lookupDescriptionResolvesLocalizedLabel() {
    assertEquals(DmTableType.BRIDGE, DmTableType.lookupDescription("Bridge"));
  }
}
