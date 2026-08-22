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
package org.hopper.edw.datavault.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LineageViewLabelFitTest {

  private static final String JOB = "dm/retail-f-order-lines/d_product";

  @Test
  void keepsFullNameWhenItFits() {
    assertEquals(JOB, LineageViewLabelFit.fitTail(JOB, JOB.length(), String::length));
  }

  @Test
  void keepsTailAndSnapsToSlash() {
    // 1-char ellipsis + "/retail-f-order-lines/d_product"
    String fitted =
        LineageViewLabelFit.fitTail(
            JOB, 1 + "/retail-f-order-lines/d_product".length(), String::length);
    assertEquals("…/retail-f-order-lines/d_product", fitted);
    assertFalse(fitted.startsWith("…dm"));
    assertTrue(fitted.endsWith("d_product"));
  }

  @Test
  void dropsEarlierPathSegmentsWhenNeeded() {
    String fitted = LineageViewLabelFit.fitTail(JOB, 1 + "/d_product".length(), String::length);
    assertEquals("…/d_product", fitted);
  }

  @Test
  void emptyAndNullAreBlank() {
    assertEquals("", LineageViewLabelFit.fitTail(null, 20, String::length));
    assertEquals("", LineageViewLabelFit.fitTail("", 20, String::length));
  }
}
