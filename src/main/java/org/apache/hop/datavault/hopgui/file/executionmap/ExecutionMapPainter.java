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
package org.apache.hop.datavault.hopgui.file.executionmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.IGc.ELineStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.command.svg.ExecutionMapExportScope;
import org.apache.hop.datavault.executionmap.ExecutionMapEdgeRouter;
import org.apache.hop.datavault.executionmap.ExecutionMapFocusContext;
import org.apache.hop.datavault.executionmap.ExecutionMapLayoutOptions;
import org.apache.hop.datavault.executionmap.ExecutionMapLineStyle;
import org.apache.hop.datavault.executionmap.ExecutionMapMetrics;
import org.apache.hop.datavault.executionmap.ExecutionMapNodeCardLayout;
import org.apache.hop.datavault.executionmap.ExecutionMapNodeCardMetrics;
import org.apache.hop.datavault.executionmap.ExecutionMapNodeColors;
import org.apache.hop.datavault.executionmap.ExecutionMapViewFilter;
import org.apache.hop.datavault.executionmap.ExecutionMapViewSupport;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphTableNameHitArea;
import org.apache.hop.datavault.hopgui.file.vault.BasePainter;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdge;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;

/** Paints a read-only execution map graph. */
@Getter
@Setter
public class ExecutionMapPainter extends BasePainter {

  private static final int LEGACY_NODE_WIDTH = ExecutionMapMetrics.MIN_NODE_WIDTH;
  private static final int LEGACY_NODE_HEIGHT = ExecutionMapMetrics.MIN_NODE_HEIGHT;
  private static final int CONTAINER_BOUNDS_PADDING = 12;

  private final ExecutionMapDocument document;
  private final ExecutionMapLayoutOptions layoutOptions;
  private final ExecutionMapFocusContext focusContext;
  private final ExecutionMapExportScope exportScope;
  private final Map<String, ExecutionMapNode> nodeById = new HashMap<>();
  private final Map<String, List<ExecutionMapNode>> childrenByParent = new HashMap<>();

  private String mouseOverNodeName;
  private Map<String, ExecutionMapNodeCardMetrics> cardMetricsById = Map.of();
  private List<ExecutionMapNode> visibleNodes = List.of();
  private List<ExecutionMapEdge> visibleEdges = List.of();

  public ExecutionMapPainter(
      ExecutionMapDocument document, IGc gc, IVariables variables, int width, int height) {
    this(document, gc, variables, width, height, ExecutionMapLayoutOptions.DEFAULT);
  }

  public ExecutionMapPainter(
      ExecutionMapDocument document,
      IGc gc,
      IVariables variables,
      int width,
      int height,
      ExecutionMapLayoutOptions layoutOptions) {
    this(
        document,
        gc,
        variables,
        width,
        height,
        layoutOptions,
        new ExecutionMapFocusContext(),
        ExecutionMapExportScope.FOCUSED);
  }

  public ExecutionMapPainter(
      ExecutionMapDocument document,
      IGc gc,
      IVariables variables,
      int width,
      int height,
      ExecutionMapLayoutOptions layoutOptions,
      ExecutionMapFocusContext focusContext,
      ExecutionMapExportScope exportScope) {
    super(gc, variables, document, new Point(width, height));
    this.document = document;
    this.layoutOptions = layoutOptions != null ? layoutOptions : ExecutionMapLayoutOptions.DEFAULT;
    this.focusContext = focusContext != null ? focusContext : new ExecutionMapFocusContext();
    this.exportScope = exportScope != null ? exportScope : ExecutionMapExportScope.FOCUSED;
  }

  public void drawExecutionMap() {
    if (document == null || gc == null) {
      return;
    }
    if (exportScope == ExecutionMapExportScope.FULL) {
      drawFullExecutionMap();
    } else {
      drawFocusedExecutionMap();
    }
  }

  private void drawFocusedExecutionMap() {
    prepareFocusedViewState();
    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);

