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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphCanvasSvgResult;
import org.apache.hop.datavault.layout.ElkLayoutBox;
import org.apache.hop.datavault.lineageview.LineageViewElkSupport;
import org.apache.hop.datavault.lineageview.backend.LineageEdge;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LineageViewCanvasSvgRendererTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void rendersInteractiveSvgWithAreaOwners() throws Exception {
    LineageGraph graph = twoNodeGraph();
    Map<String, ElkLayoutBox> boxes = LineageViewElkSupport.layout(graph);

    LineageViewCanvasSvgRenderer.Context ctx = new LineageViewCanvasSvgRenderer.Context();
    ctx.variables = new Variables();
    ctx.graph = graph;
    ctx.boxes = boxes;
    ctx.canvasSize = new Point(800, 600);
    ctx.offset = new DPoint(0, 0);
    ctx.iconSize = 32;
    ctx.gridSize = 16;
    ctx.magnification = 1.0f;
    ctx.screenMagnification = 1.0f;
    ctx.zoomFactor = 1.0f;
    ctx.maximum = LineageViewSvgPainter.maximumOf(boxes);
    ctx.showingNavigationView = true;

    ModelGraphCanvasSvgResult result = LineageViewCanvasSvgRenderer.render(ctx);
    assertNotNull(result);
    assertNotNull(result.getCanvasResult());
    assertFalse(result.getCanvasResult().getSvg().isBlank());
    assertTrue(result.getCanvasResult().getSvg().contains("<svg"));
    assertFalse(
        result.getCanvasResult().getAreaOwners().isEmpty(),
        "interactive render should populate click regions");
    assertTrue(
        result.getCanvasResult().getAreaOwners().stream()
            .anyMatch(owner -> owner.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME));
    assertTrue(
        result.getCanvasResult().getAreaOwners().stream()
            .anyMatch(owner -> owner.getOwner() instanceof String));
  }

  @Test
  void paintsErrorBannerWhenGraphMissing() throws Exception {
    LineageViewCanvasSvgRenderer.Context ctx = new LineageViewCanvasSvgRenderer.Context();
    ctx.variables = new Variables();
    ctx.canvasSize = new Point(400, 300);
    ctx.offset = new DPoint(0, 0);
    ctx.iconSize = 32;
    ctx.gridSize = 1;
    ctx.magnification = 1.0f;
    ctx.screenMagnification = 1.0f;
    ctx.zoomFactor = 1.0f;
    ctx.maximum = new Point(400, 300);
    ctx.bannerText = "backend down";
    ctx.bannerError = true;

    ModelGraphCanvasSvgResult result = LineageViewCanvasSvgRenderer.render(ctx);
    assertTrue(result.getCanvasResult().getSvg().contains("backend down"));
  }

  private static LineageGraph twoNodeGraph() {
    return LineageGraph.builder()
        .seedNodeId("dataset:ns:b")
        .nodes(
            List.of(
                LineageNode.builder()
                    .id("dataset:ns:a")
                    .kind(LineageNodeKind.DATASET)
                    .name("a")
                    .build(),
                LineageNode.builder()
                    .id("dataset:ns:b")
                    .kind(LineageNodeKind.DATASET)
                    .name("b")
                    .build()))
        .edges(
            List.of(
                LineageEdge.builder().fromNodeId("dataset:ns:a").toNodeId("dataset:ns:b").build()))
        .build();
  }
}
