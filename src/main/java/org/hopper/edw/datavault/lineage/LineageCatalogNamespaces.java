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
package org.hopper.edw.datavault.lineage;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;

/**
 * Catalog namespaces for published source-to-target lineage sibling records.
 *
 * <p>Pattern: {@code hop/{project}/lineage/{layer}/{modelBasename}} where layer is {@code dv},
 * {@code bv}, or {@code dm}.
 */
public final class LineageCatalogNamespaces {

  public static final String TAG_LINEAGE = "LINEAGE";
  public static final String TAG_LINEAGE_SNAPSHOT = "LINEAGE_SNAPSHOT";
  public static final String PROP_LAYER = "lineageLayer";
  public static final String PROP_PHYSICAL_TABLE = "physicalTableName";
  public static final String PROP_TABLE_TYPE = "tableType";
  public static final String PROP_TABLE_REASONS_JSON = "tableReasonsJson";
  public static final String PROP_SOURCES_JSON = "tableSourcesJson";
  public static final String PROP_FIELDS_JSON = "fieldsJson";
  public static final String PROP_SNAPSHOT_JSON = "lineageSnapshotJson";
  public static final String PROP_TABLE_COUNT = "lineageTableCount";
  public static final String SNAPSHOT_RECORD_NAME = "_snapshot";

  private LineageCatalogNamespaces() {}

  public static String projectLineageNamespace(
      IVariables variables, LineageLayer layer, String modelBasename) {
    String project = DvCatalogNamespaces.resolveProjectKey(variables);
    String layerSeg = layerSegment(layer);
    String model = sanitize(modelBasename);
    return "hop/" + project + "/lineage/" + layerSeg + "/" + model;
  }

  public static String layerSegment(LineageLayer layer) {
    if (layer == null) {
      return "dv";
    }
    return switch (layer) {
      case BV -> "bv";
      case DM -> "dm";
      case CROSS -> "cross";
      case DV -> "dv";
    };
  }

  public static String sanitize(String segment) {
    if (Utils.isEmpty(segment)) {
      return "model";
    }
    String cleaned = segment.trim().replaceAll("[\\\\/]+", "-");
    cleaned = cleaned.replaceAll("\\s+", "-");
    return Utils.isEmpty(cleaned) ? "model" : cleaned;
  }
}
