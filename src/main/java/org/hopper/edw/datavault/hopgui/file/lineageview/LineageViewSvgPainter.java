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
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;
import org.hopper.edw.datavault.layout.ElkLayoutBox;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;

/** Renders a session lineage graph to SVG. */
public final class LineageViewSvgPainter {

  private static final int ICON_SIZE = 32;
  private static final int EXTRA_MARGIN = 32;

  private LineageViewSvgPainter() {}

  public static String generateSvg(
      LineageGraph graph, Map<String, ElkLayoutBox> boxes, IVariables variables)
      throws HopException {
    return generateSvg(graph, boxes, variables, null);
  }

  public static String generateSvg(
      LineageGraph graph,
      Map<String, ElkLayoutBox> boxes,
      IVariables variables,
      org.hopper.edw.datavault.lineageview.LineageViewOpsOverlay opsOverlay)
      throws HopException {
    if (graph == null) {
      throw new HopException("Cannot generate SVG for an empty lineage graph.");
    }
    try {
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      Point graphMaximum = maximumOf(boxes);
      Point svgSize = new Point(graphMaximum.x + EXTRA_MARGIN, graphMaximum.y + EXTRA_MARGIN);
      var gc = ModelGraphWebCanvasData.createSvgGc(graphics2D, svgSize, ICON_SIZE);
      LineageViewPainter painter =
          new LineageViewPainter(
              graph, boxes, null, gc, variables, svgSize.x, svgSize.y, opsOverlay);
      painter.setMagnification(1.0f);
      painter.setAreaOwners(new ArrayList<>());
      painter.setZoomFactor(1.0f);
      painter.setOffset(new DPoint(0, 0));
      painter.setIconSize(ICON_SIZE);
      painter.setGridSize(1);
      painter.setShowingNavigationView(false);
      painter.setMaximum(graphMaximum);
      painter.draw();
      return graphics2D.toXml();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for lineage view", e);
    }
  }

  static Point maximumOf(Map<String, ElkLayoutBox> boxes) {
    int maxX = 400;
    int maxY = 300;
    if (boxes != null) {
      for (ElkLayoutBox box : boxes.values()) {
        if (box == null) {
          continue;
        }
        maxX = Math.max(maxX, box.x() + box.width() + 48);
        maxY = Math.max(maxY, box.y() + box.height() + 48);
      }
    }
    return new Point(maxX, maxY);
  }
}
