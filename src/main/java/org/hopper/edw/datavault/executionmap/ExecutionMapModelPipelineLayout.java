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
package org.hopper.edw.datavault.executionmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.layout.ElkLayout;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdge;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Lays out model focus children as pipeline rows: source | pipeline | target. */
public final class ExecutionMapModelPipelineLayout {

  private ExecutionMapModelPipelineLayout() {}

  public static void layout(
      ExecutionMapDocument document,
      ExecutionMapNode parent,
      List<ExecutionMapNode> children,
      Map<String, ExecutionMapNodeCardMetrics> cardMetricsById) {
    if (parent == null || children == null || children.isEmpty()) {
      if (parent != null) {
        ElkLayout defaults = ElkLayout.createForExecutionMap();
        parent.setLocation(defaults.getOriginX(), defaults.getOriginY());
      }
      return;
    }

    Map<String, ExecutionMapNode> childById = new HashMap<>();
    for (ExecutionMapNode child : children) {
      if (child != null && !Utils.isEmpty(child.getId())) {
        childById.put(child.getId(), child);
      }
    }

    Map<String, PipelineRow> rowsByPipelineId = new LinkedHashMap<>();
    List<ExecutionMapNode> pipelines = new ArrayList<>();
    for (ExecutionMapNode child : children) {
      if (child != null && isPipelineNode(child.getNodeType())) {
        pipelines.add(child);
        rowsByPipelineId.put(child.getId(), new PipelineRow(child));
      }
    }
    pipelines.sort(pipelineComparator());

    Set<String> linkedDatasetIds = new HashSet<>();
    if (document != null) {
      linkDatasetsFromEdges(document, childById, rowsByPipelineId, linkedDatasetIds);
    }

    List<ExecutionMapNode> orphans = new ArrayList<>();
    for (ExecutionMapNode child : children) {
      if (child == null || Utils.isEmpty(child.getId())) {
        continue;
      }
      if (isPipelineNode(child.getNodeType())) {
        continue;
      }
      if (!linkedDatasetIds.contains(child.getId())) {
        orphans.add(child);
      }
    }
    orphans.sort(datasetComparator());

    ElkLayout defaults = ElkLayout.createForExecutionMap();
    int originX = defaults.getOriginX();
    int originY = defaults.getOriginY();
    int gutter = ExecutionMapMetrics.HUB_GUTTER + ExecutionMapMetrics.BUS_LANE_WIDTH;

    int maxSourceWidth = 0;
    int maxPipelineWidth = 0;
    int maxTargetWidth = 0;
    for (PipelineRow row : rowsByPipelineId.values()) {
      maxSourceWidth = Math.max(maxSourceWidth, maxColumnWidth(row.sources(), cardMetricsById));
      maxPipelineWidth = Math.max(maxPipelineWidth, cardWidth(row.pipeline(), cardMetricsById));
      maxTargetWidth = Math.max(maxTargetWidth, maxColumnWidth(row.targets(), cardMetricsById));
    }

    int sourceColumnX = originX + cardWidth(parent, cardMetricsById) + gutter;
    int pipelineColumnX = sourceColumnX + maxSourceWidth + gutter;
    int targetColumnX = pipelineColumnX + maxPipelineWidth + gutter;

    int rowY = originY;
    int totalContentHeight = 0;
    for (int i = 0; i < pipelines.size(); i++) {
      ExecutionMapNode pipeline = pipelines.get(i);
      PipelineRow row = rowsByPipelineId.get(pipeline.getId());
      if (row == null) {
        continue;
      }
      int rowHeight =
          layoutPipelineRow(
              row, cardMetricsById, sourceColumnX, pipelineColumnX, targetColumnX, rowY);
      totalContentHeight += rowHeight;
      if (i < pipelines.size() - 1) {
        totalContentHeight += ExecutionMapMetrics.PIPELINE_ROW_SPACING;
        rowY += rowHeight + ExecutionMapMetrics.PIPELINE_ROW_SPACING;
      } else {
        rowY += rowHeight;
      }
    }

    if (!orphans.isEmpty()) {
      if (!pipelines.isEmpty()) {
        totalContentHeight += ExecutionMapMetrics.PIPELINE_ROW_SPACING;
        rowY += ExecutionMapMetrics.PIPELINE_ROW_SPACING;
      }
      int orphanHeight = layoutOrphanGrid(orphans, cardMetricsById, sourceColumnX, rowY);
      totalContentHeight += orphanHeight;
    }

    int parentHeight = cardHeight(parent, cardMetricsById);
    int parentY = originY;
    if (totalContentHeight > parentHeight) {
      parentY = originY + (totalContentHeight - parentHeight) / 2;
    }
    parent.setLocation(originX, parentY);
  }

