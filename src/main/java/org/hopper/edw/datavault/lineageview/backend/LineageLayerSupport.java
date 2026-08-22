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
package org.hopper.edw.datavault.lineageview.backend;

import java.util.List;
import org.apache.hop.core.util.Utils;

/** Assigns {@link LineageGraphLayer} from hop facets and job-name prefixes. */
public final class LineageLayerSupport {

  private LineageLayerSupport() {}

  public static LineageGraphLayer infer(
      LineageNodeKind kind,
      String name,
      HopExportFacet hopExport,
      HopLocationFacet hopLocation,
      List<LineageWarning> warnings,
      String nodeId) {
    if (hopExport != null && !Utils.isEmpty(hopExport.getModelLayer())) {
      String layer = hopExport.getModelLayer().trim();
      if ("DV".equalsIgnoreCase(layer)) {
        return LineageGraphLayer.DV;
      }
      if ("BV".equalsIgnoreCase(layer)) {
        return LineageGraphLayer.BV;
      }
      if ("DM".equalsIgnoreCase(layer)) {
        return LineageGraphLayer.DM;
      }
    }
    String jobName = name != null ? name : "";
    int slash = jobName.indexOf('/');
    String prefix = slash > 0 ? jobName.substring(0, slash) : jobName;
    if ("dv".equalsIgnoreCase(prefix)) {
      return LineageGraphLayer.DV;
    }
    if ("bv".equalsIgnoreCase(prefix)) {
      return LineageGraphLayer.BV;
    }
    if ("dm".equalsIgnoreCase(prefix)) {
      return LineageGraphLayer.DM;
    }
    if (hopLocation != null
        && (!Utils.isEmpty(hopLocation.getCatalogKey())
            || !Utils.isEmpty(hopLocation.getCatalogConnection()))) {
      return LineageGraphLayer.SOURCE;
    }
    if (kind == LineageNodeKind.DATASET) {
      return LineageGraphLayer.SOURCE;
    }
    if (warnings != null) {
      warnings.add(
          LineageWarning.builder()
              .code(LineageWarning.LAYER_INFERRED)
              .message("Job layer defaulted to DV")
              .nodeId(nodeId)
              .build());
    }
    return LineageGraphLayer.DV;
  }
}
