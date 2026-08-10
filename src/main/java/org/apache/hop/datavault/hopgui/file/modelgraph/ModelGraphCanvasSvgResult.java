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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import lombok.Getter;
import org.apache.hop.core.gui.CanvasSvgRenderResult;
import org.apache.hop.datavault.hopgui.file.vault.BasePainter;

/**
 * SVG canvas render result plus navigation minimap geometry captured from {@link BasePainter}.
 * Hop's {@link CanvasSvgRenderResult} only carries view/graph ports.
 */
@Getter
public final class ModelGraphCanvasSvgResult {

  private final CanvasSvgRenderResult canvasResult;
  private final double navigationScale;
  private final double navigationGraphOriginX;
  private final double navigationGraphOriginY;

  public ModelGraphCanvasSvgResult(
      CanvasSvgRenderResult canvasResult,
      double navigationScale,
      double navigationGraphOriginX,
      double navigationGraphOriginY) {
    this.canvasResult = canvasResult;
    this.navigationScale = navigationScale;
    this.navigationGraphOriginX = navigationGraphOriginX;
    this.navigationGraphOriginY = navigationGraphOriginY;
  }

  public static ModelGraphCanvasSvgResult fromPainter(
      CanvasSvgRenderResult canvasResult, BasePainter painter) {
    if (painter == null) {
      return new ModelGraphCanvasSvgResult(canvasResult, 0, 0, 0);
    }
    return new ModelGraphCanvasSvgResult(
        canvasResult,
        painter.getNavigationScale(),
        painter.getNavigationGraphOriginX(),
        painter.getNavigationGraphOriginY());
  }
}
