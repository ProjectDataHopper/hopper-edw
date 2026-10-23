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
import java.util.Set;
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
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphSvgIconCache;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphTableNameHitArea;
import org.hopper.edw.datavault.hopgui.file.vault.BasePainter;
import org.hopper.edw.datavault.layout.ElkLayoutBox;
import org.hopper.edw.datavault.lineageview.LineageViewElkSupport;
import org.hopper.edw.datavault.lineageview.LineageViewLabelFit;
import org.hopper.edw.datavault.lineageview.LineageViewNodeAppearance;
import org.hopper.edw.datavault.lineageview.LineageViewOpsBadge;
import org.hopper.edw.datavault.lineageview.LineageViewOpsOverlay;
import org.hopper.edw.datavault.lineageview.backend.LineageEdge;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;

/** Paints a table-level lineage graph. */
public class LineageViewPainter extends BasePainter {

  private static final int ICON_SIZE = 32;
  private static final int CARD_MARGIN = 8;
  private static final int CORNER_ARC = 20;
  private static final int CHEVRON = 14;
  private static final int TOGGLE_HIT = 16;
  private static final int CONNECTOR_SIZE = 6;

  private final LineageGraph graph;
  private final Map<String, ElkLayoutBox> boxes;
  private final String selectedNodeId;
  private final LineageViewOpsOverlay opsOverlay;
  private Set<String> compactNodeIds = Set.of();
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

