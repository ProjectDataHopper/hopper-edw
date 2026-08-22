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

import java.util.List;
import org.junit.jupiter.api.Test;

class EdwJourneyGroupSelectionTest {

  @Test
  void prefersCurrentThenLastThenFirst() {
    List<String> names = List.of("core", "retail-sources");
    assertEquals(
        "retail-sources", EdwJourneyGroupSelection.resolve(names, "retail-sources", "core"));
    assertEquals("core", EdwJourneyGroupSelection.resolve(names, "", "core"));
    assertEquals("core", EdwJourneyGroupSelection.resolve(names, "gone", "also-gone"));
    assertEquals("", EdwJourneyGroupSelection.resolve(List.of(), "retail-sources", "core"));
  }

  @Test
  void matchesLastUsedIgnoreCase() {
    assertEquals(
        "retail-sources",
        EdwJourneyGroupSelection.resolve(List.of("retail-sources"), "", "Retail-Sources"));
  }
}
