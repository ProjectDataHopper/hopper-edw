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

import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.ui.core.PropsUi;

/** Edge-anchor helpers for table-to-table relationship lines in model graph painters. */
public final class ModelGraphConnectionGeometry {
  public static final int DEFAULT_NUMBER_OF_SPLINE_SEGMENTS = 20;

  private ModelGraphConnectionGeometry() {}

  /** Returns the configured number of segments used to approximate each connection spline. */
  public static int splineSegments() {
    return DEFAULT_NUMBER_OF_SPLINE_SEGMENTS;
  }

  /** Axis-aligned table card bounds in the painter's draw coordinate space. */
  public record Bounds(int x, int y, int width, int height) {

    public Bounds {
      width = Math.max(1, width);
      height = Math.max(1, height);
    }

    public int centerX() {
      return x + width / 2;
    }

    public int centerY() {
      return y + height / 2;
    }
  }

  public record ConnectionAnchors(Point from, Point to) {}

  private record Normal(int x, int y) {}

  /**
   * Returns the midpoint on the side of {@code from} that faces {@code to}, using dominant-axis
   * routing (horizontal when |dx|*h dominates, otherwise vertical; ties prefer vertical).
   */
  public static Point anchorToward(Bounds from, Bounds to) {
    int dx = to.centerX() - from.centerX();
    int dy = to.centerY() - from.centerY();

    boolean horizontal =
        !rectanglesOverlap(from, to) && Math.abs(dx) * from.height() > Math.abs(dy) * from.width();
    if (horizontal) {
      if (dx > 0) {
        return rightMid(from);
      }
      return leftMid(from);
    }
    if (dy > 0) {
      return bottomMid(from);
    }
    if (dy < 0) {
      return topMid(from);
    }
    if (dx > 0) {
      return bottomMid(from);
    }
    if (dx < 0) {
      return topMid(from);
    }
    return bottomMid(from);
  }

  private static boolean rectanglesOverlap(Bounds a, Bounds b) {
    return a.x < b.x + b.width()
        && a.x + a.width() > b.x
        && a.y < b.y + b.height()
        && a.y + a.height() > b.y;
  }

  /** Returns edge anchors on each box facing the other. */
  public static ConnectionAnchors anchorsBetween(Bounds a, Bounds b) {
    return new ConnectionAnchors(anchorToward(a, b), anchorToward(b, a));
  }

  /**
   * Where the center-to-center line leaves {@code from} toward {@code to}. Falls back to {@link
   * #anchorToward(Bounds, Bounds)} when the target center is inside {@code from}.
   */
  public static Point borderToward(Bounds from, Bounds to) {
    if (from == null) {
      return new Point(0, 0);
    }
    if (to == null) {
      return new Point(from.centerX(), from.centerY());
    }
    Point hit = lineBoxExit(from, from.centerX(), from.centerY(), to.centerX(), to.centerY());
    return hit != null ? hit : anchorToward(from, to);
  }

  /** Border attachment on each box of the center-to-center line. */
  public static ConnectionAnchors borderAnchorsBetween(Bounds a, Bounds b) {
    return new ConnectionAnchors(borderToward(a, b), borderToward(b, a));
  }

  /**
   * First intersection of the segment ({@code x0},{@code y0})→({@code x1},{@code y1}) with {@code
   * box}'s border, starting from a point inside the box. {@code null} when the segment does not
   * leave the box.
   */
  static Point lineBoxExit(Bounds box, int x0, int y0, int x1, int y1) {
    if (box == null) {
      return null;
    }
    double dx = x1 - x0;
    double dy = y1 - y0;
    if (dx == 0 && dy == 0) {
      return null;
    }
    double left = box.x();
    double right = box.x() + box.width();
    double top = box.y();
    double bottom = box.y() + box.height();
    double bestT = Double.POSITIVE_INFINITY;
    double hitX = 0;
    double hitY = 0;
    if (dx != 0) {
      double[] xs = {left, right};
      for (double edgeX : xs) {
        double t = (edgeX - x0) / dx;
        if (t <= 1e-6 || t > 1.0 + 1e-6 || t >= bestT) {
          continue;
        }
        double y = y0 + t * dy;
        if (y + 1e-6 >= top && y - 1e-6 <= bottom) {
          bestT = t;
          hitX = edgeX;
          hitY = y;
        }
      }
    }
    if (dy != 0) {
      double[] ys = {top, bottom};
      for (double edgeY : ys) {
        double t = (edgeY - y0) / dy;
        if (t <= 1e-6 || t > 1.0 + 1e-6 || t >= bestT) {
          continue;
        }
        double x = x0 + t * dx;
        if (x + 1e-6 >= left && x - 1e-6 <= right) {
          bestT = t;
          hitX = x;
          hitY = edgeY;
        }
      }
    }
    if (bestT == Double.POSITIVE_INFINITY) {
      return null;
    }
    return new Point(roundCoordinate(hitX), roundCoordinate(hitY));
  }

