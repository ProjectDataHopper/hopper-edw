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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;
import org.apache.hop.datavault.layout.ElkLayoutBox;
import org.apache.hop.datavault.lineageview.LineageViewElkSupport;
import org.apache.hop.datavault.lineageview.backend.LineageEdge;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LineageViewSvgPainterTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void generatesSvgForTwoNodeGraph() throws Exception {
    LineageGraph graph =
        LineageGraph.builder()
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
                    LineageEdge.builder()
                        .fromNodeId("dataset:ns:a")
                        .toNodeId("dataset:ns:b")
                        .build()))
            .build();
    Map<String, ElkLayoutBox> boxes = LineageViewElkSupport.layout(graph);
    String svg = LineageViewSvgPainter.generateSvg(graph, boxes, new Variables());
    assertFalse(svg.isBlank());
    assertTrue(svg.contains("<svg"));
    assertTrue(svg.contains("a") || svg.contains("b"));
  }

  @Test
  void registersNameHitAboveCardBody() throws Exception {
    LineageGraph graph =
        LineageGraph.builder()
            .nodes(
                List.of(
                    LineageNode.builder()
                        .id("dataset:ns:a")
                        .kind(LineageNodeKind.DATASET)
                        .name("a")
                        .build()))
            .build();
    Map<String, ElkLayoutBox> boxes = LineageViewElkSupport.layout(graph);
    Point size = new Point(400, 300);
    List<AreaOwner> owners = new ArrayList<>();
    var gc = ModelGraphWebCanvasData.createSvgGc(HopSvgGraphics2D.newDocument(), size, 32);
    LineageViewPainter painter =
        new LineageViewPainter(graph, boxes, null, gc, new Variables(), size.x, size.y);
    painter.setMagnification(1.0f);
    painter.setOffset(new DPoint(0, 0));
    painter.setGridSize(1);
    painter.setShowingNavigationView(false);
    painter.setAreaOwners(owners);
    painter.draw();
    long names =
        owners.stream().filter(a -> a.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME).count();
    long icons =
        owners.stream().filter(a -> a.getAreaType() == AreaOwner.AreaType.TRANSFORM_ICON).count();
    assertEquals(1, names);
    assertEquals(1, icons);
    assertTrue(
        owners.get(owners.size() - 1).getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME
            || owners.stream().anyMatch(a -> a.getAreaType() == AreaOwner.AreaType.TRANSFORM_NAME));
    assertTrue(
        owners.stream()
            .filter(a -> a.getAreaType() == AreaOwner.AreaType.TRANSFORM_ICON)
            .allMatch(a -> a.getOwner() instanceof String && a.getParent() instanceof Object));
  }

  @Test
  void drawsBannerWhenGraphIsEmpty() throws Exception {
    Point size = new Point(400, 300);
    var gc = ModelGraphWebCanvasData.createSvgGc(HopSvgGraphics2D.newDocument(), size, 32);
    LineageViewPainter painter =
        new LineageViewPainter(null, Map.of(), null, gc, new Variables(), size.x, size.y);
    painter.setBanner("backend down", true);
    painter.setMagnification(1.0f);
    painter.setOffset(new DPoint(0, 0));
    painter.setGridSize(1);
    painter.setShowingNavigationView(false);
    painter.setAreaOwners(new ArrayList<>());
    painter.draw();
  }
}
