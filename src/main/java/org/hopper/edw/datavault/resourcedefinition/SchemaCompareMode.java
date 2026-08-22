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
package org.hopper.edw.datavault.resourcedefinition;

/** How schema impact simulation chooses expected vs actual contracts. */
public enum SchemaCompareMode {
  /**
   * Expected = working-tree catalog or {@code catalogVersionTag}; actual = live physical source
   * discovery (default CI gate).
   */
  LIVE_SOURCE,
  /**
   * Expected = {@code baselineVersionTag}; actual = working-tree catalog (offline design review).
   */
  WORKING_VS_VERSION,
  /**
   * Expected = {@code baselineVersionTag}; actual = {@code catalogVersionTag} (offline version
   * diff).
   */
  VERSION_VS_VERSION,
  /**
   * Actual = persisted schema harvest run (OPS {@code schema_harvest_*}); no live source discovery.
   * Expected contract was already compared during harvest (working catalog or version tag). Use
   * after {@code Harvest source metadata}; run id from request or {@code DV_SCHEMA_HARVEST_RUN_ID}.
   */
  HARVEST_RUN
}
