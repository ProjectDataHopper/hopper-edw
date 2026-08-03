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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;

/**
 * Emits a Draw.io / diagrams.net {@code .drawio} (mxfile) document from an {@link
 * ArchitectureGraph}.
 *
 * <ul>
 *   <li><b>Swimlane mode</b> (default): layered lanes with multi-row node wrapping.
 *   <li><b>Freeform mode</b> ({@link ArchitectureGraph#isFreeformLayout()}): ELK coordinates on
 *       nodes, structural edges for model diagrams.
 * </ul>
 */
public final class DrawioArchitectureExporter {

  private static final int NODE_WIDTH = 160;
  private static final int NODE_HEIGHT = 56;
  private static final int H_GAP = 24;
  private static final int V_GAP = 48;
  private static final int ROW_GAP = 20;
  private static final int MARGIN = 40;
  private static final int LANE_LABEL_HEIGHT = 28;

  /** Max content width of a swimlane before wrapping to the next row. */
  private static final int MAX_LANE_CONTENT_WIDTH = 1400;

  private DrawioArchitectureExporter() {}

  public static String export(ArchitectureGraph graph) {
    if (graph == null) {
      graph = new ArchitectureGraph();
      graph.setName("empty");
    }
    if (graph.isFreeformLayout()) {
      return exportFreeform(graph);
    }
    return exportSwimlanes(graph);
  }

  private static String exportFreeform(ArchitectureGraph graph) {
    StringBuilder cells = new StringBuilder();
    cells.append("        <mxCell id=\"0\"/>\n");
    cells.append("        <mxCell id=\"1\" parent=\"0\"/>\n");

    int cellId = 2;
    Map<String, Integer> nodeCellIds = new HashMap<>();
    int maxX = 800;
    int maxY = 600;

    for (ArchitectureNode node : graph.getNodes()) {
      int id = cellId++;
      nodeCellIds.put(node.getId(), id);
      int x = node.getX() != null ? node.getX() : MARGIN;
      int y = node.getY() != null ? node.getY() : MARGIN;
      int w = node.getWidth() != null && node.getWidth() > 0 ? node.getWidth() : NODE_WIDTH;
      int h = node.getHeight() != null && node.getHeight() > 0 ? node.getHeight() : NODE_HEIGHT;
      maxX = Math.max(maxX, x + w + MARGIN);
      maxY = Math.max(maxY, y + h + MARGIN);
      cells
          .append("        <mxCell id=\"")
          .append(id)
          .append("\" value=\"")
          .append(escapeXml(nodeLabel(node)))
          .append("\" style=\"")
          .append(styleFor(node))
          .append("\" vertex=\"1\" parent=\"1\">\n")
          .append("          <mxGeometry x=\"")
          .append(x)
          .append("\" y=\"")
          .append(y)
          .append("\" width=\"")
          .append(w)
          .append("\" height=\"")
          .append(h)
          .append("\" as=\"geometry\"/>\n")
          .append("        </mxCell>\n");
    }

    if (!graph.isOmitEdges()) {
      appendEdges(cells, graph, nodeCellIds, cellId);
    }

    return wrapMxfile(graph, cells, maxX, maxY);
  }

