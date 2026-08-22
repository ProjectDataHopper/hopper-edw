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
package org.apache.hop.datavault.hopgui.file.lineageview;

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
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;
import org.apache.hop.datavault.layout.ElkLayoutBox;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;

/** Renders a lineage view to SVG for Hop Web. */
public final class LineageViewCanvasSvgRenderer {

  private LineageViewCanvasSvgRenderer() {}

  public static final class Context {
    public IVariables variables;
    public LineageGraph graph;
    public Map<String, ElkLayoutBox> boxes;
    public String selectedNodeId;
    public String mouseOverTableName;
    public Point canvasSize;
    public DPoint offset;
    public int iconSize;
    public int gridSize;
    public float magnification;
    public float screenMagnification;
    public double zoomFactor;
    public Point maximum;
    public boolean showingNavigationView;
    public org.apache.hop.datavault.lineageview.LineageViewOpsOverlay opsOverlay;
    public String bannerText;
    public boolean bannerError;
  }

  public static ModelGraphCanvasSvgResult render(Context ctx) throws HopException {
    if (ctx == null || ctx.canvasSize == null) {
      throw new HopException("Cannot render lineage view SVG: missing context.");
    }
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      var gc = ModelGraphWebCanvasData.createSvgGc(graphics2D, ctx.canvasSize, ctx.iconSize);
      LineageViewPainter painter =
          new LineageViewPainter(
              ctx.graph,
              ctx.boxes != null ? ctx.boxes : Map.of(),
              ctx.selectedNodeId,
              gc,
              ctx.variables,
              ctx.canvasSize.x,
              ctx.canvasSize.y,
              ctx.opsOverlay);
      Point maximum =
          ctx.maximum != null ? ctx.maximum : LineageViewSvgPainter.maximumOf(ctx.boxes);
      painter.setGridSize(ctx.gridSize);
      painter.setZoomFactor((float) ctx.zoomFactor);
      painter.setMagnification(ctx.magnification);
      painter.setScreenMagnification(ctx.screenMagnification);
      painter.setOffset(ctx.offset != null ? ctx.offset : new DPoint(0, 0));
      painter.setIconSize(ctx.iconSize);
      painter.setMaximum(maximum);
      painter.setAreaOwners(areaOwners);
      painter.setShowingNavigationView(ctx.showingNavigationView);
      painter.setMouseOverTableName(ctx.mouseOverTableName);
      painter.setBanner(ctx.bannerText, ctx.bannerError);
      painter.draw();
      CanvasSvgRenderResult canvasResult =
          new CanvasSvgRenderResult(
              graphics2D.toXml(), areaOwners, painter.getViewPort(), painter.getGraphPort());
      return ModelGraphCanvasSvgResult.fromPainter(canvasResult, painter);
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for lineage view", e);
    }
  }
}
