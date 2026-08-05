/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.architecture;

/** Which slice of the architecture graph to export. */
public enum ArchitectureViewType {
  /** Workflow/capability view of what is implemented and run. */
  SOLUTION,
  /**
   * Inventory of involved tables/sources across models (no relationship edges). Prefer {@link
   * #MODEL} for relational ER-style diagrams.
   */
  DATA,
  /**
   * Aggregated layer data diagrams: one Draw.io for all DV models, one for all BV, one for all
   * dimensional — tables unioned across files, ELK layout, structural relationships.
   */
  MODEL,
  /**
   * One Draw.io per model file under type subfolders ({@code data-vault/}, {@code business-vault/},
   * {@code dimensional/}), named {@code {basename}.drawio}. Prefer a resource definition group as
   * the model source.
   */
  MODELS,
  /** Coarse Source → DV → BV → DM flow (same exporter as SOLUTION for now). */
  END_TO_END
}
