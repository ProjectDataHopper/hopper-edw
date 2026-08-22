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
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.CanvasSvgRenderResult;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.command.svg.ExecutionMapExportScope;
import org.hopper.edw.datavault.executionmap.ExecutionMapFocusContext;
import org.hopper.edw.datavault.executionmap.ExecutionMapNodeCardMetrics;
import org.hopper.edw.datavault.executionmap.ExecutionMapViewFilter;
import org.hopper.edw.datavault.executionmap.ExecutionMapViewSupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;

/** Renders an execution map to SVG for Hop Web. */
public final class ExecutionMapCanvasSvgRenderer {

  private ExecutionMapCanvasSvgRenderer() {}

  public static final class Context {
    public IVariables variables;
    public ExecutionMapDocument document;
    public ExecutionMapFocusContext focusContext;
    public ExecutionMapExportScope exportScope = ExecutionMapExportScope.FOCUSED;
    public Point canvasSize;
    public DPoint offset;
    public int iconSize;
    public int gridSize;
    public float magnification;
    public float screenMagnification;
    public double zoomFactor;
    public Point maximum;
    public String mouseOverNodeName;
    public boolean showingNavigationView;
  }

  public static ModelGraphCanvasSvgResult render(Context ctx) throws HopException {
    if (ctx == null || ctx.document == null || ctx.canvasSize == null) {
      throw new HopException("Cannot render execution map SVG: missing context or document.");
    }
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      var gc = ModelGraphWebCanvasData.createSvgGc(graphics2D, ctx.canvasSize, ctx.iconSize);

      ExecutionMapFocusContext focus =
          ctx.focusContext != null ? ctx.focusContext : new ExecutionMapFocusContext();
      ExecutionMapExportScope scope =
          ctx.exportScope != null ? ctx.exportScope : ExecutionMapExportScope.FOCUSED;

      ExecutionMapPainter painter =
          new ExecutionMapPainter(
              ctx.document,
              gc,
              ctx.variables,
              ctx.canvasSize.x,
              ctx.canvasSize.y,
              null,
              focus,
              scope);

      Point maximum = ctx.maximum;
      if (maximum == null) {
        Map<String, ExecutionMapNodeCardMetrics> cardMetrics =
            ExecutionMapViewSupport.prepareFocusedView(ctx.document, focus, gc, ctx.magnification);
        List<ExecutionMapNode> visible =
            ExecutionMapViewFilter.getVisibleNodes(ctx.document, focus);
        maximum = ExecutionMapViewSupport.computeViewMaximum(visible, cardMetrics);
      }

      painter.setGridSize(ctx.gridSize);
      painter.setZoomFactor((float) ctx.zoomFactor);
      painter.setMagnification(ctx.magnification);
      painter.setScreenMagnification(ctx.screenMagnification);
      painter.setOffset(ctx.offset != null ? ctx.offset : new DPoint(0, 0));
      painter.setIconSize(ctx.iconSize);
      painter.setMaximum(maximum);
      painter.setAreaOwners(areaOwners);
      painter.setMouseOverNodeName(ctx.mouseOverNodeName);
      painter.setShowingNavigationView(ctx.showingNavigationView);
      painter.drawExecutionMap();

      CanvasSvgRenderResult canvasResult =
          new CanvasSvgRenderResult(
              graphics2D.toXml(), areaOwners, painter.getViewPort(), painter.getGraphPort());
      return ModelGraphCanvasSvgResult.fromPainter(canvasResult, painter);
    } catch (Exception e) {
      throw new HopException(
          "Unable to generate SVG for execution map "
              + (ctx.document.getName() != null ? ctx.document.getName() : ""),
          e);
    }
  }
}
