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
 *
 */

package org.apache.hop.datavault.architecture;

import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineage.TableLineage;
import org.apache.hop.datavault.lineage.TableSourceKind;
import org.apache.hop.datavault.lineage.TableSourceRef;

/**
 * Builds a DATA <em>inventory</em> {@link ArchitectureGraph} from one or more model lineage
 * snapshots: tables and sources only, no relationship edges. For relational diagrams use {@link
 * ArchitectureGraphFromModel}.
 */
public final class ArchitectureGraphFromLineage {

  private ArchitectureGraphFromLineage() {}

  public static ArchitectureGraph build(List<LineageSnapshot> snapshots) {
    ArchitectureGraph graph = new ArchitectureGraph();
    graph.setViewType(ArchitectureViewType.DATA);
    graph.setName("data-inventory");
    graph.setDescription(
        "Data inventory (tables and sources) from model lineage — no relationship edges");
    // Inventory only: involved tables, not ER relationships (see aggregated MODEL export for ELK)
    graph.setOmitEdges(true);
    graph.setFreeformLayout(false);

    if (snapshots == null || snapshots.isEmpty()) {
      return graph;
    }

    int modelCount = 0;
    for (LineageSnapshot snapshot : snapshots) {
      if (snapshot == null) {
        continue;
      }
      if (!Utils.isEmpty(snapshot.getModelName()) || !Utils.isEmpty(snapshot.getModelFilename())) {
        modelCount++;
      }
      addSnapshot(graph, snapshot);
    }
    if (modelCount > 0) {
      graph.setDescription(
          "Data inventory from "
              + modelCount
              + " model(s) — tables and sources only, no relationship edges");
    }
    return graph;
  }

  private static void addSnapshot(ArchitectureGraph graph, LineageSnapshot snapshot) {
    String modelId =
        "model:"
            + (!Utils.isEmpty(snapshot.getModelFilename())
                ? snapshot.getModelFilename()
                : snapshot.getModelName());
    ArchitectureNode modelNode =
        graph.getOrCreateNode(
            modelId,
            !Utils.isEmpty(snapshot.getModelName()) ? snapshot.getModelName() : modelId,
            ArchitectureNodeKind.MODEL,
            layerFor(snapshot.getModelLayer()));
    modelNode.setPath(snapshot.getModelFilename());
    modelNode.setDetailType(
        snapshot.getModelLayer() != null ? snapshot.getModelLayer().name() : "MODEL");

    for (TableLineage table : snapshot.getTables()) {
      if (table == null || Utils.isEmpty(table.getLogicalName())) {
        continue;
      }
      String tableId = tableNodeId(snapshot, table);
      ArchitectureNode tableNode =
          graph.getOrCreateNode(
              tableId,
              table.getLogicalName(),
              ArchitectureNodeKind.TABLE,
              layerFor(table.getLayer() != null ? table.getLayer() : snapshot.getModelLayer()));
      tableNode.setDetailType(table.getTableType());
      tableNode.setDescription(table.getDescription());
      if (!Utils.isEmpty(table.getPhysicalTableName())) {
        tableNode.property("physicalTable", table.getPhysicalTableName());
      }
      if (!Utils.isEmpty(table.getTargetDatabaseMetaName())) {
        tableNode.property("database", table.getTargetDatabaseMetaName());
        String dbId = "db:" + table.getTargetDatabaseMetaName();
        graph.getOrCreateNode(
            dbId,
            table.getTargetDatabaseMetaName(),
            ArchitectureNodeKind.DATABASE,
            ArchitectureLayer.TARGET);
        // No edges in inventory view — tables and databases are listed in lanes only.
      }

      for (TableSourceRef source : table.getSources()) {
        if (source == null || Utils.isEmpty(source.getName())) {
          continue;
        }
        String sourceId = sourceNodeId(source);
        ArchitectureNodeKind kind =
            source.getKind() == TableSourceKind.DV_SOURCE
                ? ArchitectureNodeKind.SOURCE
                : ArchitectureNodeKind.TABLE;
        ArchitectureLayer layer =
            kind == ArchitectureNodeKind.SOURCE
                ? ArchitectureLayer.SOURCE
                : layerFor(snapshot.getModelLayer());
        ArchitectureNode sourceNode =
            graph.getOrCreateNode(sourceId, source.getName(), kind, layer);
        if (source.getKind() != null) {
          sourceNode.setDetailType(source.getKind().name());
        }
        // No FLOWS_TO edges in inventory — use MODEL export for structural relationships.
      }
    }
  }

  private static String tableNodeId(LineageSnapshot snapshot, TableLineage table) {
    String physical =
        !Utils.isEmpty(table.getPhysicalTableName())
            ? table.getPhysicalTableName()
            : table.getLogicalName();
    String model =
        !Utils.isEmpty(table.getModelName())
            ? table.getModelName()
            : snapshot.getModelName();
    // Dedupe across models when physical name matches (cross-model shared hubs).
    return "table:" + physical.toLowerCase() + "@" + (model != null ? model : "unknown");
  }

  private static String sourceNodeId(TableSourceRef source) {
    if (!Utils.isEmpty(source.getCatalogKey())) {
      return "source:" + source.getCatalogKey();
    }
    String kind = source.getKind() != null ? source.getKind().name() : "REF";
    return "source:" + kind + ":" + source.getName();
  }

  private static ArchitectureLayer layerFor(LineageLayer layer) {
    if (layer == null) {
      return ArchitectureLayer.OTHER;
    }
    return switch (layer) {
      case DV -> ArchitectureLayer.DATA_VAULT;
      case BV -> ArchitectureLayer.BUSINESS_VAULT;
      case DM -> ArchitectureLayer.DIMENSIONAL;
      default -> ArchitectureLayer.OTHER;
    };
  }
}
