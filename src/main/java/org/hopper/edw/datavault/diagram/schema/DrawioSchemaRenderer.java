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
package org.hopper.edw.datavault.diagram.schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.architecture.ArchitectureGraph;
import org.hopper.edw.datavault.architecture.ArchitectureNode;

/**
 * Renders a {@link SchemaDiagram} as Draw.io {@code mxfile} XML. Uses ELK coordinates from an
 * optional {@link ArchitectureGraph} when present.
 */
public final class DrawioSchemaRenderer {

  private static final int NODE_WIDTH = 220;
  private static final int HEADER_HEIGHT = 28;
  private static final int ROW_HEIGHT = 18;
  private static final int MIN_HEIGHT = 48;
  private static final int GRID_GAP_X = 40;
  private static final int GRID_GAP_Y = 40;
  private static final int COLUMNS = 3;

  private DrawioSchemaRenderer() {}

  public static String render(
      SchemaDiagram diagram, ArchitectureGraph graph, SchemaRenderOptions options) {
    SchemaRenderOptions opts = options != null ? options : new SchemaRenderOptions();
    SchemaDiagram schema = diagram != null ? diagram : new SchemaDiagram();
    StringBuilder cells = new StringBuilder();
    cells.append("        <mxCell id=\"0\"/>\n");
    cells.append("        <mxCell id=\"1\" parent=\"0\"/>\n");

    int cellId = 2;
    Map<String, Integer> entityCellIds = new HashMap<>();
    int maxX = 800;
    int maxY = 600;
    List<SchemaEntity> entities = schema.getEntities();
    for (int i = 0; i < entities.size(); i++) {
      SchemaEntity entity = entities.get(i);
      if (entity == null || Utils.isEmpty(entity.getName())) {
        continue;
      }
      int id = cellId++;
      entityCellIds.put(entity.getName(), id);
      int[] box = boxFor(entity, graph, i, opts);
      int x = box[0];
      int y = box[1];
      int w = box[2];
      int h = box[3];
      maxX = Math.max(maxX, x + w + 40);
      maxY = Math.max(maxY, y + h + 40);
      cells
          .append("        <mxCell id=\"")
          .append(id)
          .append("\" value=\"")
          .append(escapeXml(cellLabel(entity, opts)))
          .append("\" style=\"")
          .append(styleFor(entity))
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

    for (SchemaRelation relation : schema.getRelations()) {
      if (relation == null) {
        continue;
      }
      Integer from = entityCellIds.get(relation.getFromName());
      Integer to = entityCellIds.get(relation.getToName());
      if (from == null || to == null) {
        continue;
      }
      String label = relation.getLabel() != null ? relation.getLabel() : "";
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

    String pageName =
        !Utils.isEmpty(schema.getTitle())
            ? schema.getTitle()
            : (graph != null && !Utils.isEmpty(graph.getName()) ? graph.getName() : "model");
    return """
        <mxfile host="hopper-edw" modified="1" agent="hopper-edw-diagram-export" version="22.0.0">
          <diagram id="model" name="%s">
            <mxGraphModel dx="1200" dy="800" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="%d" pageHeight="%d" math="0" shadow="0">
              <root>
        %s              </root>
            </mxGraphModel>
          </diagram>
        </mxfile>
        """
        .formatted(escapeXml(pageName), Math.max(maxX, 400), Math.max(maxY, 400), cells);
  }

  private static int[] boxFor(
      SchemaEntity entity, ArchitectureGraph graph, int index, SchemaRenderOptions opts) {
    int height = heightFor(entity, opts);
    ArchitectureNode node = findNode(graph, entity.getName());
    if (node != null && node.hasLayoutCoordinates()) {
      int w = node.getWidth() != null && node.getWidth() > 0 ? node.getWidth() : NODE_WIDTH;
      int h = node.getHeight() != null && node.getHeight() > height ? node.getHeight() : height;
      return new int[] {node.getX(), node.getY(), w, h};
    }
    int col = index % COLUMNS;
    int row = index / COLUMNS;
    int x = 40 + col * (NODE_WIDTH + GRID_GAP_X);
    int y = 40 + row * (height + GRID_GAP_Y);
    return new int[] {x, y, NODE_WIDTH, height};
  }

  private static ArchitectureNode findNode(ArchitectureGraph graph, String name) {
    if (graph == null || Utils.isEmpty(name)) {
      return null;
    }
    ArchitectureNode byId = graph.findNode("table:" + name);
    if (byId != null) {
      return byId;
    }
    for (ArchitectureNode node : graph.getNodes()) {
      if (name.equals(node.getName()) || name.equals(node.getId())) {
        return node;
      }
    }
    return null;
  }

  private static int heightFor(SchemaEntity entity, SchemaRenderOptions opts) {
    int rows = 0;
    if (opts.isIncludeColumnDetails()) {
      rows = entity.getFields().size();
    }
    return Math.max(MIN_HEIGHT, HEADER_HEIGHT + rows * ROW_HEIGHT);
  }

  private static String cellLabel(SchemaEntity entity, SchemaRenderOptions opts) {
    StringBuilder sb = new StringBuilder();
    sb.append(entity.getName());
    if (!Utils.isEmpty(entity.getStereotype())) {
      sb.append('\n').append('«').append(entity.getStereotype()).append('»');
    }
    if (opts.isIncludeColumnDetails()) {
      for (SchemaField field : entity.getFields()) {
        if (field == null || Utils.isEmpty(field.getName())) {
          continue;
        }
        sb.append('\n');
        if (field.isPrimaryKey()) {
          sb.append("PK ");
        } else if (field.isForeignKey()) {
          sb.append("FK ");
        }
        sb.append(field.getName());
        if (opts.isIncludeDataTypes() && !Utils.isEmpty(field.getType())) {
          sb.append(" : ").append(field.getType());
        }
      }
    }
    return sb.toString();
  }

  private static String styleFor(SchemaEntity entity) {
    String stereotype = entity.getStereotype();
    return "rounded=0;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacingLeft=6;spacingTop=4;fillColor="
        + SchemaStereotypes.headerColor(stereotype)
        + ";strokeColor="
        + SchemaStereotypes.borderColor(stereotype)
        + ";fontSize=11;";
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
