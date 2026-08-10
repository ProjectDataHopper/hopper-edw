/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.hopgui.file.sourcemodel;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.command.svg.SvgRenderOptions;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Headless SVG export for a {@link SourceModel}. */
public final class SourceModelSvgPainter {

  private static final int ICON_SIZE = 32;

  private SourceModelSvgPainter() {}

  public static String generateSourceModelSvg(
      SourceModel model,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException("Cannot generate SVG for a null source model.");
    }
    SvgRenderOptions renderOptions = options != null ? options : SvgRenderOptions.defaults();
    Point maximum = model.getMaximum();
    float magnification = renderOptions.getMagnification();
    Point svgSize =
        new Point(
            Math.max(1, (int) (maximum.x * magnification) + 32),
            Math.max(1, (int) (maximum.y * magnification) + 32));

    SourceModelCanvasSvgRenderer.Context ctx = new SourceModelCanvasSvgRenderer.Context();
    ctx.variables = variables;
    ctx.model = model;
    ctx.canvasSize = svgSize;
    ctx.offset = new DPoint(0, 0);
    ctx.iconSize = ICON_SIZE;
    ctx.gridSize = 1;
    ctx.magnification = magnification;
    ctx.screenMagnification = magnification;
    ctx.zoomFactor = 1.0f;
    ctx.maximum = maximum;
    ctx.showingNavigationView = false;
    ctx.showEmptyModelHint = false;
    ctx.drawNotes = renderOptions.isIncludeNotes();
    ctx.metadataProvider = metadataProvider;

    ModelGraphCanvasSvgResult result = SourceModelCanvasSvgRenderer.render(ctx);
    return result.getCanvasResult().getSvg();
  }
}
