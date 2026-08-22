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
package org.hopper.edw.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoadRunInsightRuleCatalogTest {

  @Test
  void toJsonIncludesAllInsightCodesAndTuningHints() {
    String json = LoadRunInsightRuleCatalog.toJson();

    assertTrue(json.contains("DIM_LOOKUP_PRELOAD_CANDIDATE"));
    assertTrue(json.contains("HIGH_TARGET_READ_RATIO"));
    assertTrue(json.contains("SORT_MEMORY_RISK"));
    assertTrue(json.contains("BULK_LOAD_USED"));
    assertTrue(json.contains("HIGH_TRANSFORM_DURATION"));
    assertTrue(json.contains("preloadLookupCache"));
    assertTrue(json.contains("sortRowsSize"));
  }
}
