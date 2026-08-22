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
package org.hopper.edw.datavault.hopgui.file.executionmap;

import java.util.ArrayList;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.SvgGc;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.command.svg.ExecutionMapExportScope;
import org.hopper.edw.datavault.command.svg.SvgRenderOptions;
import org.hopper.edw.datavault.executionmap.ExecutionMapFocusContext;
import org.hopper.edw.datavault.executionmap.ExecutionMapNodeCardMetrics;
import org.hopper.edw.datavault.executionmap.ExecutionMapViewFilter;
import org.hopper.edw.datavault.executionmap.ExecutionMapViewSupport;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;

/** Renders an {@link ExecutionMapDocument} to SVG using {@link ExecutionMapPainter}. */
public final class ExecutionMapSvgPainter {

  private static final int ICON_SIZE = 32;
  private static final int EXTRA_MARGIN = 32;

  private ExecutionMapSvgPainter() {}

  public static String generateExecutionMapSvg(
      ExecutionMapDocument document, SvgRenderOptions options, IVariables variables)
      throws HopException {
    if (document == null) {
      throw new HopException("Cannot generate SVG for a null execution map document.");
    }
    SvgRenderOptions renderOptions = options != null ? options : SvgRenderOptions.defaults();
    ExecutionMapExportScope exportScope =
        renderOptions.getExecutionMapExportScope() != null
            ? renderOptions.getExecutionMapExportScope()
            : ExecutionMapExportScope.FOCUSED;
    ExecutionMapFocusContext focus =
        renderOptions.getExecutionMapFocus() != null
            ? renderOptions.getExecutionMapFocus()
            : new ExecutionMapFocusContext();
    try {
      float magnification = renderOptions.getMagnification();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      Point provisionalSize = new Point(1200, 900);
      SvgGc gc = new SvgGc(graphics2D, provisionalSize, ICON_SIZE, 0, 0);

      Point graphMaximum;
      if (exportScope == ExecutionMapExportScope.FULL) {
        graphMaximum = document.getMaximum();
      } else {
        Map<String, ExecutionMapNodeCardMetrics> cardMetrics =
            ExecutionMapViewSupport.prepareFocusedView(document, focus, gc, magnification);
        java.util.List<ExecutionMapNode> visible =
            ExecutionMapViewFilter.getVisibleNodes(document, focus);
        graphMaximum = ExecutionMapViewSupport.computeViewMaximum(visible, cardMetrics);
      }

      Point svgSize =
          new Point(
              (int) (graphMaximum.x * magnification) + EXTRA_MARGIN,
              (int) (graphMaximum.y * magnification) + EXTRA_MARGIN);
      gc = new SvgGc(graphics2D, svgSize, ICON_SIZE, 0, 0);

      ExecutionMapPainter painter =
          new ExecutionMapPainter(
              document, gc, variables, svgSize.x, svgSize.y, null, focus, exportScope);
      painter.setMagnification(magnification);
      painter.setAreaOwners(new ArrayList<>());
      painter.setZoomFactor(1.0f);
      painter.setOffset(new DPoint(0, 0));
      painter.setIconSize(ICON_SIZE);
      painter.setGridSize(1);
      painter.setShowingNavigationView(false);
      painter.setMaximum(graphMaximum);
      painter.drawExecutionMap();

      return graphics2D.toXml();
    } catch (Exception e) {
      throw new HopException(
          "Unable to generate SVG for execution map "
              + (document.getName() != null ? document.getName() : ""),
          e);
    }
  }
}
