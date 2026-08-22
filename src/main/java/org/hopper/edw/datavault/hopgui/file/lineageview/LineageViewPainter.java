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
package org.hopper.edw.datavault.hopgui.file.lineageview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.IGc.ELineStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.ConnectionAnchors;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphTableNameHitArea;
import org.hopper.edw.datavault.hopgui.file.vault.BasePainter;
import org.hopper.edw.datavault.layout.ElkLayoutBox;
import org.hopper.edw.datavault.lineageview.LineageViewElkSupport;
import org.hopper.edw.datavault.lineageview.LineageViewLabelFit;
import org.hopper.edw.datavault.lineageview.LineageViewOpsBadge;
import org.hopper.edw.datavault.lineageview.LineageViewOpsOverlay;
import org.hopper.edw.datavault.lineageview.backend.LineageEdge;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageGraphLayer;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;

/** Paints a table-level lineage graph. */
public class LineageViewPainter extends BasePainter {

  private static final int TEXT_PAD = 8;
  private static final int CONNECTOR_SIZE = 6;

  private final LineageGraph graph;
  private final Map<String, ElkLayoutBox> boxes;
  private final String selectedNodeId;
  private final LineageViewOpsOverlay opsOverlay;
  private String bannerText;
  private boolean bannerError;

  public LineageViewPainter(
      LineageGraph graph,
      Map<String, ElkLayoutBox> boxes,
      String selectedNodeId,
      IGc gc,
      IVariables variables,
      int width,
      int height) {
    this(graph, boxes, selectedNodeId, gc, variables, width, height, LineageViewOpsOverlay.empty());
  }

  public LineageViewPainter(
      LineageGraph graph,
      Map<String, ElkLayoutBox> boxes,
      String selectedNodeId,
      IGc gc,
      IVariables variables,
      int width,
      int height,
      LineageViewOpsOverlay opsOverlay) {
    super(
        gc,
        variables != null ? variables : Variables.getADefaultVariableSpace(),
        graph,
        new Point(width, height));
    this.graph = graph;
    this.boxes = boxes != null ? boxes : Map.of();
    this.selectedNodeId = selectedNodeId;
    this.opsOverlay = opsOverlay != null ? opsOverlay : LineageViewOpsOverlay.empty();
    this.areaOwners = new ArrayList<>();
    this.offset = new DPoint(0, 0);
  }

  public void setBanner(String bannerText, boolean bannerError) {
    this.bannerText = bannerText;
    this.bannerError = bannerError;
  }