  private static void linkDatasetsFromEdges(
      ExecutionMapDocument document,
      Map<String, ExecutionMapNode> childById,
      Map<String, PipelineRow> rowsByPipelineId,
      Set<String> linkedDatasetIds) {
    for (ExecutionMapEdge edge : document.getEdgesOrEmpty()) {
      if (edge == null || edge.getEdgeType() == null) {
        continue;
      }
      if (edge.getEdgeType() == ExecutionMapEdgeType.READS_FROM) {
        ExecutionMapNode dataset = childById.get(edge.getFromNodeId());
        ExecutionMapNode pipeline = childById.get(edge.getToNodeId());
        PipelineRow row = pipeline != null ? rowsByPipelineId.get(pipeline.getId()) : null;
        if (dataset == null || row == null) {
          continue;
        }
        if (dataset.getNodeType() == ExecutionMapNodeType.SOURCE_DATASET) {
          row.sources().add(dataset);
        } else if (dataset.getNodeType() == ExecutionMapNodeType.TARGET_DATASET) {
          row.targets().add(dataset);
        }
        linkedDatasetIds.add(dataset.getId());
      } else if (edge.getEdgeType() == ExecutionMapEdgeType.WRITES_TO) {
        ExecutionMapNode pipeline = childById.get(edge.getFromNodeId());
        ExecutionMapNode dataset = childById.get(edge.getToNodeId());
        PipelineRow row = pipeline != null ? rowsByPipelineId.get(pipeline.getId()) : null;
        if (dataset == null || row == null) {
          continue;
        }
        row.targets().add(dataset);
        linkedDatasetIds.add(dataset.getId());
      }
    }
    for (PipelineRow row : rowsByPipelineId.values()) {
      row.sources().sort(datasetComparator());
      row.targets().sort(datasetComparator());
    }
  }

  private static int layoutPipelineRow(
      PipelineRow row,
      Map<String, ExecutionMapNodeCardMetrics> cardMetricsById,
      int sourceColumnX,
      int pipelineColumnX,
      int targetColumnX,
      int rowY) {
    int sourceHeight = layoutColumn(row.sources(), cardMetricsById, sourceColumnX, rowY);
    int targetHeight = layoutColumn(row.targets(), cardMetricsById, targetColumnX, rowY);
    int sideHeight = Math.max(sourceHeight, targetHeight);

    ExecutionMapNode pipeline = row.pipeline();
    int pipelineHeight = cardHeight(pipeline, cardMetricsById);
    int pipelineY = rowY;
    if (sideHeight > pipelineHeight) {
      pipelineY = rowY + (sideHeight - pipelineHeight) / 2;
    }
    pipeline.setLocation(pipelineColumnX, pipelineY);

    return Math.max(sideHeight, pipelineHeight);
  }

  private static int layoutColumn(
      List<ExecutionMapNode> nodes,
      Map<String, ExecutionMapNodeCardMetrics> cardMetricsById,
      int columnX,
      int startY) {
    if (nodes == null || nodes.isEmpty()) {
      return 0;
    }
    int y = startY;
    int totalHeight = 0;
    for (int i = 0; i < nodes.size(); i++) {
      ExecutionMapNode node = nodes.get(i);
      int height = cardHeight(node, cardMetricsById);
      node.setLocation(columnX, y);
      y += height;
      totalHeight += height;
      if (i < nodes.size() - 1) {
        y += ExecutionMapMetrics.CHILD_VERTICAL_SPACING;
        totalHeight += ExecutionMapMetrics.CHILD_VERTICAL_SPACING;
      }
    }
    return totalHeight;
  }

