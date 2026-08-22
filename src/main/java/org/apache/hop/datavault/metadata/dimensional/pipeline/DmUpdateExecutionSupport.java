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
package org.apache.hop.datavault.metadata.dimensional.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimensionSupport;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;

/** Execution ordering helpers for dimensional model update workflows. */
public final class DmUpdateExecutionSupport {

  private DmUpdateExecutionSupport() {}

  /** Returns dimensions, junk dimensions, bridges, then fact variants (model order within each). */
  public static List<IDmTable> orderTablesForPipelineExecution(List<IDmTable> tables) {
    List<IDmTable> dimensions = new ArrayList<>();
    List<IDmTable> junkDimensions = new ArrayList<>();
    List<IDmTable> bridges = new ArrayList<>();
    List<IDmTable> facts = new ArrayList<>();
    if (tables == null) {
      return List.of();
    }
    for (IDmTable table : tables) {
      if (table == null || table.getTableType() == null) {
        continue;
      }
      switch (table.getTableType()) {
        case DIMENSION -> dimensions.add(table);
        case DIMENSION_ALIAS -> {
          // Aliases inherit pipelines from the referenced physical dimension.
        }
        case RANGE_DIMENSION -> {
          // Metadata-only band definitions; applied on fact load pipelines.
        }
        case JUNK_DIMENSION -> {
          if (table instanceof DmJunkDimension junkDimension
              && DmJunkDimensionSupport.requiresStandalonePipeline(junkDimension)) {
            junkDimensions.add(table);
          }
        }
        case BRIDGE -> bridges.add(table);
        case FACT,
                FACTLESS_FACT,
                PERIODIC_SNAPSHOT_FACT,
                ACCUMULATING_SNAPSHOT_FACT,
                AGGREGATE_FACT ->
            facts.add(table);
      }
    }
    List<IDmTable> ordered = new ArrayList<>();
    ordered.addAll(dimensions);
    ordered.addAll(junkDimensions);
    ordered.addAll(bridges);
    ordered.addAll(facts);
    return ordered;
  }
}
