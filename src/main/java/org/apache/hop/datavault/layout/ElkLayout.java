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
package org.apache.hop.datavault.layout;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.rectpacking.options.RectPackingOptions;
import org.eclipse.elk.alg.rectpacking.p1widthapproximation.WidthApproximationStrategy;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.ContentAlignment;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.HierarchyHandling;
import org.eclipse.elk.graph.ElkNode;

/**
 * Configuration for ELK graph layout (layered Sugiyama or rectangle packing). This class is
 * independent of any specific consumer and can be reused by other Hop plugins or applications that
 * use the Eclipse Layout Kernel.
 */
@Getter
@Setter
public class ElkLayout {

  public static final boolean DEFAULT_ENABLED = true;
  public static final ElkLayoutAlgorithm DEFAULT_ALGORITHM = ElkLayoutAlgorithm.LAYERED;
  public static final int DEFAULT_SPACING_WITHIN_LAYER = 48;
  public static final int DEFAULT_SPACING_BETWEEN_LAYERS = 16;
  public static final int DEFAULT_SPACING_EDGE_NODE = 16;
  public static final int DEFAULT_ORIGIN_X = 48;
  public static final int DEFAULT_ORIGIN_Y = 16;
  public static final int DEFAULT_GRID_SIZE = 16;
  public static final int DEFAULT_MIN_NODE_WIDTH = 120;
  public static final int DEFAULT_NODE_HEIGHT = 48;
  public static final double DEFAULT_CHAR_WIDTH = 7.5;
  public static final double DEFAULT_ICON_PADDING = 32;
  public static final int DEFAULT_TARGET_WIDTH = 1000;

  private boolean enabled = DEFAULT_ENABLED;
  private ElkLayoutAlgorithm algorithm = DEFAULT_ALGORITHM;
  private int targetWidth = DEFAULT_TARGET_WIDTH;
  private ElkLayoutDirection direction = ElkLayoutDirection.RIGHT;
  private int spacingWithinLayer = DEFAULT_SPACING_WITHIN_LAYER;
  private int spacingBetweenLayers = DEFAULT_SPACING_BETWEEN_LAYERS;
  private int spacingEdgeNode = DEFAULT_SPACING_EDGE_NODE;
  private ElkCrossingMinimization crossingMinimization = ElkCrossingMinimization.LAYER_SWEEP;
  private ElkNodePlacement nodePlacement = ElkNodePlacement.BRANDES_KOEPF;
  private ElkLayeringStrategy layeringStrategy = ElkLayeringStrategy.NETWORK_SIMPLEX;
  private ElkCycleBreaking cycleBreaking = ElkCycleBreaking.GREEDY;
  private int originX = DEFAULT_ORIGIN_X;
  private int originY = DEFAULT_ORIGIN_Y;
  private int gridSize = DEFAULT_GRID_SIZE;
  private int minNodeWidth = DEFAULT_MIN_NODE_WIDTH;
  private int nodeHeight = DEFAULT_NODE_HEIGHT;
  private double charWidth = DEFAULT_CHAR_WIDTH;
  private double iconPadding = DEFAULT_ICON_PADDING;
  private boolean includeChildrenHierarchy;

  public ElkLayout() {}

  public ElkLayout(ElkLayout layout) {
    if (layout == null) {
      return;
    }
    enabled = layout.enabled;
    algorithm = layout.getAlgorithm();
    setTargetWidth(layout.targetWidth);
    direction = layout.getDirection();
    setSpacingWithinLayer(layout.spacingWithinLayer);
    setSpacingBetweenLayers(layout.spacingBetweenLayers);
    setSpacingEdgeNode(layout.spacingEdgeNode);
    crossingMinimization = layout.getCrossingMinimization();
    nodePlacement = layout.getNodePlacement();
    layeringStrategy = layout.getLayeringStrategy();
    cycleBreaking = layout.getCycleBreaking();
    setOriginX(layout.originX);
    setOriginY(layout.originY);
    setGridSize(layout.gridSize);
    setMinNodeWidth(layout.minNodeWidth);
    setNodeHeight(layout.nodeHeight);
    setCharWidth(layout.charWidth);
    setIconPadding(layout.iconPadding);
    includeChildrenHierarchy = layout.includeChildrenHierarchy;
  }

  public static final int EXECUTION_MAP_SPACING_WITHIN_LAYER = 24;
  public static final int EXECUTION_MAP_SPACING_BETWEEN_LAYERS = 48;
  public static final int EXECUTION_MAP_SPACING_EDGE_NODE = 20;