  public void setCompactNodeIds(Set<String> compactNodeIds) {
    this.compactNodeIds = compactNodeIds != null ? compactNodeIds : Set.of();
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
      drawElbow(fromPt, toPt, connectors);
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
      LineageViewNodeAppearance look = LineageViewNodeAppearance.of(graph, node);
      boolean compact = isCompact(node);
      int arc = compact ? Math.min(w, h) : Math.max(2, (int) Math.round(CORNER_ARC * scaleX));
      gc.setBackground(look.fill());
      gc.setForeground(EColor.DARKGRAY);
      gc.fillRoundRectangle(x, y, w, h, arc, arc);
      gc.drawRoundRectangle(x, y, w, h, arc, arc);
    }
  }

  private void drawNode(LineageNode node, ElkLayoutBox box) {
    boolean seed = node.getId() != null && node.getId().equals(graph.getSeedNodeId());
    boolean selected = node.getId() != null && node.getId().equals(selectedNodeId);
    boolean compact = isCompact(node);
    LineageViewNodeAppearance look = LineageViewNodeAppearance.of(graph, node);
    Point loc = real2screen(box.x(), box.y());
    int x = loc.x;
    int y = loc.y;
    int width = box.width();
    int height = box.height();
    int arc = compact ? Math.min(width, height) : CORNER_ARC;
    gc.setBackground(look.fill());
    gc.setForeground(seed || selected ? EColor.BLUE : EColor.BLACK);
    gc.setLineWidth(selected ? 3 : seed ? 2 : 1);
    gc.fillRoundRectangle(x, y, width, height, arc, arc);
    gc.drawRoundRectangle(x, y, width, height, arc, arc);
    gc.setLineWidth(1);

    int iconX = compact ? x + (width - ICON_SIZE) / 2 : x + CARD_MARGIN;
    int iconY = compact ? y + (height - ICON_SIZE) / 2 : y + CARD_MARGIN;
    drawLogo(look, iconX, iconY);

    String title = null;
    int nameX = 0;
    int nameY = 0;
    Point nameExtent = null;
    if (!compact) {
      gc.setForeground(EColor.BLACK);
      gc.setFont(EFont.GRAPH);
      int textX = x + CARD_MARGIN + ICON_SIZE + CARD_MARGIN;
      int textRight = x + width - CARD_MARGIN - TOGGLE_HIT;
      int textWidth = Math.max(0, textRight - textX);
      String rawTitle = node.getName() != null ? node.getName() : node.getId();
      title = fitLabel(rawTitle, textWidth);
      nameX = textX;
      nameY = y + CARD_MARGIN;
      gc.drawText(title, nameX, nameY, true);
      nameExtent = gc.textExtent(title);
      boolean underline = node.getId() != null && node.getId().equals(mouseOverTableName);
      if (underline && nameExtent != null) {
        gc.drawLine(nameX, nameY + nameExtent.y, nameX + nameExtent.x, nameY + nameExtent.y);
      }
      drawSecondaryLine(node, look, textX, textRight, nameY, nameExtent);
      gc.setFont(EFont.SMALL);
      gc.setForeground(EColor.BLACK);
      String typeLabel = fitLabel(look.typeLabel(), Math.max(0, textRight - (x + CARD_MARGIN)));
      gc.drawText(typeLabel, x + CARD_MARGIN, iconY + ICON_SIZE + 2, true);
      gc.setFont(EFont.GRAPH);
    }

    int toggleX = x + width - TOGGLE_HIT;
    int toggleY = y;
    drawChevron(toggleX + (TOGGLE_HIT - CHEVRON) / 2, toggleY + 1, compact);

    if (areaOwners != null) {
      // String owner → Hop Web AreaOwner JSON kind=label; LineageNode stays in parent for hit
      // tests. The chevron is added last so it wins over the card body.
      areaOwners.add(
          new AreaOwner(AreaType.TRANSFORM_ICON, x, y, width, height, offset, node, node.getId()));
      if (!compact && nameExtent != null) {
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
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_INFO_ICON,
              toggleX,
              toggleY,
              TOGGLE_HIT,
              TOGGLE_HIT,
              offset,
              node,
              node.getId()));
    }
  }

  private void drawSecondaryLine(
      LineageNode node,
      LineageViewNodeAppearance look,
      int textX,
      int textRight,
      int nameY,
      Point nameExtent) {
    int secondaryY = nameY + (nameExtent != null ? nameExtent.y : 0) + 2;
    LineageViewOpsBadge badge = opsOverlay.badgeFor(node);
    String duration = badge != null ? badge.label() : null;
    gc.setFont(EFont.SMALL);
    int durationWidth = 0;
    if (!Utils.isEmpty(duration)) {
      Point durationExtent = gc.textExtent(duration);
      durationWidth = durationExtent != null ? durationExtent.x : 0;
    }
    int layerMax = Math.max(0, textRight - textX - durationWidth - (durationWidth > 0 ? 6 : 0));
    gc.setForeground(EColor.DARKGRAY);
    gc.drawText(fitLabel(look.layerLabel(), layerMax), textX, secondaryY, true);
    if (!Utils.isEmpty(duration)) {
      gc.setForeground(badge.slow() ? EColor.RED : badge.stale() ? EColor.DARKGRAY : EColor.BLUE);
      gc.drawText(duration, textRight - durationWidth, secondaryY, true);
    }
    gc.setFont(EFont.GRAPH);
  }

  private void drawLogo(LineageViewNodeAppearance look, int iconX, int iconY) {
    boolean drawn =
        ModelGraphSvgIconCache.drawIcon(
            gc,
            look.iconClassLoader(),
            look.iconPath(),
            iconX,
            iconY,
            ICON_SIZE,
            ICON_SIZE,
            magnification);
    if (!drawn) {
      gc.setBackground(EColor.BLUE);
      gc.fillRectangle(iconX, iconY, ICON_SIZE, ICON_SIZE);
    }
  }

  /** Inward chevron on an expanded card, outward chevron on a compact card. */
  private void drawChevron(int x, int y, boolean compact) {
    gc.setForeground(EColor.DARKGRAY);
    gc.setLineWidth(2);
    int midY = y + CHEVRON / 2;
    if (compact) {
      gc.drawPolyline(new int[] {x + 2, y + 2, x + CHEVRON - 2, midY, x + 2, y + CHEVRON - 2});
    } else {
      gc.drawPolyline(
          new int[] {x + CHEVRON - 2, y + 2, x + 2, midY, x + CHEVRON - 2, y + CHEVRON - 2});
    }
    gc.setLineWidth(1);
  }

  private void drawElbow(Point fromPt, Point toPt, List<Point> joints) {
    if (fromPt == null || toPt == null) {
      return;
    }
    int midX = (fromPt.x + toPt.x) / 2;
    boolean straight = Math.abs(fromPt.x - toPt.x) < 4 || Math.abs(fromPt.y - toPt.y) < 4;
    if (straight) {
      gc.drawLine(fromPt.x, fromPt.y, toPt.x, toPt.y);
      joints.add(fromPt);
      joints.add(toPt);
      return;
    }
    gc.drawPolyline(new int[] {fromPt.x, fromPt.y, midX, fromPt.y, midX, toPt.y, toPt.x, toPt.y});
    joints.add(fromPt);
    joints.add(new Point(midX, fromPt.y));
    joints.add(new Point(midX, toPt.y));
    joints.add(toPt);
  }

  private void drawConnector(Point p) {
    if (p == null) {
      return;
    }
    int x = p.x - CONNECTOR_SIZE / 2;
    int y = p.y - CONNECTOR_SIZE / 2;
    gc.setBackground(EColor.DARKGRAY);
    gc.setForeground(EColor.DARKGRAY);
    gc.fillRoundRectangle(x, y, CONNECTOR_SIZE, CONNECTOR_SIZE, CONNECTOR_SIZE, CONNECTOR_SIZE);
  }

  private boolean isCompact(LineageNode node) {
    return node != null && node.getId() != null && compactNodeIds.contains(node.getId());
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