  private static int layoutOrphanGrid(
      List<ExecutionMapNode> orphans,
      Map<String, ExecutionMapNodeCardMetrics> cardMetricsById,
      int startX,
      int startY) {
    int columns = ExecutionMapMetrics.ORPHAN_GRID_COLUMNS;
    int columnWidth = 0;
    for (ExecutionMapNode orphan : orphans) {
      columnWidth = Math.max(columnWidth, cardWidth(orphan, cardMetricsById));
    }
    int gutter = ExecutionMapMetrics.HUB_GUTTER;
    int rowCount = (orphans.size() + columns - 1) / columns;

    List<Integer> rowHeights = new ArrayList<>();
    for (int row = 0; row < rowCount; row++) {
      int maxHeight = 0;
      for (int col = 0; col < columns; col++) {
        int index = row * columns + col;
        if (index >= orphans.size()) {
          break;
        }
        maxHeight = Math.max(maxHeight, cardHeight(orphans.get(index), cardMetricsById));
      }
      rowHeights.add(maxHeight);
    }

    for (int i = 0; i < orphans.size(); i++) {
      int col = i % columns;
      int row = i / columns;
      int x = startX + col * (columnWidth + gutter);
      int y = startY;
      for (int r = 0; r < row; r++) {
        y += rowHeights.get(r) + ExecutionMapMetrics.CHILD_VERTICAL_SPACING;
      }
      orphans.get(i).setLocation(x, y);
    }

    int totalHeight = rowHeights.stream().mapToInt(Integer::intValue).sum();
    if (rowCount > 1) {
      totalHeight += (rowCount - 1) * ExecutionMapMetrics.CHILD_VERTICAL_SPACING;
    }
    return totalHeight;
  }

  private static int maxColumnWidth(
      List<ExecutionMapNode> nodes, Map<String, ExecutionMapNodeCardMetrics> cardMetricsById) {
    int max = 0;
    if (nodes == null) {
      return max;
    }
    for (ExecutionMapNode node : nodes) {
      max = Math.max(max, cardWidth(node, cardMetricsById));
    }
    return max;
  }

  private static int cardWidth(
      ExecutionMapNode node, Map<String, ExecutionMapNodeCardMetrics> cardMetricsById) {
    if (node == null) {
      return ExecutionMapMetrics.MIN_NODE_WIDTH;
    }
    ExecutionMapNodeCardMetrics card =
        cardMetricsById != null ? cardMetricsById.get(node.getId()) : null;
    return card != null ? card.width() : ExecutionMapMetrics.MIN_NODE_WIDTH;
  }

  private static int cardHeight(
      ExecutionMapNode node, Map<String, ExecutionMapNodeCardMetrics> cardMetricsById) {
    if (node == null) {
      return ExecutionMapMetrics.MIN_NODE_HEIGHT;
    }
    ExecutionMapNodeCardMetrics card =
        cardMetricsById != null ? cardMetricsById.get(node.getId()) : null;
    return card != null ? card.height() : ExecutionMapMetrics.MIN_NODE_HEIGHT;
  }

  private static boolean isPipelineNode(ExecutionMapNodeType nodeType) {
    if (nodeType == null) {
      return false;
    }
    return switch (nodeType) {
      case GENERATED_PIPELINE, ORCHESTRATOR_PIPELINE, PIPELINE, ROOT_PIPELINE -> true;
      default -> false;
    };
  }

  private static Comparator<ExecutionMapNode> pipelineComparator() {
    return Comparator.comparing(
            (ExecutionMapNode node) -> node.getNodeType(),
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(
            node -> node.getName() != null ? node.getName() : "", String.CASE_INSENSITIVE_ORDER);
  }

  private static Comparator<ExecutionMapNode> datasetComparator() {
    return Comparator.comparing(
            (ExecutionMapNode node) -> node.getNodeType(),
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(
            node -> node.getName() != null ? node.getName() : "", String.CASE_INSENSITIVE_ORDER);
  }

  private record PipelineRow(
      ExecutionMapNode pipeline, List<ExecutionMapNode> sources, List<ExecutionMapNode> targets) {
    PipelineRow(ExecutionMapNode pipeline) {
      this(pipeline, new ArrayList<>(), new ArrayList<>());
    }
  }
}
