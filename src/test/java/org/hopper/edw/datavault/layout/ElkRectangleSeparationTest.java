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
package org.hopper.edw.datavault.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElkRectangleSeparationTest {

  @Test
  void overlappingBoxesSeparateAndKeepHorizontalOrder() {
    Map<String, ElkLayoutBox> boxes = new LinkedHashMap<>();
    boxes.put("a", new ElkLayoutBox("a", 0, 0, 100, 80));
    boxes.put("b", new ElkLayoutBox("b", 90, 0, 100, 80));

    Map<String, ElkLayoutBox> separated = ElkRectangleSeparation.separate(boxes, 16);

    ElkLayoutBox a = separated.get("a");
    ElkLayoutBox b = separated.get("b");
    assertTrue(a.x() + a.width() + 16 <= b.x());
    assertTrue(a.x() < b.x());
  }
}
