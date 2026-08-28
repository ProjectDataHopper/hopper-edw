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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.gui.Point;
import org.apache.hop.ui.core.PropsUi;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.ConnectionAnchors;
import org.junit.jupiter.api.Test;

class ModelGraphConnectionGeometryTest {

  private static final Bounds BOX_A = new Bounds(0, 0, 100, 50);
  private static final Bounds BOX_WIDE = new Bounds(0, 0, 200, 40);

  @Test
  void anchorWhenTargetBelow() {
    Bounds below = new Bounds(10, 120, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, below);
    assertEquals(new Point(50, 50), anchors.from());
    assertEquals(new Point(50, 120), anchors.to());
  }

  @Test
  void anchorWhenTargetAbove() {
    Bounds above = new Bounds(10, -80, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, above);
    assertEquals(new Point(50, 0), anchors.from());
    assertEquals(new Point(50, -40), anchors.to());
  }

  @Test
  void anchorWhenTargetToTheRight() {
    Bounds right = new Bounds(200, 5, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, right);
    assertEquals(new Point(100, 25), anchors.from());
    assertEquals(new Point(200, 25), anchors.to());
  }

  @Test
  void anchorWhenTargetToTheLeft() {
    Bounds left = new Bounds(-120, 5, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, left);
    assertEquals(new Point(0, 25), anchors.from());
    assertEquals(new Point(-40, 25), anchors.to());
  }

  @Test
  void shallowInclinationUsesSideEvenOnWideCards() {
    // ~30° (tan 30° ≈ 0.577). A 2:1 card used to attach top/bottom past ~26°.
    Bounds target = boundsOffsetFrom(BOX_WIDE, 200, 115);
    Point fromAnchor = ModelGraphConnectionGeometry.anchorToward(BOX_WIDE, target);
    assertEquals(new Point(BOX_WIDE.x() + BOX_WIDE.width(), BOX_WIDE.centerY()), fromAnchor);
  }

  @Test
  void fortyFiveDegreeInclinationUsesSide() {
    Bounds target = boundsOffsetFrom(BOX_A, 120, 120);
    Point fromAnchor = ModelGraphConnectionGeometry.anchorToward(BOX_A, target);
    assertEquals(new Point(BOX_A.x() + BOX_A.width(), BOX_A.centerY()), fromAnchor);
  }

  @Test
  void thresholdInclinationPrefersSide() {
    int dx = 200;
    int dy =
        (int)
            Math.floor(
                dx
                    * Math.tan(
                        Math.toRadians(
                            ModelGraphConnectionGeometry.SIDE_ROUTING_MAX_INCLINATION_DEGREES)));
    Bounds target = boundsOffsetFrom(BOX_A, dx, dy);
    Point fromAnchor = ModelGraphConnectionGeometry.anchorToward(BOX_A, target);
    assertEquals(new Point(BOX_A.x() + BOX_A.width(), BOX_A.centerY()), fromAnchor);
  }

  @Test
  void steepInclinationUsesTopBottom() {
    // ~55° (tan 55° ≈ 1.428), past the 50° side-routing threshold.
    Bounds target = boundsOffsetFrom(BOX_WIDE, 200, 286);
    Point fromAnchor = ModelGraphConnectionGeometry.anchorToward(BOX_WIDE, target);
    assertEquals(new Point(BOX_WIDE.centerX(), BOX_WIDE.y() + BOX_WIDE.height()), fromAnchor);
  }

