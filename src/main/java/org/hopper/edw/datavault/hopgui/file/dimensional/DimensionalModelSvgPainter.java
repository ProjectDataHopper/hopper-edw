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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.SvgGc;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.command.svg.ModelBoundsSupport;
import org.hopper.edw.datavault.command.svg.SvgRenderOptions;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Renders a {@link DimensionalModel} to SVG using {@link DimensionalModelPainter}. */
public final class DimensionalModelSvgPainter {

  private static final int ICON_SIZE = 32;
  private static final int EXTRA_MARGIN = 0;

  private DimensionalModelSvgPainter() {}

  public static String generateDimensionalModelSvg(
      DimensionalModel model,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException("Cannot generate SVG for a null dimensional model.");
    }
    SvgRenderOptions renderOptions = options != null ? options : SvgRenderOptions.defaults();
    try {
      Point maximum = ModelBoundsSupport.getMaximum(model, renderOptions.isIncludeNotes());
      maximum.multiply(renderOptions.getMagnification());

      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      Point svgSize = new Point(maximum.x + EXTRA_MARGIN, maximum.y + EXTRA_MARGIN);

      SvgGc gc = new SvgGc(graphics2D, svgSize, ICON_SIZE, 0, 0);
      DimensionalModelPainter painter =
          new DimensionalModelPainter(model, gc, variables, svgSize.x, svgSize.y);
      painter.setMagnification(renderOptions.getMagnification());
      painter.setAreaOwners(new ArrayList<>());
      painter.setZoomFactor(1.0f);
      painter.setOffset(new DPoint(0, 0));
      painter.setIconSize(ICON_SIZE);
      painter.setGridSize(1);
      painter.setShowingNavigationView(false);
      painter.setShowEmptyModelHint(false);
      painter.setDrawNotes(renderOptions.isIncludeNotes());
      painter.setMaximum(ModelBoundsSupport.getMaximum(model, renderOptions.isIncludeNotes()));
      painter.drawDimensionalModel(metadataProvider);

      return graphics2D.toXml();
    } catch (Exception e) {
      throw new HopException(
          "Unable to generate SVG for dimensional model "
              + (model.getName() != null ? model.getName() : ""),
          e);
    }
  }
}
