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
package org.apache.hop.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.CanvasSvgRenderResult;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.gui.markdown.NoteLinkHit;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Renders a dimensional model to SVG for Hop Web. */
public final class DimensionalModelCanvasSvgRenderer {

  private DimensionalModelCanvasSvgRenderer() {}

  public static final class Context {
    public IVariables variables;
    public DimensionalModel model;
    public Point canvasSize;
    public DPoint offset;
    public Rectangle selectionRegion;
    public int iconSize;
    public int gridSize;
    public float magnification;
    public float screenMagnification;
    public double zoomFactor;
    public Point maximum;
    public String mouseOverTableName;
    public NoteLinkHit mouseOverNoteLink;
    public String noteImageBaseFilename;
    public boolean showingNavigationView;
    public boolean drawNotes = true;
    public IHopMetadataProvider metadataProvider;
    public IDmTable startRelationshipTable;
    public Point relationshipDragEndLocation;
    public IDmTable candidateRelationshipTarget;
  }

  public static ModelGraphCanvasSvgResult render(Context ctx) throws HopException {
    if (ctx == null || ctx.model == null || ctx.canvasSize == null) {
      throw new HopException("Cannot render dimensional model SVG: missing context or model.");
    }
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      var gc = ModelGraphWebCanvasData.createSvgGc(graphics2D, ctx.canvasSize, ctx.iconSize);

      DimensionalModelPainter painter =
          new DimensionalModelPainter(
              ctx.model, gc, ctx.variables, ctx.canvasSize.x, ctx.canvasSize.y);
      painter.setGridSize(ctx.gridSize);
      painter.setZoomFactor((float) ctx.zoomFactor);
      painter.setMagnification(ctx.magnification);
      painter.setScreenMagnification(ctx.screenMagnification);
      painter.setOffset(ctx.offset != null ? ctx.offset : new DPoint(0, 0));
      painter.setIconSize(ctx.iconSize);
      painter.setSelectionRegion(ctx.selectionRegion);
      painter.setAreaOwners(areaOwners);
      painter.setMouseOverTableName(ctx.mouseOverTableName);
      painter.setMouseOverNoteLink(ctx.mouseOverNoteLink);
      painter.setNoteImageBaseFilename(ctx.noteImageBaseFilename);
      painter.setShowingNavigationView(ctx.showingNavigationView);
      painter.setDrawNotes(ctx.drawNotes);
      painter.setMetadataProvider(ctx.metadataProvider);
      painter.setMaximum(ctx.maximum != null ? ctx.maximum : ctx.model.getMaximum());
      painter.setRelationshipDragInfo(
          ctx.startRelationshipTable,
          ctx.relationshipDragEndLocation,
          ctx.candidateRelationshipTarget);

      painter.drawDimensionalModel(ctx.metadataProvider);

      CanvasSvgRenderResult canvasResult =
          new CanvasSvgRenderResult(
              graphics2D.toXml(), areaOwners, painter.getViewPort(), painter.getGraphPort());
      return ModelGraphCanvasSvgResult.fromPainter(canvasResult, painter);
    } catch (Exception e) {
      throw new HopException(
          "Unable to generate SVG for dimensional model "
              + (ctx.model.getName() != null ? ctx.model.getName() : ""),
          e);
    }
  }
}