  @Test
  void borderTowardHitsRightEdgeOnHorizontalLink() {
    Bounds right = new Bounds(200, 5, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.borderAnchorsBetween(BOX_A, right);
    assertEquals(new Point(100, 25), anchors.from());
    assertEquals(new Point(200, 25), anchors.to());
  }

  @Test
  void borderTowardHitsBottomEdgeOnVerticalLink() {
    Bounds below = new Bounds(10, 120, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.borderAnchorsBetween(BOX_A, below);
    assertEquals(new Point(50, 50), anchors.from());
    assertEquals(new Point(50, 120), anchors.to());
  }

  @Test
  void borderTowardHitsSideOnDiagonalLink() {
    Bounds diagonal = new Bounds(200, 50, 80, 40);
    Point from = ModelGraphConnectionGeometry.borderToward(BOX_A, diagonal);
    assertEquals(100, from.x);
    assertTrue(from.y > 25 && from.y < 50);
  }

  @Test
  void overlappingBoxesRemainStable() {
    Bounds overlap = new Bounds(40, 10, 60, 30);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, overlap);
    assertEquals(new Point(50, 50), anchors.from());
    assertEquals(new Point(70, 10), anchors.to());
  }

  @Test
  void splinePolylineHasExpectedVertexCount() {
    Bounds below = new Bounds(10, 120, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, below);
    int segments = 12;
    int[] polyline =
        ModelGraphConnectionGeometry.splinePolyline(
            anchors.from(), anchors.to(), BOX_A, below, segments);
    assertEquals((segments + 1) * 2, polyline.length);
  }

  @Test
  void splinePolylineStartsAndEndsAtAnchors() {
    Bounds right = new Bounds(200, 5, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, right);
    int[] polyline =
        ModelGraphConnectionGeometry.splinePolyline(anchors.from(), anchors.to(), BOX_A, right, 20);
    assertEquals(anchors.from().x, polyline[0]);
    assertEquals(anchors.from().y, polyline[1]);
    int last = polyline.length - 2;
    assertEquals(anchors.to().x, polyline[last]);
    assertEquals(anchors.to().y, polyline[last + 1]);
  }

  @Test
  void controlLengthClampsForShortAndLongConnections() {
    assertEquals(
        3.5, ModelGraphConnectionGeometry.controlLength(new Point(0, 0), new Point(10, 0)), 0.01);
    assertEquals(
        70, ModelGraphConnectionGeometry.controlLength(new Point(0, 0), new Point(200, 0)), 0.01);
    assertEquals(
        150, ModelGraphConnectionGeometry.controlLength(new Point(0, 0), new Point(500, 0)), 0.01);
  }

  @Test
  void effectiveSegmentCountScalesWithScreenLength() {
    double zoom = PropsUi.getNativeZoomFactor();
    assertEquals(
        expectedSegmentCount(120, 20, zoom),
        ModelGraphConnectionGeometry.effectiveSegmentCount(120, 20));
    assertEquals(
        expectedSegmentCount(300, 20, zoom),
        ModelGraphConnectionGeometry.effectiveSegmentCount(300, 20));
    assertEquals(
        expectedSegmentCount(300, 30, zoom),
        ModelGraphConnectionGeometry.effectiveSegmentCount(300, 30));
    assertEquals(
        expectedSegmentCount(5000, 20, zoom),
        ModelGraphConnectionGeometry.effectiveSegmentCount(5000, 20));
    assertTrue(
        ModelGraphConnectionGeometry.effectiveSegmentCount(300, 20)
            <= ModelGraphConnectionGeometry.effectiveSegmentCount(5000, 20));
  }

  /** Target box whose center is offset from {@code from}'s center by {@code dx},{@code dy}. */
  private static Bounds boundsOffsetFrom(Bounds from, int dx, int dy) {
    int cx = from.centerX() + dx;
    int cy = from.centerY() + dy;
    return new Bounds(cx - 40, cy - 20, 80, 40);
  }

  private static int expectedSegmentCount(
      double screenLength, int configuredSegments, double zoomFactor) {
    int configured = Math.max(1, configuredSegments);
    int byLength = (int) Math.ceil(screenLength / (15.0 * zoomFactor));
    return Math.max(configured, Math.min(200, byLength));
  }

  @Test
  void boxCentersSupportDirectConnections() {
    Bounds below = new Bounds(10, 120, 80, 40);
    assertEquals(50, BOX_A.centerX());
    assertEquals(25, BOX_A.centerY());
    assertEquals(50, below.centerX());
    assertEquals(140, below.centerY());
  }

  @Test
  void splineLeavesBoxPerpendicularToBottomEdge() {
    Bounds below = new Bounds(10, 120, 80, 40);
    ConnectionAnchors anchors = ModelGraphConnectionGeometry.anchorsBetween(BOX_A, below);
    int[] polyline =
        ModelGraphConnectionGeometry.splinePolyline(anchors.from(), anchors.to(), BOX_A, below, 20);
    assertTrue(
        polyline[3] > polyline[1], "first segment should continue downward from bottom edge");
  }
}
