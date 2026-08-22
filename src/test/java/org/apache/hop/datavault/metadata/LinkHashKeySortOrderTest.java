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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Documents that STRING hash-key ordering used by Hop SortRows is not the same as typical
 * linguistic database ORDER BY collations for decimal-dash digests (e.g. {@code 0-10-...} vs {@code
 * 0-100-...}).
 *
 * <p>Link CDC must order the target stream with either a hop-compatible binary/{@code C} collation
 * on SQL {@code ORDER BY} (see {@link DvHashKeyOrderStrategySupport}) or Hop SortRows — never a
 * default locale/UCA collation alone.
 */
class LinkHashKeySortOrderTest {

  @Test
  void javaStringOrderPlacesTenBeforeOneHundredInThirdSegment() {
    String ten = DvHashKeyOrderStrategySupport.PROBE_TEN;
    String hundred = DvHashKeyOrderStrategySupport.PROBE_HUNDRED;

    // Java / Hop SortRows (case-sensitive): '-' (45) < '0' (48) at the first differing char after
    // "0-10", so "0-10-..." sorts before "0-100-...".
    assertTrue(ten.compareTo(hundred) < 0);
    assertTrue(DvHashKeyOrderStrategySupport.javaPlacesTenBeforeHundred());

    List<String> javaOrder = new ArrayList<>(List.of(hundred, ten));
    Collections.sort(javaOrder);
    assertTrue(javaOrder.get(0).equals(ten));
  }
}
