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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphTableCardLayout;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;

/** Measures and draws execution map node cards. */
public final class ExecutionMapNodeCardLayout {

  private ExecutionMapNodeCardLayout() {}

  public static Map<String, ExecutionMapNodeCardMetrics> measureCards(
      IGc gc, List<ExecutionMapNode> nodes, float magnification) {
    Map<String, ExecutionMapNodeCardMetrics> metrics = new HashMap<>();
    if (nodes == null || gc == null) {
      return metrics;
    }
    for (ExecutionMapNode node : nodes) {
      if (node == null || Utils.isEmpty(node.getId())) {
        continue;
      }
      String name = node.getName() != null ? node.getName() : node.getId();
      String typeLabel = ExecutionMapNodeIconSupport.getTypeLabel(node);
      ModelGraphTableCardLayout.BoxSize box =
          ModelGraphTableCardLayout.computeBoxSize(gc, name, null, typeLabel, null);
      int width = Math.max(ExecutionMapMetrics.MIN_NODE_WIDTH, box.width());
      int height = Math.max(ExecutionMapMetrics.MIN_NODE_HEIGHT, box.height());
      gc.setFont(EFont.GRAPH);
      Point nameExtent = gc.textExtent(name);
      int nameX = ModelGraphTableCardLayout.nameX(0);
      int nameY = ModelGraphTableCardLayout.nameY(0);
      metrics.put(
          node.getId(),
          new ExecutionMapNodeCardMetrics(width, height, nameX, nameY, nameExtent.x, nameExtent.y));
    }
    return metrics;
  }

  public static void drawCard(
      IGc gc,
      ExecutionMapNode node,
      int x,
      int y,
      ExecutionMapNodeCardMetrics metrics,
      float magnification,
      boolean underlineName) {
    if (node == null || gc == null || metrics == null) {
      return;
    }
    gc.setLineWidth(1);
    ExecutionMapNodeColors.setFillBackground(gc, node);
    gc.setForeground(IGc.EColor.CRYSTAL);
    gc.fillRoundRectangle(x, y, metrics.width(), metrics.height(), 8, 8);
    gc.drawRoundRectangle(x, y, metrics.width(), metrics.height(), 8, 8);

    ModelGraphTableCardLayout.drawSvgIcon(
        gc,
        ExecutionMapNodeIconSupport.getIconClassLoader(node.getNodeType()),
        ExecutionMapNodeIconSupport.getIconPath(node.getNodeType()),
        x,
        y,
        magnification);
    String name = node.getName() != null ? node.getName() : node.getId();
    ModelGraphTableCardLayout.drawName(gc, name, x, y, underlineName);
    ModelGraphTableCardLayout.drawTypeBelowIcon(
        gc, ExecutionMapNodeIconSupport.getTypeLabel(node), x, y);
  }
}
