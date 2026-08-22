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
package org.hopper.edw.datavault.architecture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.layout.ElkGraphLayout;
import org.hopper.edw.datavault.layout.ElkLayout;
import org.hopper.edw.datavault.layout.ElkLayoutBox;
import org.hopper.edw.datavault.layout.ElkLayoutEdge;
import org.hopper.edw.datavault.layout.ElkLayoutNode;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;

/**
 * Builds freeform MODEL {@link ArchitectureGraph}s with ELK table layout.
 *
 * <p><b>Primary use:</b> aggregate diagrams across <em>several</em> model files of the same layer
 * (all DV, all BV, or all dimensional) so enterprise multi-file models appear as one Draw.io
 * diagram — not one file per model.
 */
public final class ArchitectureGraphFromModel {

  private ArchitectureGraphFromModel() {}

  public static ArchitectureGraph fromDataVault(DataVaultModel model, IVariables variables)
      throws HopException {
    List<DataVaultModel> models = new ArrayList<>();
    if (model != null) {
      models.add(model);
    }
    return fromDataVaultModels(models, variables);
  }

  /** Aggregate Data Vault tables and relationships across multiple {@code .hdv} files. */
  public static ArchitectureGraph fromDataVaultModels(
      Collection<DataVaultModel> models, IVariables variables) throws HopException {
    ArchitectureGraph graph =
        baseGraph(
            aggregateName(models, DataVaultModel::getName, "data-vault"),
            "DATA_VAULT",
            models == null ? 0 : models.size());
    if (models == null || models.isEmpty()) {
      return graph;
    }
    ElkGraphLayout layoutGraph = ElkGraphLayout.fromDataVaultModels(models);
    Map<String, ElkLayoutBox> boxes = layoutGraph.layoutToBoxes(ElkLayout.createDefault());

    for (DataVaultModel model : models) {
      if (model == null || model.getTables() == null) {
        continue;
      }
      for (IDvTable table : model.getTables()) {
        if (table == null || Utils.isEmpty(table.getName())) {
          continue;
        }
        ArchitectureNode node =
            graph.getOrCreateNode(
                tableNodeId(table.getName()),
                table.getName(),
                ArchitectureNodeKind.TABLE,
                ArchitectureLayer.DATA_VAULT);
        if (Utils.isEmpty(node.getDetailType())) {
          String type = table.getTableType() != null ? table.getTableType().name() : "TABLE";
          node.setDetailType(type);
        }
        if (!Utils.isEmpty(model.getName())) {
          node.property("model", model.getName());
        }
        if (!node.hasLayoutCoordinates()) {
          applyBox(node, boxes.get(table.getName()));
        }
      }
    }
    addStructuralEdges(graph, layoutGraph);
    ArchitecturePathSupport.portableizeGraph(graph, variables);
    return graph;
  }

  public static ArchitectureGraph fromBusinessVault(
      BusinessVaultModel model, DataVaultModel effectiveDv, IVariables variables)
      throws HopException {
    List<BusinessVaultModel> bv = new ArrayList<>();
    if (model != null) {
      bv.add(model);
    }
    List<DataVaultModel> dv = new ArrayList<>();
    if (effectiveDv != null) {
      dv.add(effectiveDv);
    }
    return fromBusinessVaultModels(bv, dv, variables);
  }

  /** Aggregate Business Vault (and referenced DV) tables across multiple model files. */
  public static ArchitectureGraph fromBusinessVaultModels(
      Collection<BusinessVaultModel> bvModels,
      Collection<DataVaultModel> effectiveDvModels,
      IVariables variables)
      throws HopException {
    ArchitectureGraph graph =
        baseGraph(
            aggregateName(bvModels, BusinessVaultModel::getName, "business-vault"),
            "BUSINESS_VAULT",
            bvModels == null ? 0 : bvModels.size());
    if (bvModels == null || bvModels.isEmpty()) {
      return graph;
    }
    ElkGraphLayout layoutGraph =
        ElkGraphLayout.fromBusinessVaultModels(bvModels, effectiveDvModels);
    Map<String, ElkLayoutBox> boxes = layoutGraph.layoutToBoxes(ElkLayout.createDefault());

    for (BusinessVaultModel model : bvModels) {
      if (model == null || model.getTables() == null) {
        continue;
      }
      for (IBvTable table : model.getTables()) {
        if (table == null || Utils.isEmpty(table.getName())) {
          continue;
        }
        ArchitectureNode node =
            graph.getOrCreateNode(
                tableNodeId(table.getName()),
                table.getName(),
                ArchitectureNodeKind.TABLE,
                ArchitectureLayer.BUSINESS_VAULT);
        if (Utils.isEmpty(node.getDetailType())) {
          node.setDetailType("BV");
        }
        if (!Utils.isEmpty(model.getName())) {
          node.property("model", model.getName());
        }
        if (!node.hasLayoutCoordinates()) {
          applyBox(node, boxes.get(table.getName()));
        }
      }
    }
    // DV tables present only as layout/edge targets
    for (ElkLayoutNode layoutNode : layoutGraph.getNodes()) {
      if (graph.findNode(tableNodeId(layoutNode.getId())) != null) {
        continue;
      }
      ArchitectureNode node =
          graph.getOrCreateNode(
              tableNodeId(layoutNode.getId()),
              layoutNode.getLabel() != null ? layoutNode.getLabel() : layoutNode.getId(),
              ArchitectureNodeKind.TABLE,
              ArchitectureLayer.DATA_VAULT);
      node.setDetailType("DV");
      applyBox(node, boxes.get(layoutNode.getId()));
    }
    addStructuralEdges(graph, layoutGraph);
    ArchitecturePathSupport.portableizeGraph(graph, variables);
    return graph;
  }

