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
package org.hopper.edw.datavault.hopgui.file.modelgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.Point;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphEdgeLayout.Edge;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphEdgeLayout.EdgeGeometry;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphEdgeLayout.Side;
import org.junit.jupiter.api.Test;

class ModelGraphEdgeLayoutTest {

  @Test
  void spreadsAnchorsOnSharedSide() {
    Bounds hub = new Bounds(400, 100, 160, 90);
    Bounds satA = new Bounds(50, 50, 140, 80);
    Bounds satB = new Bounds(50, 200, 140, 80);

    Map<String, EdgeGeometry> layout =
        ModelGraphEdgeLayout.layout(
            List.of(
                new Edge("a", "satA", satA, "hub", hub), new Edge("b", "satB", satB, "hub", hub)));

    assertEquals(2, layout.size());
    Point hubA = layout.get("a").toAnchor();
    Point hubB = layout.get("b").toAnchor();
    assertEquals(hubA.x, hubB.x);
    assertNotEquals(hubA.y, hubB.y);
    assertEquals(Side.LEFT, layout.get("a").toSide());
    assertEquals(Side.LEFT, layout.get("b").toSide());
  }

  @Test
  void sortsSharedSideByOtherNodeToAvoidCrossings() {
    Bounds hub = new Bounds(400, 100, 160, 90);
    Bounds lowerSat = new Bounds(50, 200, 140, 80);
    Bounds upperSat = new Bounds(50, 40, 140, 80);

    // Lower satellite is listed first; without sorting its hub anchor would sit above the
    // upper satellite's and the two straight lines would cross.
    Map<String, EdgeGeometry> layout =
        ModelGraphEdgeLayout.layout(
            List.of(
                new Edge("lower", "lowerSat", lowerSat, "hub", hub),
                new Edge("upper", "upperSat", upperSat, "hub", hub)));

    Point hubForLower = layout.get("lower").toAnchor();
    Point hubForUpper = layout.get("upper").toAnchor();
    assertTrue(hubForUpper.y < hubForLower.y);
  }

  @Test
  void shallowDiagonalPrefersLeftRightSides() {
    Bounds from = new Bounds(0, 0, 200, 40);
    // ~30° down-right (Δx=220, Δy=127): wide cards used to attach on top/bottom here.
    Bounds to = new Bounds(220, 127, 200, 40);
    Map<String, EdgeGeometry> layout =
        ModelGraphEdgeLayout.layout(List.of(new Edge("one", "from", from, "to", to)));

    EdgeGeometry geometry = layout.get("one");
    assertNotNull(geometry);
    assertEquals(Side.RIGHT, geometry.fromSide());
    assertEquals(Side.LEFT, geometry.toSide());
  }

  @Test
  void singleEdgeCentersOnFacingSide() {
    Bounds from = new Bounds(0, 0, 100, 80);
    Bounds to = new Bounds(200, 0, 100, 80);
    Map<String, EdgeGeometry> layout =
        ModelGraphEdgeLayout.layout(List.of(new Edge("one", "from", from, "to", to)));

    EdgeGeometry geometry = layout.get("one");
    assertNotNull(geometry);
    assertEquals(Side.RIGHT, geometry.fromSide());
    assertEquals(Side.LEFT, geometry.toSide());
    assertEquals(new Point(100, 40), geometry.fromAnchor());
    assertEquals(new Point(200, 40), geometry.toAnchor());
  }

  @Test
  void skipsSelfEdgesSoTheyDoNotStealSideSlots() {
    Bounds warehouse = new Bounds(320, 48, 160, 80);
    Bounds fact = new Bounds(320, 192, 180, 90);
    Map<String, EdgeGeometry> layout =
        ModelGraphEdgeLayout.layout(
            List.of(
                new Edge("alias-self", "d_warehouse", warehouse, "d_warehouse", warehouse),
                new Edge("fact-dim", "d_warehouse", warehouse, "fact", fact)));

    assertEquals(1, layout.size());
    EdgeGeometry geometry = layout.get("fact-dim");
    assertNotNull(geometry);
    assertEquals(Side.BOTTOM, geometry.fromSide());
    assertEquals(warehouse.centerX(), geometry.fromAnchor().x);
    assertEquals(warehouse.y() + warehouse.height(), geometry.fromAnchor().y);
  }

  @Test
  void skipsIncompleteEdges() {
    Bounds box = new Bounds(0, 0, 40, 40);
    Map<String, EdgeGeometry> layout =
        ModelGraphEdgeLayout.layout(
            List.of(
                new Edge(null, "a", box, "b", box),
                new Edge("ok", "a", box, "b", new Bounds(80, 0, 40, 40))));
    assertEquals(1, layout.size());
    assertNotNull(layout.get("ok"));
  }

  @Test
  void anchorOnSideUsesFractions() {
    Bounds bounds = new Bounds(0, 0, 100, 100);
    Point a = ModelGraphEdgeLayout.anchorOnSide(bounds, Side.RIGHT, 0, 2);
    Point b = ModelGraphEdgeLayout.anchorOnSide(bounds, Side.RIGHT, 1, 2);
    assertEquals(100, a.x);
    assertEquals(100, b.x);
    assertTrue(a.y < b.y);
  }
}