  public static ElkLayout createDefault() {
    return new ElkLayout();
  }

  /** Tighter layered defaults tuned for nested execution map graphs. */
  public static ElkLayout createForExecutionMap() {
    ElkLayout layout = new ElkLayout();
    layout.setSpacingWithinLayer(EXECUTION_MAP_SPACING_WITHIN_LAYER);
    layout.setSpacingBetweenLayers(EXECUTION_MAP_SPACING_BETWEEN_LAYERS);
    layout.setSpacingEdgeNode(EXECUTION_MAP_SPACING_EDGE_NODE);
    layout.setDirection(ElkLayoutDirection.RIGHT);
    layout.setIncludeChildrenHierarchy(true);
    return layout;
  }

  public int getSpacingWithinLayer() {
    return spacingWithinLayer >= 0 ? spacingWithinLayer : DEFAULT_SPACING_WITHIN_LAYER;
  }

  public void setSpacingWithinLayer(int spacingWithinLayer) {
    this.spacingWithinLayer =
        spacingWithinLayer >= 0 ? spacingWithinLayer : DEFAULT_SPACING_WITHIN_LAYER;
  }

  public int getSpacingBetweenLayers() {
    return spacingBetweenLayers >= 0 ? spacingBetweenLayers : DEFAULT_SPACING_BETWEEN_LAYERS;
  }

  public void setSpacingBetweenLayers(int spacingBetweenLayers) {
    this.spacingBetweenLayers =
        spacingBetweenLayers >= 0 ? spacingBetweenLayers : DEFAULT_SPACING_BETWEEN_LAYERS;
  }

  public int getSpacingEdgeNode() {
    return spacingEdgeNode >= 0 ? spacingEdgeNode : DEFAULT_SPACING_EDGE_NODE;
  }

  public void setSpacingEdgeNode(int spacingEdgeNode) {
    this.spacingEdgeNode = spacingEdgeNode >= 0 ? spacingEdgeNode : DEFAULT_SPACING_EDGE_NODE;
  }

  public int getOriginX() {
    return originX >= 0 ? originX : DEFAULT_ORIGIN_X;
  }

  public void setOriginX(int originX) {
    this.originX = originX >= 0 ? originX : DEFAULT_ORIGIN_X;
  }

  public int getOriginY() {
    return originY >= 0 ? originY : DEFAULT_ORIGIN_Y;
  }

  public void setOriginY(int originY) {
    this.originY = originY >= 0 ? originY : DEFAULT_ORIGIN_Y;
  }

  public int getGridSize() {
    return gridSize > 0 ? gridSize : DEFAULT_GRID_SIZE;
  }

  public void setGridSize(int gridSize) {
    this.gridSize = gridSize > 0 ? gridSize : DEFAULT_GRID_SIZE;
  }

  public int getMinNodeWidth() {
    return minNodeWidth > 0 ? minNodeWidth : DEFAULT_MIN_NODE_WIDTH;
  }

  public void setMinNodeWidth(int minNodeWidth) {
    this.minNodeWidth = minNodeWidth > 0 ? minNodeWidth : DEFAULT_MIN_NODE_WIDTH;
  }

  public int getNodeHeight() {
    return nodeHeight > 0 ? nodeHeight : DEFAULT_NODE_HEIGHT;
  }

  public void setNodeHeight(int nodeHeight) {
    this.nodeHeight = nodeHeight > 0 ? nodeHeight : DEFAULT_NODE_HEIGHT;
  }

  public double getCharWidth() {
    return charWidth > 0 ? charWidth : DEFAULT_CHAR_WIDTH;
  }

  public void setCharWidth(double charWidth) {
    this.charWidth = charWidth > 0 ? charWidth : DEFAULT_CHAR_WIDTH;
  }

  public double getIconPadding() {
    return iconPadding >= 0 ? iconPadding : DEFAULT_ICON_PADDING;
  }

  public void setIconPadding(double iconPadding) {
    this.iconPadding = iconPadding >= 0 ? iconPadding : DEFAULT_ICON_PADDING;
  }

  public ElkLayoutAlgorithm getAlgorithm() {
    return algorithm != null ? algorithm : DEFAULT_ALGORITHM;
  }

  public int getTargetWidth() {
    return targetWidth > 0 ? targetWidth : DEFAULT_TARGET_WIDTH;
  }