  public void draw() {
    if (gc == null) {
      return;
    }
    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);
    if (graph != null) {
      drawGraph();
    }
    gc.setTransform(0.0f, 0.0f, 1.0f);
    if (!Utils.isEmpty(bannerText)) {
      gc.setForeground(bannerError ? EColor.RED : EColor.DARKGRAY);
      gc.drawText(bannerText, 16, 16);
    }
  }

  private void drawGraph() {
    gc.setTransform(0.0f, 0.0f, magnification);
    if (gridSize > 1) {
      drawGrid();
    }
    gc.setLineStyle(ELineStyle.SOLID);
    gc.setForeground(EColor.DARKGRAY);
    gc.setLineWidth(1);
    List<Point> connectors = new ArrayList<>();
    for (LineageEdge edge : graph.getEdgesOrEmpty()) {
      ElkLayoutBox from = boxes.get(edge.getFromNodeId());
      ElkLayoutBox to = boxes.get(edge.getToNodeId());
      if (from == null || to == null) {
        continue;
      }
      ConnectionAnchors anchors =
          ModelGraphConnectionGeometry.borderAnchorsBetween(boundsOf(from), boundsOf(to));
      Point fromPt = real2screen(anchors.from().x, anchors.from().y);
      Point toPt = real2screen(anchors.to().x, anchors.to().y);
      gc.drawLine(fromPt.x, fromPt.y, toPt.x, toPt.y);
      connectors.add(fromPt);
      connectors.add(toPt);
    }
    for (LineageNode node : graph.getNodesOrEmpty()) {
      ElkLayoutBox box = boxes.get(node.getId());
      if (box == null) {
        continue;
      }
      drawNode(node, box);
    }
    for (Point connector : connectors) {
      drawConnector(connector);
    }
    gc.setTransform(0.0f, 0.0f, 1.0f);
    drawNavigationView();
  }

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (graph == null) {
      return;
    }
    for (LineageNode node : graph.getNodesOrEmpty()) {
      ElkLayoutBox box = boxes.get(node.getId());
      if (box == null) {
        continue;
      }
      int w = Math.max(4, (int) Math.ceil(box.width() * scaleX));
      int h = Math.max(4, (int) Math.ceil(box.height() * scaleY));
      int x = (int) (graphX + box.x() * scaleX);
      int y = (int) (graphY + box.y() * scaleY);
      gc.setBackground(colorFor(node));
      gc.setForeground(EColor.DARKGRAY);
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }
  }

  private void drawNode(LineageNode node, ElkLayoutBox box) {
    boolean seed = node.getId() != null && node.getId().equals(graph.getSeedNodeId());
    boolean selected = node.getId() != null && node.getId().equals(selectedNodeId);
    Point loc = real2screen(box.x(), box.y());
    int x = loc.x;
    int y = loc.y;
    int width = box.width();
    int height = box.height();
    gc.setBackground(colorFor(node));
    gc.setForeground(selected || seed ? EColor.BLUE : EColor.BLACK);
    gc.setLineWidth(seed || selected ? 2 : 1);
    gc.fillRoundRectangle(x, y, width, height, 8, 8);
    gc.drawRoundRectangle(x, y, width, height, 8, 8);
    gc.setForeground(EColor.BLACK);
    gc.setFont(EFont.GRAPH);
    int textWidth = Math.max(0, width - TEXT_PAD * 2);
    String rawTitle = node.getName() != null ? node.getName() : node.getId();
    String title = fitLabel(rawTitle, textWidth);
    String subtitle =
        (node.getKind() != null ? node.getKind().getCode() : "")
            + (node.getLayer() != null ? " · " + node.getLayer().getCode() : "");
    int nameX = x + TEXT_PAD;
    int nameY = y + TEXT_PAD;
    gc.drawText(title, nameX, nameY);
    Point nameExtent = gc.textExtent(title);
    boolean underline = node.getId() != null && node.getId().equals(mouseOverTableName);
    if (underline && nameExtent != null) {
      gc.drawLine(nameX, nameY + nameExtent.y, nameX + nameExtent.x, nameY + nameExtent.y);
    }
    gc.drawText(fitLabel(subtitle, textWidth), x + TEXT_PAD, y + 28);
    LineageViewOpsBadge badge = opsOverlay.badgeFor(node);
    if (badge != null && !Utils.isEmpty(badge.label())) {
      gc.setForeground(badge.slow() ? EColor.RED : badge.stale() ? EColor.DARKGRAY : EColor.BLUE);
      gc.drawText(fitLabel(badge.label(), textWidth), x + TEXT_PAD, y + 44);
    }
    if (areaOwners != null) {
      // String owner → Hop Web AreaOwner JSON kind=label; LineageNode stays in parent for hit
      // tests.
      areaOwners.add(
          new AreaOwner(AreaType.TRANSFORM_ICON, x, y, width, height, offset, node, node.getId()));
      ModelGraphTableNameHitArea.Bounds nameHit =
          ModelGraphTableNameHitArea.bounds(nameX, nameY, nameExtent);
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_NAME,
              nameHit.x(),
              nameHit.y(),
              nameHit.width(),
              nameHit.height(),
              offset,
              node,
              title));
    }
  }

  private void drawConnector(Point p) {
    if (p == null) {
      return;
    }
    int x = p.x - CONNECTOR_SIZE / 2;
    int y = p.y - CONNECTOR_SIZE / 2;
    gc.setBackground(EColor.DARKGRAY);
    gc.setForeground(EColor.DARKGRAY);
    gc.fillRectangle(x, y, CONNECTOR_SIZE, CONNECTOR_SIZE);
  }

  private static Bounds boundsOf(ElkLayoutBox box) {
    return new Bounds(box.x(), box.y(), box.width(), box.height());
  }

  private String fitLabel(String text, int maxWidth) {
    return LineageViewLabelFit.fitTail(
        text,
        maxWidth,
        value -> {
          Point extent = gc.textExtent(value);
          return extent != null ? extent.x : 0;
        });
  }

  private static EColor colorFor(LineageNode node) {
    if (node.getKind() == LineageNodeKind.JOB) {
      return EColor.LIGHTBLUE;
    }
    if (node.getLayer() == LineageGraphLayer.SOURCE) {
      return EColor.LIGHTGRAY;
    }
    return EColor.BACKGROUND;
  }

  public Point computeMaximum() {
    return LineageViewSvgPainter.maximumOf(boxes);
  }

  public static int cardWidth() {
    return LineageViewElkSupport.NODE_WIDTH;
  }

  public static int cardHeight() {
    return LineageViewElkSupport.NODE_HEIGHT;
  }
}