  /** Degenerate bounds centered on a point (for drag lines toward the cursor). */
  public static Bounds pointBounds(int x, int y) {
    return new Bounds(x, y, 1, 1);
  }

  /** Outward-facing unit normal for an anchor lying on a box edge. */
  static Normal anchorNormal(Bounds bounds, Point anchor) {
    if (anchor.y == bounds.y) {
      return new Normal(0, -1);
    }
    if (anchor.y == bounds.y + bounds.height()) {
      return new Normal(0, 1);
    }
    if (anchor.x == bounds.x) {
      return new Normal(-1, 0);
    }
    if (anchor.x == bounds.x + bounds.width()) {
      return new Normal(1, 0);
    }
    int dx = anchor.x - bounds.centerX();
    int dy = anchor.y - bounds.centerY();
    if (Math.abs(dx) >= Math.abs(dy)) {
      return dx >= 0 ? new Normal(1, 0) : new Normal(-1, 0);
    }
    return dy >= 0 ? new Normal(0, 1) : new Normal(0, -1);
  }

  /** Control-point distance for a cubic spline between two anchors. */
  static double controlLength(Point from, Point to) {
    double dist = Math.hypot(to.x - from.x, to.y - from.y);
    if (dist <= 0) {
      return 0;
    }
    double preferred = dist * 0.35;
    double minimum = Math.min(24, dist / 3.0);
    double maximum = Math.min(150, dist * 0.5);
    return Math.min(Math.max(preferred, minimum), maximum);
  }

  /**
   * Picks a segment count that keeps each piece short in screen space when zoomed in, while
   * honoring the configured minimum.
   */
  static int effectiveSegmentCount(double screenLength, int configuredSegments) {
    int configured = Math.max(1, configuredSegments);
    int byLength = (int) Math.ceil(screenLength / (15.0 * PropsUi.getNativeZoomFactor()));
    return Math.max(configured, Math.min(200, byLength));
  }

  /** Samples a cubic Bezier between two anchors using the configured segment count. */
  public static int[] splinePolyline(Point from, Point to, Bounds fromBounds, Bounds toBounds) {
    return splinePolyline(from, to, fromBounds, toBounds, splineSegments());
  }

  /**
   * Samples a cubic Bezier between two anchors into {@code segments} straight segments ({@code
   * segments + 1} vertices).
   */
  public static int[] splinePolyline(
      Point from, Point to, Bounds fromBounds, Bounds toBounds, int segments) {
    int segmentCount = Math.max(1, segments);
    CubicBezier curve = cubicBezier(from, to, fromBounds, toBounds);

    int pointCount = segmentCount + 1;
    int[] polyline = new int[pointCount * 2];
    for (int i = 0; i < pointCount; i++) {
      double t = (double) i / segmentCount;
      double[] point = curve.pointAt(t);
      polyline[2 * i] = roundCoordinate(point[0]);
      polyline[2 * i + 1] = roundCoordinate(point[1]);
    }
    return polyline;
  }

  /** Draws a straight line between the centers of two table bounds. */
  public static void drawConnectionCenterLine(IGc gc, Bounds fromBounds, Bounds toBounds) {
    if (gc == null || fromBounds == null || toBounds == null) {
      return;
    }
    gc.drawLine(fromBounds.centerX(), fromBounds.centerY(), toBounds.centerX(), toBounds.centerY());
  }