  private static String exportSwimlanes(ArchitectureGraph graph) {
    Map<ArchitectureLayer, List<ArchitectureNode>> lanes = new EnumMap<>(ArchitectureLayer.class);
    for (ArchitectureLayer layer : ArchitectureLayer.values()) {
      lanes.put(layer, new ArrayList<>());
    }
    for (ArchitectureNode node : graph.getNodes()) {
      ArchitectureLayer layer = node.getLayer() != null ? node.getLayer() : ArchitectureLayer.OTHER;
      lanes.get(layer).add(node);
    }

    StringBuilder cells = new StringBuilder();
    cells.append("        <mxCell id=\"0\"/>\n");
    cells.append("        <mxCell id=\"1\" parent=\"0\"/>\n");

    int cellId = 2;
    Map<String, Integer> nodeCellIds = new HashMap<>();
    int y = MARGIN;
    int pageMaxX = 800;

    for (ArchitectureLayer layer : ArchitectureLayer.values()) {
      List<ArchitectureNode> nodes = lanes.get(layer);
      if (nodes == null || nodes.isEmpty()) {
        continue;
      }

      // Multi-row wrap within lane
      List<List<ArchitectureNode>> rows = packRows(nodes, MAX_LANE_CONTENT_WIDTH);
      int contentWidth = 0;
      for (List<ArchitectureNode> row : rows) {
        int rowW = row.size() * (NODE_WIDTH + H_GAP) - H_GAP;
        contentWidth = Math.max(contentWidth, rowW);
      }
      int laneWidth = Math.max(contentWidth + 2 * MARGIN + 36, 400);
      int laneHeight =
          LANE_LABEL_HEIGHT
              + 24
              + rows.size() * NODE_HEIGHT
              + Math.max(0, rows.size() - 1) * ROW_GAP;

      cells
          .append("        <mxCell id=\"")
          .append(cellId++)
          .append("\" value=\"")
          .append(escapeXml(layerLabel(layer)))
          .append("\" style=\"swimlane;horizontal=0;startSize=28;fillColor=#f5f5f5;")
          .append("strokeColor=#b0b0b0;fontStyle=1;\" vertex=\"1\" parent=\"1\">\n")
          .append("          <mxGeometry x=\"")
          .append(MARGIN)
          .append("\" y=\"")
          .append(y)
          .append("\" width=\"")
          .append(laneWidth)
          .append("\" height=\"")
          .append(laneHeight)
          .append("\" as=\"geometry\"/>\n")
          .append("        </mxCell>\n");
      int laneParent = cellId - 1;
      pageMaxX = Math.max(pageMaxX, MARGIN + laneWidth + MARGIN);

      int rowY = 20;
      for (List<ArchitectureNode> row : rows) {
        int x = MARGIN + 36;
        for (ArchitectureNode node : row) {
          int id = cellId++;
          nodeCellIds.put(node.getId(), id);
          cells
              .append("        <mxCell id=\"")
              .append(id)
              .append("\" value=\"")
              .append(escapeXml(nodeLabel(node)))
              .append("\" style=\"")
              .append(styleFor(node))
              .append("\" vertex=\"1\" parent=\"")
              .append(laneParent)
              .append("\">\n")
              .append("          <mxGeometry x=\"")
              .append(x)
              .append("\" y=\"")
              .append(rowY)
              .append("\" width=\"")
              .append(NODE_WIDTH)
              .append("\" height=\"")
              .append(NODE_HEIGHT)
              .append("\" as=\"geometry\"/>\n")
              .append("        </mxCell>\n");
          x += NODE_WIDTH + H_GAP;
        }
        rowY += NODE_HEIGHT + ROW_GAP;
      }
      y += laneHeight + V_GAP;
    }

    if (!graph.isOmitEdges()) {
      cellId = appendEdges(cells, graph, nodeCellIds, cellId);
    }

    return wrapMxfile(graph, cells, pageMaxX, Math.max(y + MARGIN, 600));
  }

  /** Pack nodes into rows that stay under {@code maxContentWidth}. */
  static List<List<ArchitectureNode>> packRows(List<ArchitectureNode> nodes, int maxContentWidth) {
    List<List<ArchitectureNode>> rows = new ArrayList<>();
    if (nodes == null || nodes.isEmpty()) {
      return rows;
    }
    int perRow = Math.max(1, (maxContentWidth + H_GAP) / (NODE_WIDTH + H_GAP));
    List<ArchitectureNode> current = new ArrayList<>();
    for (ArchitectureNode node : nodes) {
      if (current.size() >= perRow) {
        rows.add(current);
        current = new ArrayList<>();
      }
      current.add(node);
    }
    if (!current.isEmpty()) {
      rows.add(current);
    }
    return rows;
  }

