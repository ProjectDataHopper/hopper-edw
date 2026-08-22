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

import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;

/** Prepares a drill-down execution map view for painting or SVG export. */
public final class ExecutionMapViewSupport {

  private ExecutionMapViewSupport() {}

  public static Map<String, ExecutionMapNodeCardMetrics> prepareFocusedView(
      ExecutionMapDocument document, ExecutionMapFocusContext focus, IGc gc, float magnification) {
    ExecutionMapFocusContext resolvedFocus = focus != null ? focus : new ExecutionMapFocusContext();
    List<org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode> visible =
        ExecutionMapViewFilter.getVisibleNodes(document, resolvedFocus);
    Map<String, ExecutionMapNodeCardMetrics> metrics =
        ExecutionMapNodeCardLayout.measureCards(gc, visible, magnification);
    ExecutionMapTwoLevelLayout.layout(document, resolvedFocus, metrics);
    return metrics;
  }

  public static Point computeViewMaximum(
      List<ExecutionMapNode> visibleNodes, Map<String, ExecutionMapNodeCardMetrics> cardMetrics) {
    int maxX = 0;
    int maxY = 0;
    if (visibleNodes != null) {
      for (ExecutionMapNode node : visibleNodes) {
        if (node == null || node.getLocation() == null) {
          continue;
        }
        ExecutionMapNodeCardMetrics metrics =
            cardMetrics != null ? cardMetrics.get(node.getId()) : null;
        int width = metrics != null ? metrics.width() : ExecutionMapMetrics.MIN_NODE_WIDTH;
        int height = metrics != null ? metrics.height() : ExecutionMapMetrics.MIN_NODE_HEIGHT;
        maxX = Math.max(maxX, node.getLocation().x + width);
        maxY = Math.max(maxY, node.getLocation().y + height);
      }
    }
    return new Point(Math.max(maxX + 32, 800), Math.max(maxY + 32, 600));
  }
}