  public void setTargetWidth(int targetWidth) {
    this.targetWidth = targetWidth > 0 ? targetWidth : DEFAULT_TARGET_WIDTH;
  }

  public ElkLayoutDirection getDirection() {
    return direction != null ? direction : ElkLayoutDirection.RIGHT;
  }

  public ElkCrossingMinimization getCrossingMinimization() {
    return crossingMinimization != null
        ? crossingMinimization
        : ElkCrossingMinimization.LAYER_SWEEP;
  }

  public ElkNodePlacement getNodePlacement() {
    return nodePlacement != null ? nodePlacement : ElkNodePlacement.BRANDES_KOEPF;
  }

  public ElkLayeringStrategy getLayeringStrategy() {
    return layeringStrategy != null ? layeringStrategy : ElkLayeringStrategy.NETWORK_SIMPLEX;
  }

  public ElkCycleBreaking getCycleBreaking() {
    return cycleBreaking != null ? cycleBreaking : ElkCycleBreaking.GREEDY;
  }

  /** Applies layout options to the root node of an ELK graph. */
  public void applyTo(ElkNode root) {
    if (getAlgorithm() == ElkLayoutAlgorithm.RECT_PACKING) {
      applyRectPackingTo(root);
    } else {
      applyLayeredTo(root);
    }
  }

  private void applyLayeredTo(ElkNode root) {
    int paddingX = getOriginX();
    int paddingY = getOriginY();

    root.setProperty(CoreOptions.ALGORITHM, LayeredOptions.ALGORITHM_ID);
    root.setProperty(CoreOptions.DIRECTION, getDirection().toElkDirection());
    root.setProperty(CoreOptions.PADDING, new ElkPadding(paddingX, paddingY, paddingX, paddingY));
    root.setProperty(LayeredOptions.SPACING_NODE_NODE, (double) getSpacingWithinLayer());
    root.setProperty(
        LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, (double) getSpacingBetweenLayers());
    root.setProperty(LayeredOptions.SPACING_EDGE_NODE, (double) getSpacingEdgeNode());
    root.setProperty(
        LayeredOptions.CROSSING_MINIMIZATION_STRATEGY, getCrossingMinimization().toElkStrategy());
    root.setProperty(LayeredOptions.NODE_PLACEMENT_STRATEGY, getNodePlacement().toElkStrategy());
    root.setProperty(LayeredOptions.LAYERING_STRATEGY, getLayeringStrategy().toElkStrategy());
    root.setProperty(LayeredOptions.CYCLE_BREAKING_STRATEGY, getCycleBreaking().toElkStrategy());
    if (isIncludeChildrenHierarchy()) {
      root.setProperty(LayeredOptions.HIERARCHY_HANDLING, HierarchyHandling.INCLUDE_CHILDREN);
    }
  }

  private void applyRectPackingTo(ElkNode root) {
    int paddingX = getOriginX();
    int paddingY = getOriginY();

    root.setProperty(CoreOptions.ALGORITHM, RectPackingOptions.ALGORITHM_ID);
    root.setProperty(CoreOptions.PADDING, new ElkPadding(paddingX, paddingY, paddingX, paddingY));
    root.setProperty(CoreOptions.EXPAND_NODES, false);
    root.setProperty(RectPackingOptions.NODE_SIZE_FIXED_GRAPH_SIZE, true);
    root.setProperty(RectPackingOptions.SPACING_NODE_NODE, (double) getSpacingWithinLayer());
    root.setProperty(
        RectPackingOptions.WIDTH_APPROXIMATION_STRATEGY, WidthApproximationStrategy.TARGET_WIDTH);
    root.setProperty(
        RectPackingOptions.WIDTH_APPROXIMATION_TARGET_WIDTH, (double) getTargetWidth());
    root.setProperty(RectPackingOptions.CONTENT_ALIGNMENT, ContentAlignment.topLeft());
  }

  /** Estimates node width from a label, respecting {@link #getMinNodeWidth()}. */
  public double estimateNodeWidth(String label) {
    double minimum = getMinNodeWidth();
    if (Utils.isEmpty(label)) {
      return minimum;
    }
    return Math.max(minimum, getIconPadding() + label.length() * getCharWidth());
  }

  /** Snaps a coordinate to {@link #getGridSize()}. */
  public int snap(int coordinate) {
    int grid = getGridSize();
    if (grid <= 0) {
      return coordinate;
    }
    return Math.round((float) coordinate / grid) * grid;
  }
}
