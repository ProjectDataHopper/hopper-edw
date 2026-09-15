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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SourceModelPainterEmptyHintTest {

  @Test
  void emptyModelHintScaleIsModestHalfwayBoost() {
    // Half-way between 1.0 (too small) and 2.5×native (too large): 1.5×native.
    // Zoom is passed in so this test never initializes SWT/PropsUi (headless CI).
    assertEquals(1.5f, SourceModelPainter.emptyModelHintScale(1.0d), 0.001f);
    assertEquals(1.5f, SourceModelPainter.emptyModelHintScale(0.5d), 0.001f);
    assertEquals(3.0f, SourceModelPainter.emptyModelHintScale(2.0d), 0.001f);
  }
}
