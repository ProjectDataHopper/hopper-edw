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
package org.hopper.edw.datavault.hopgui.file.modelgraph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.gui.Point;
import org.junit.jupiter.api.Test;

class ModelGraphClickSupportTest {

  @Test
  void samePointIsAClick() {
    Point click = new Point(40, 50);
    assertTrue(ModelGraphClickSupport.isUnmovedClick(click, new Point(40, 50), 3));
  }

  @Test
  void jitterWithinThresholdIsAClick() {
    Point click = new Point(40, 50);
    assertTrue(ModelGraphClickSupport.isUnmovedClick(click, new Point(43, 50), 3));
    assertTrue(ModelGraphClickSupport.isUnmovedClick(click, new Point(40, 53), 3));
  }

  @Test
  void movementPastThresholdIsADrag() {
    Point click = new Point(40, 50);
    assertFalse(ModelGraphClickSupport.isUnmovedClick(click, new Point(44, 50), 3));
    assertFalse(ModelGraphClickSupport.isUnmovedClick(click, new Point(40, 54), 3));
  }

  @Test
  void missingPointsAreNotClicks() {
    Point click = new Point(40, 50);
    assertFalse(ModelGraphClickSupport.isUnmovedClick(null, click, 3));
    assertFalse(ModelGraphClickSupport.isUnmovedClick(click, null, 3));
    assertFalse(ModelGraphClickSupport.isUnmovedClick(null, null, 3));
  }
}
