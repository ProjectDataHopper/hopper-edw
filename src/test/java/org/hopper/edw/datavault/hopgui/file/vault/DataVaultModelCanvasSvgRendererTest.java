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
package org.hopper.edw.datavault.hopgui.file.vault;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataVaultModelCanvasSvgRendererTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void rendersInteractiveSvgWithAreaOwners() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setName("web-canvas-test");
    DvHub hub = new DvHub();
    hub.setName("H_Customer");
    hub.setLocation(new Point(100, 100));
    model.getTables().add(hub);

    DataVaultModelCanvasSvgRenderer.Context ctx = new DataVaultModelCanvasSvgRenderer.Context();
    ctx.variables = new Variables();
    ctx.model = model;
    ctx.canvasSize = new Point(800, 600);
    ctx.offset = new DPoint(0, 0);
    ctx.iconSize = 32;
    ctx.gridSize = 16;
    ctx.magnification = 1.0f;
    ctx.screenMagnification = 1.0f;
    ctx.zoomFactor = 1.0f;
    ctx.maximum = model.getMaximum();
    ctx.showingNavigationView = true;
    ctx.showEmptyModelHint = false;

    ModelGraphCanvasSvgResult result = DataVaultModelCanvasSvgRenderer.render(ctx);
    assertNotNull(result);
    assertNotNull(result.getCanvasResult());
    assertFalse(result.getCanvasResult().getSvg().isBlank());
    assertTrue(result.getCanvasResult().getSvg().contains("<svg"));
    assertFalse(
        result.getCanvasResult().getAreaOwners().isEmpty(),
        "interactive render should populate click regions");
  }
}