  private static int appendEdges(
      StringBuilder cells, ArchitectureGraph graph, Map<String, Integer> nodeCellIds, int cellId) {
    for (ArchitectureEdge edge : graph.getEdges()) {
      Integer from = nodeCellIds.get(edge.getFromNodeId());
      Integer to = nodeCellIds.get(edge.getToNodeId());
      if (from == null || to == null) {
        continue;
      }
      // Prefer short / empty labels on freeform model diagrams to reduce clutter
      String label = edge.getLabel() != null ? edge.getLabel() : "";
      if (!graph.isFreeformLayout() && edge.getKind() != null && Utils.isEmpty(label)) {
        label = edge.getKind().name().toLowerCase();
      }
      if (graph.isFreeformLayout() && ArchitecturePathSupport.looksLikeFilesystemPath(label)) {
        label = "";
      }
      cells
          .append("        <mxCell id=\"")
          .append(cellId++)
          .append("\" value=\"")
          .append(escapeXml(label))
          .append("\" style=\"edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;")
          .append("jettySize=auto;html=1;endArrow=block;endFill=1;strokeColor=#666666;\"")
          .append(" edge=\"1\" parent=\"1\" source=\"")
          .append(from)
          .append("\" target=\"")
          .append(to)
          .append("\">\n")
          .append("          <mxGeometry relative=\"1\" as=\"geometry\"/>\n")
          .append("        </mxCell>\n");
    }
    return cellId;
  }

  private static String wrapMxfile(
      ArchitectureGraph graph, StringBuilder cells, int pageWidth, int pageHeight) {
    String pageName =
        !Utils.isEmpty(graph.getName())
            ? graph.getName()
            : (graph.getViewType() != null ? graph.getViewType().name() : "Architecture");

    return """
        <mxfile host="hop-datavault" modified="1" agent="hop-datavault-architecture-export" version="22.0.0">
          <diagram id="architecture" name="%s">
            <mxGraphModel dx="1200" dy="800" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="%d" pageHeight="%d" math="0" shadow="0">
              <root>
        %s              </root>
            </mxGraphModel>
          </diagram>
        </mxfile>
        """
        .formatted(escapeXml(pageName), Math.max(pageWidth, 400), Math.max(pageHeight, 400), cells);
  }

  private static String nodeLabel(ArchitectureNode node) {
    StringBuilder sb = new StringBuilder();
    sb.append(node.getName() != null ? node.getName() : node.getId());
    if (!Utils.isEmpty(node.getDetailType())) {
      sb.append('\n').append(node.getDetailType());
    }
    return sb.toString();
  }

  private static String styleFor(ArchitectureNode node) {
    String fill =
        switch (node.getKind() != null ? node.getKind() : ArchitectureNodeKind.OTHER) {
          case WORKFLOW -> "#dae8fc";
          case CAPABILITY -> "#fff2cc";
          case MODEL -> "#d5e8d4";
          case TABLE -> "#e1d5e7";
          case SOURCE -> "#f8cecc";
          case DATABASE -> "#f5f5f5";
          case DATASET -> "#ffe6cc";
          case CATALOG -> "#cce5ff";
          default -> "#ffffff";
        };
    // Refine table colors by vault type when detail is set
    if (node.getKind() == ArchitectureNodeKind.TABLE && !Utils.isEmpty(node.getDetailType())) {
      String d = node.getDetailType().toUpperCase();
      if (d.contains("HUB")) {
        fill = "#d5e8d4";
      } else if (d.contains("LINK") || d.contains("LNK")) {
        fill = "#dae8fc";
      } else if (d.contains("SAT")) {
        fill = "#fff2cc";
      } else if (d.contains("FACT")) {
        fill = "#e1d5e7";
      } else if (d.contains("DIM")) {
        fill = "#ffe6cc";
      }
    }
    return "rounded=1;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor="
        + fill
        + ";strokeColor=#666666;fontSize=11;";
  }

  private static String layerLabel(ArchitectureLayer layer) {
    return switch (layer) {
      case SOURCE -> "Sources";
      case CONTROL -> "Controls (catalog / schema / quality)";
      case ORCHESTRATION -> "Orchestration";
      case DATA_VAULT -> "Data Vault";
      case BUSINESS_VAULT -> "Business Vault";
      case DIMENSIONAL -> "Dimensional";
      case TARGET -> "Targets / databases";
      case OPS -> "Operations / lineage / maps";
      case OTHER -> "Other";
    };
  }

  private static String escapeXml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