  public static ArchitectureGraph fromDimensional(DimensionalModel model, IVariables variables)
      throws HopException {
    List<DimensionalModel> models = new ArrayList<>();
    if (model != null) {
      models.add(model);
    }
    return fromDimensionalModels(models, variables);
  }

  /** Aggregate dimensional tables across multiple {@code .hdm} files. */
  public static ArchitectureGraph fromDimensionalModels(
      Collection<DimensionalModel> models, IVariables variables) throws HopException {
    ArchitectureGraph graph =
        baseGraph(
            aggregateName(models, DimensionalModel::getName, "dimensional"),
            "DIMENSIONAL",
            models == null ? 0 : models.size());
    if (models == null || models.isEmpty()) {
      return graph;
    }
    ElkGraphLayout layoutGraph = ElkGraphLayout.fromDimensionalModels(models);
    Map<String, ElkLayoutBox> boxes = layoutGraph.layoutToBoxes(ElkLayout.createDefault());

    for (DimensionalModel model : models) {
      if (model == null || model.getTables() == null) {
        continue;
      }
      for (IDmTable table : model.getTables()) {
        if (table == null || Utils.isEmpty(table.getName())) {
          continue;
        }
        ArchitectureNode node =
            graph.getOrCreateNode(
                tableNodeId(table.getName()),
                table.getName(),
                ArchitectureNodeKind.TABLE,
                ArchitectureLayer.DIMENSIONAL);
        if (Utils.isEmpty(node.getDetailType())) {
          String type = table.getClass().getSimpleName();
          if (type.startsWith("Dm")) {
            type = type.substring(2);
          }
          node.setDetailType(type);
        }
        if (!Utils.isEmpty(model.getName())) {
          node.property("model", model.getName());
        }
        if (!node.hasLayoutCoordinates()) {
          applyBox(node, boxes.get(table.getName()));
        }
      }
    }
    addStructuralEdges(graph, layoutGraph);
    ArchitecturePathSupport.portableizeGraph(graph, variables);
    return graph;
  }

  private static ArchitectureGraph baseGraph(String name, String detail, int modelCount) {
    ArchitectureGraph graph = new ArchitectureGraph();
    graph.setViewType(ArchitectureViewType.MODEL);
    graph.setFreeformLayout(true);
    graph.setOmitEdges(false);
    graph.setName(!Utils.isEmpty(name) ? name : "model");
    graph.setDescription(
        "Aggregated "
            + detail
            + " model diagram ("
            + modelCount
            + " model file(s)) with ELK layout");
    return graph;
  }

  private static <T> String aggregateName(
      Collection<T> models, java.util.function.Function<T, String> nameFn, String fallback) {
    if (models == null || models.isEmpty()) {
      return fallback;
    }
    if (models.size() == 1) {
      T only = models.iterator().next();
      if (only != null) {
        String n = nameFn.apply(only);
        if (!Utils.isEmpty(n)) {
          return n;
        }
      }
    }
    return fallback;
  }

  private static void applyBox(ArchitectureNode node, ElkLayoutBox box) {
    if (box != null) {
      node.layoutBox(box.x(), box.y(), box.width(), box.height());
    }
  }

  private static void addStructuralEdges(ArchitectureGraph graph, ElkGraphLayout layoutGraph) {
    for (ElkLayoutEdge edge : layoutGraph.getEdges()) {
      if (edge == null || Utils.isEmpty(edge.getFromId()) || Utils.isEmpty(edge.getToId())) {
        continue;
      }
      String fromId = tableNodeId(edge.getFromId());
      String toId = tableNodeId(edge.getToId());
      if (graph.findNode(fromId) == null || graph.findNode(toId) == null) {
        continue;
      }
      graph.addEdge(fromId, toId, ArchitectureEdgeKind.REFERENCES, null);
    }
  }

  private static String tableNodeId(String tableName) {
    return "table:" + tableName;
  }
}