    gc.setTransform(0.0f, 0.0f, magnification);
    if (gridSize > 1) {
      drawGrid();
    }

    List<Bounds> nodeBounds = collectCardBounds();
    for (ExecutionMapEdge edge : visibleEdges) {
      drawFocusedEdge(edge, nodeBounds);
    }
    for (ExecutionMapNode node : visibleNodes) {
      drawCardNode(node);
    }

    gc.setTransform(0.0f, 0.0f, 1.0f);
    drawNavigationView();
  }

  private void drawFullExecutionMap() {
    indexNodes();
    gc.setTransform(0.0f, 0.0f, 1.0f);
    gc.setBackground(EColor.BACKGROUND);
    gc.fillRectangle(0, 0, area.x, area.y);

    gc.setTransform(0.0f, 0.0f, magnification);
    if (gridSize > 1) {
      drawGrid();
    }
    List<Bounds> nodeBounds = collectLegacyNodeBounds();
    Map<String, Integer> laneIndexBySource = new HashMap<>();
    drawContainerBounds();
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || edge.getEdgeType() == ExecutionMapEdgeType.CONTAINS) {
        continue;
      }
      if (!layoutOptions.usesEdgeForLayout(edge.getEdgeType())) {
        drawLegacyEdge(edge, false, nodeBounds, laneIndexBySource);
      }
    }
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || edge.getEdgeType() == ExecutionMapEdgeType.CONTAINS) {
        continue;
      }
      if (layoutOptions.usesEdgeForLayout(edge.getEdgeType())) {
        drawLegacyEdge(edge, true, nodeBounds, laneIndexBySource);
      }
    }
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      drawLegacyNode(node);
    }

    gc.setTransform(0.0f, 0.0f, 1.0f);
    drawNavigationView();
  }

  private void prepareFocusedViewState() {
    if (areaOwners != null) {
      areaOwners.clear();
    }
    indexNodes();
    cardMetricsById =
        ExecutionMapViewSupport.prepareFocusedView(document, focusContext, gc, magnification);
    visibleNodes = ExecutionMapViewFilter.getVisibleNodes(document, focusContext);
    visibleEdges = ExecutionMapViewFilter.getVisibleEdges(document, focusContext);
  }

  private void indexNodes() {
    nodeById.clear();
    childrenByParent.clear();
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node != null && !Utils.isEmpty(node.getId())) {
        nodeById.put(node.getId(), node);
      }
    }
    for (ExecutionMapNode node : nodeById.values()) {
      if (Utils.isEmpty(node.getParentNodeId()) || !nodeById.containsKey(node.getParentNodeId())) {
        continue;
      }
      childrenByParent
          .computeIfAbsent(node.getParentNodeId(), ignored -> new ArrayList<>())
          .add(node);
    }
  }

  @Override
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    if (document == null) {
      return;
    }
    List<ExecutionMapNode> nodes =
        exportScope == ExecutionMapExportScope.FULL ? document.getNodesOrEmpty() : visibleNodes;
    for (ExecutionMapNode node : nodes) {
      if (node == null || node.getLocation() == null) {
        continue;
      }
      int w;
      int h;
      if (exportScope == ExecutionMapExportScope.FULL) {
        w = Math.max(4, (int) Math.ceil(LEGACY_NODE_WIDTH * scaleX));
        h = Math.max(4, (int) Math.ceil(LEGACY_NODE_HEIGHT * scaleY));
      } else {
        ExecutionMapNodeCardMetrics metrics = cardMetricsById.get(node.getId());
        w =
            Math.max(
                4,
                (int) Math.ceil((metrics != null ? metrics.width() : LEGACY_NODE_WIDTH) * scaleX));
        h =
            Math.max(
                4,
                (int)
                    Math.ceil((metrics != null ? metrics.height() : LEGACY_NODE_HEIGHT) * scaleY));
      }
      int x = (int) (graphX + node.getLocation().x * scaleX);
      int y = (int) (graphY + node.getLocation().y * scaleY);
      ExecutionMapNodeColors.setFillBackground(gc, node);
      gc.setForeground(EColor.DARKGRAY);
      gc.fillRectangle(x, y, w, h);
      gc.drawRectangle(x, y, w, h);
    }
  }

  private Point nodeScreenLocation(ExecutionMapNode node) {
    Point loc = node.getLocation();
    return real2screen(loc.x, loc.y);
  }

  private void drawCardNode(ExecutionMapNode node) {
    if (node == null || node.getLocation() == null) {
      return;
    }
    ExecutionMapNodeCardMetrics metrics = cardMetricsById.get(node.getId());
    if (metrics == null) {
      metrics = ExecutionMapNodeCardMetrics.defaultFor(node);
    }
    Point screenLoc = nodeScreenLocation(node);
    int x = screenLoc.x;
    int y = screenLoc.y;
    String name = node.getName() != null ? node.getName() : node.getId();
    boolean underlineName = name.equals(mouseOverNodeName);
    ExecutionMapNodeCardLayout.drawCard(gc, node, x, y, metrics, magnification, underlineName);

    if (areaOwners != null) {
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_ICON,
              x,
              y,
              metrics.width(),
              metrics.height(),
              offset,
              node,
              name));
      ModelGraphTableNameHitArea.Bounds nameHit =
          ModelGraphTableNameHitArea.bounds(
              x + metrics.nameX(), y + metrics.nameY(), metrics.nameWidth(), metrics.nameHeight());
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_NAME,
              nameHit.x(),
              nameHit.y(),
              nameHit.width(),
              nameHit.height(),
              offset,
              node,
              name));
    }
  }

  private void drawLegacyNode(ExecutionMapNode node) {
    if (node == null || node.getLocation() == null) {
      return;
    }
    Point screenLoc = nodeScreenLocation(node);
    int x = screenLoc.x;
    int y = screenLoc.y;
    gc.setLineWidth(1);
    ExecutionMapNodeColors.setFillBackground(gc, node);
    gc.setForeground(EColor.CRYSTAL);
    gc.fillRoundRectangle(x, y, LEGACY_NODE_WIDTH, LEGACY_NODE_HEIGHT, 8, 8);
    gc.drawRoundRectangle(x, y, LEGACY_NODE_WIDTH, LEGACY_NODE_HEIGHT, 8, 8);
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.BLACK);
    String title = node.getName() != null ? node.getName() : node.getId();
    gc.drawText(title, x + 8, y + 8, true);
    String secondary = !Utils.isEmpty(node.getPluginId()) ? node.getPluginId() : null;
    if (secondary == null && node.getNodeType() != null) {
      secondary = node.getNodeType().name();
    }
    if (!Utils.isEmpty(node.getSnapshotId())) {
      secondary = secondary == null ? "snapshot" : secondary + " • snapshot";
    }
    if (!Utils.isEmpty(secondary)) {
      gc.drawText(secondary, x + 8, y + 24, true);
    }
    if (areaOwners != null) {
      areaOwners.add(
          new AreaOwner(
              AreaType.TRANSFORM_ICON,
              x,
              y,
              LEGACY_NODE_WIDTH,
              LEGACY_NODE_HEIGHT,
              offset,
              node,
              null));
    }
  }

  private void drawContainerBounds() {
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || Utils.isEmpty(node.getId())) {
        continue;
      }
      List<ExecutionMapNode> children = childrenByParent.get(node.getId());
      if (children == null || children.isEmpty()) {
        continue;
      }
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      if (node.getLocation() != null) {
        Point screenLoc = nodeScreenLocation(node);
        minX = screenLoc.x;
        minY = screenLoc.y;
        maxX = screenLoc.x + LEGACY_NODE_WIDTH;
        maxY = screenLoc.y + LEGACY_NODE_HEIGHT;
      }
      for (ExecutionMapNode child : children) {
        if (child == null || child.getLocation() == null) {
          continue;
        }
        Point childLoc = nodeScreenLocation(child);
        minX = Math.min(minX, childLoc.x);
        minY = Math.min(minY, childLoc.y);
        maxX = Math.max(maxX, childLoc.x + LEGACY_NODE_WIDTH);
        maxY = Math.max(maxY, childLoc.y + LEGACY_NODE_HEIGHT);
      }
      if (minX == Integer.MAX_VALUE) {
        continue;
      }
      int pad = CONTAINER_BOUNDS_PADDING;
      int x = minX - pad;
      int y = minY - pad;
      int w = maxX - minX + pad * 2;
      int h = maxY - minY + pad * 2;
      gc.setLineWidth(1);
      gc.setLineStyle(ELineStyle.DOT);
      gc.setForeground(EColor.LIGHTGRAY);
      gc.setBackground(EColor.BACKGROUND);
      gc.drawRoundRectangle(x, y, w, h, 12, 12);
    }
  }

  private List<Bounds> collectCardBounds() {
    List<Bounds> bounds = new ArrayList<>();
    for (ExecutionMapNode node : visibleNodes) {
      if (node != null && node.getLocation() != null) {
        bounds.add(cardBounds(node));
      }
    }
    return bounds;
  }

  private List<Bounds> collectLegacyNodeBounds() {
    List<Bounds> bounds = new ArrayList<>();
    for (ExecutionMapNode node : nodeById.values()) {
      if (node != null && node.getLocation() != null) {
        bounds.add(legacyNodeBounds(node));
      }
    }
    return bounds;
  }

  private Bounds cardBounds(ExecutionMapNode node) {
    Point screenLoc = nodeScreenLocation(node);
    ExecutionMapNodeCardMetrics metrics = cardMetricsById.get(node.getId());
    int width = metrics != null ? metrics.width() : LEGACY_NODE_WIDTH;
    int height = metrics != null ? metrics.height() : LEGACY_NODE_HEIGHT;
    return new Bounds(screenLoc.x, screenLoc.y, width, height);
  }

  private Bounds legacyNodeBounds(ExecutionMapNode node) {
    Point screenLoc = nodeScreenLocation(node);
    return new Bounds(screenLoc.x, screenLoc.y, LEGACY_NODE_WIDTH, LEGACY_NODE_HEIGHT);
  }

  private void drawFocusedEdge(ExecutionMapEdge edge, List<Bounds> nodeBounds) {
    drawRenderedEdge(edge, true, nodeBounds, null);
  }

  private void drawLegacyEdge(
      ExecutionMapEdge edge,
      boolean flowEdge,
      List<Bounds> nodeBounds,
      Map<String, Integer> laneIndexBySource) {
    drawRenderedEdge(edge, false, nodeBounds, laneIndexBySource);
  }

  private void drawRenderedEdge(
      ExecutionMapEdge edge,
      boolean useCardBounds,
      List<Bounds> nodeBounds,
      Map<String, Integer> laneIndexBySource) {
    if (edge == null) {
      return;
    }
    ExecutionMapNode from = nodeById.get(edge.getFromNodeId());
    ExecutionMapNode to = nodeById.get(edge.getToNodeId());
    if (from == null || to == null || from.getLocation() == null || to.getLocation() == null) {
      return;
    }
    Bounds fromBounds = useCardBounds ? cardBounds(from) : legacyNodeBounds(from);
    Bounds toBounds = useCardBounds ? cardBounds(to) : legacyNodeBounds(to);
    boolean flowEdge = layoutOptions.usesEdgeForLayout(edge.getEdgeType());

    gc.setLineWidth(1);
    gc.setForeground(resolveEdgeColor(edge.getEdgeType(), flowEdge));
    drawEdgeGeometry(edge, fromBounds, toBounds, flowEdge, nodeBounds, laneIndexBySource);
    drawCenterEdgeLabel(edge, fromBounds, toBounds);
  }

  private void drawEdgeGeometry(
      ExecutionMapEdge edge,
      Bounds fromBounds,
      Bounds toBounds,
      boolean flowEdge,
      List<Bounds> nodeBounds,
      Map<String, Integer> laneIndexBySource) {
    switch (ExecutionMapLineStyle.configured()) {
      case DIRECT_CENTER -> {
        gc.setLineStyle(ELineStyle.DOT);
        ModelGraphConnectionGeometry.drawConnectionCenterLine(gc, fromBounds, toBounds);
      }
      case SPLINE -> {
        gc.setLineStyle(flowEdge ? ELineStyle.SOLID : ELineStyle.DASH);
        ModelGraphConnectionGeometry.drawConnectionSpline(gc, fromBounds, toBounds);
      }
      case ORTHOGONAL ->
          drawOrthogonalEdgeGeometry(
              edge, fromBounds, toBounds, flowEdge, nodeBounds, laneIndexBySource);
      default -> {
        gc.setLineStyle(ELineStyle.DOT);
        ModelGraphConnectionGeometry.drawConnectionCenterLine(gc, fromBounds, toBounds);
      }
    }
  }

  private void drawOrthogonalEdgeGeometry(
      ExecutionMapEdge edge,
      Bounds fromBounds,
      Bounds toBounds,
      boolean flowEdge,
      List<Bounds> nodeBounds,
      Map<String, Integer> laneIndexBySource) {
    if (flowEdge) {
      gc.setLineStyle(ELineStyle.SOLID);
      ModelGraphConnectionGeometry.drawConnectionSpline(gc, fromBounds, toBounds);
      return;
    }
    gc.setLineStyle(ELineStyle.DASH);
    int laneIndex =
        laneIndexBySource != null ? laneIndexBySource.getOrDefault(edge.getFromNodeId(), 0) : 0;
    if (laneIndexBySource != null) {
      laneIndexBySource.put(edge.getFromNodeId(), laneIndex + 1);
    }
    List<Bounds> obstacles =
        nodeBounds != null
            ? ExecutionMapEdgeRouter.obstaclesForEdge(nodeBounds, fromBounds, toBounds)
            : List.of();
    int[] polyline =
        ExecutionMapEdgeRouter.routeOrthogonal(
            new ExecutionMapEdgeRouter.RouteRequest(
                fromBounds,
                toBounds,
                obstacles,
                laneIndex,
                toBounds.x() >= fromBounds.x() + fromBounds.width() / 2));
    ExecutionMapEdgeRouter.drawRoutedEdge(gc, polyline);
  }

  private void drawCenterEdgeLabel(ExecutionMapEdge edge, Bounds fromBounds, Bounds toBounds) {
    if (Utils.isEmpty(edge.getLabel()) || edge.getEdgeType() == ExecutionMapEdgeType.REFERENCES) {
      return;
    }
    String label = edge.getLabel();
    if (label.length() > 28) {
      label = label.substring(0, 25) + "...";
    }
    int labelX = (fromBounds.centerX() + toBounds.centerX()) / 2;
    int labelY = (fromBounds.centerY() + toBounds.centerY()) / 2;
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.DARKGRAY);
    gc.drawText(label, labelX, labelY, true);
  }

  private static EColor resolveEdgeColor(ExecutionMapEdgeType edgeType, boolean flowEdge) {
    if (flowEdge) {
      return EColor.CRYSTAL;
    }
    if (edgeType == ExecutionMapEdgeType.READS_FROM
        || edgeType == ExecutionMapEdgeType.WRITES_TO
        || edgeType == ExecutionMapEdgeType.PIPELINE_SOURCE) {
      return EColor.LIGHTBLUE;
    }
    return EColor.GRAY;
  }
}
