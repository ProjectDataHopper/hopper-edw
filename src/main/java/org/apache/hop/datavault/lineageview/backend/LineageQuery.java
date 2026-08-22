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
package org.apache.hop.datavault.lineageview.backend;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineage.LineageSnapshot;

/** Neighborhood request. Adapters fetch raw structure; {@link LineageGraphOps} clips it. */
@Value
@Builder
public class LineageQuery {
  /** Preferred seed: dataset first (end-of-chain data), then job. */
  OpenLineageRef dataset;

  OpenLineageRef job;

  LineageLayer modelLayer;
  String modelName;
  String logicalTable;
  String modelFilename;

  @Builder.Default LineageDirection direction = LineageDirection.UPSTREAM;
  @Builder.Default int depth = 6;
  @Builder.Default boolean includeJobs = true;

  /** Empty = keep all layers. */
  @Builder.Default List<LineageGraphLayer> layerFilters = List.of();

  /**
   * Unsaved (or extra) model snapshots from the GUI. Headless adapters must not look at HopGui.
   * Local-models maps these through the snapshot mapper. Null/empty is fine for Marquez and
   * file-folder.
   */
  @Builder.Default List<LineageSnapshot> extraSnapshots = List.of();

  String resourceGroup;

  public boolean hasSeedIdentity() {
    return (dataset != null && dataset.isComplete())
        || (job != null && job.isComplete())
        || (modelName != null
            && !modelName.isBlank()
            && logicalTable != null
            && !logicalTable.isBlank());
  }
}
