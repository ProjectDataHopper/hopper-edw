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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.gui.Point;
import org.junit.jupiter.api.Test;

class ModelGraphTableCardLayoutTest {

  @Test
  void nameAndTypePositionsFollowIconColumnLayout() {
    int boxX = 100;
    int boxY = 50;

    assertEquals(
        boxX
            + ModelGraphTableCardLayout.MARGIN
            + ModelGraphTableCardLayout.ICON_SIZE
            + ModelGraphTableCardLayout.MARGIN,
        ModelGraphTableCardLayout.nameX(boxX));
    assertEquals(boxY + ModelGraphTableCardLayout.MARGIN, ModelGraphTableCardLayout.nameY(boxY));
    assertEquals(
        boxY
            + ModelGraphTableCardLayout.MARGIN
            + ModelGraphTableCardLayout.ICON_SIZE
            + ModelGraphTableCardLayout.MARGIN,
        ModelGraphTableCardLayout.typeLabelY(boxY));
  }

  @Test
  void secondaryLineYAddsGapBelowNameExtent() {
    int boxY = 20;
    Point nameExtent = new Point(80, 14);

    assertEquals(
        ModelGraphTableCardLayout.nameY(boxY)
            + nameExtent.y
            + ModelGraphTableCardLayout.SECONDARY_LABEL_GAP,
        ModelGraphTableCardLayout.secondaryLineY(boxY, nameExtent));
  }
}
