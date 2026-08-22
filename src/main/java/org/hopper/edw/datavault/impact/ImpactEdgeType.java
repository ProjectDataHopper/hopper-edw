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
package org.hopper.edw.datavault.impact;

/** Why two impact nodes are connected. */
public enum ImpactEdgeType {
  /** Catalog source field/object maps into a DV hub, link, or satellite. */
  SOURCE_TO_DV,
  /** DV satellite or link depends on a parent hub (or hubs). */
  DV_PARENT,
  /** DV satellite attribute feeds a BV SCD2 target column. */
  DV_TO_BV_SCD2,
  /** DV hub/sat participates in a BV PIT table (table-level). */
  DV_TO_BV_PIT,
  /** DV/BV table is referenced from a BV SQL business table (table-level). */
  DV_TO_BV_SQL,
  /** DV/BV physical name appears in dimensional SQL (table-level, medium confidence). */
  DV_BV_TO_DM_SQL,
  /** Catalog source is a dimensional RECORD_DEFINITION staging feed. */
  SOURCE_TO_DM_RECORD
}