  /** Draws a spline between the edge anchors on two table bounds. */
  public static void drawConnectionSpline(IGc gc, Bounds fromBounds, Bounds toBounds) {
    ConnectionAnchors anchors = anchorsBetween(fromBounds, toBounds);
    drawConnectionSpline(gc, anchors.from(), anchors.to(), fromBounds, toBounds);
  }

  /** Draws a spline between explicit endpoints, using bounds to derive edge normals. */
  public static void drawConnectionSpline(
      IGc gc, Point from, Point to, Bounds fromBounds, Bounds toBounds) {
    double dist = Math.hypot(to.x - from.x, to.y - from.y);
    float magnification = Math.max(1f, gc.getMagnification());
    int segments = effectiveSegmentCount(dist * magnification, splineSegments());
    drawSplineCurve(gc, from, to, fromBounds, toBounds, segments);
  }

  private static void drawSplineCurve(
      IGc gc, Point from, Point to, Bounds fromBounds, Bounds toBounds, int segments) {
    int segmentCount = Math.max(1, segments);
    CubicBezier curve = cubicBezier(from, to, fromBounds, toBounds);

    int previousX = roundCoordinate(from.x);
    int previousY = roundCoordinate(from.y);
    for (int i = 1; i <= segmentCount; i++) {
      double t = (double) i / segmentCount;
      double[] point = curve.pointAt(t);
      int x = roundCoordinate(point[0]);
      int y = roundCoordinate(point[1]);
      if (x != previousX || y != previousY) {
        gc.drawLine(previousX, previousY, x, y);
        previousX = x;
        previousY = y;
      }
    }
  }

  private static CubicBezier cubicBezier(Point from, Point to, Bounds fromBounds, Bounds toBounds) {
    Normal startNormal = anchorNormal(fromBounds, from);
    Normal endNormal = endNormalForSpline(from, to, fromBounds, toBounds);
    double controlLen = controlLength(from, to);

    double p0x = from.x;
    double p0y = from.y;
    double p1x = p0x + startNormal.x * controlLen;
    double p1y = p0y + startNormal.y * controlLen;
    double p3x = to.x;
    double p3y = to.y;
    double p2x = p3x + endNormal.x * controlLen;
    double p2y = p3y + endNormal.y * controlLen;
    return new CubicBezier(p0x, p0y, p1x, p1y, p2x, p2y, p3x, p3y);
  }

  private static int roundCoordinate(double value) {
    return (int) Math.floor(value + 0.5);
  }

  private record CubicBezier(
      double p0x,
      double p0y,
      double p1x,
      double p1y,
      double p2x,
      double p2y,
      double p3x,
      double p3y) {

    private double[] pointAt(double t) {
      double u = 1 - t;
      double uu = u * u;
      double tt = t * t;
      double uuu = uu * u;
      double ttt = tt * t;
      double x = uuu * p0x + 3 * uu * t * p1x + 3 * u * tt * p2x + ttt * p3x;
      double y = uuu * p0y + 3 * uu * t * p1y + 3 * u * tt * p2y + ttt * p3y;
      return new double[] {x, y};
    }
  }

  private static Normal endNormalForSpline(
      Point from, Point to, Bounds fromBounds, Bounds toBounds) {
    if (toBounds.width() == 1 && toBounds.height() == 1) {
      Point towardSource = anchorToward(toBounds, fromBounds);
      return anchorNormal(toBounds, towardSource);
    }
    return anchorNormal(toBounds, to);
  }

  private static Point topMid(Bounds bounds) {
    return new Point(bounds.centerX(), bounds.y);
  }

  private static Point bottomMid(Bounds bounds) {
    return new Point(bounds.centerX(), bounds.y + bounds.height());
  }

  private static Point leftMid(Bounds bounds) {
    return new Point(bounds.x, bounds.centerY());
  }

  private static Point rightMid(Bounds bounds) {
    return new Point(bounds.x + bounds.width(), bounds.centerY());
  }
}
