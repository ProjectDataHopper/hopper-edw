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
package org.apache.hop.datavault.executionmap;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;

/** Routes execution map reference edges as orthogonal polylines through gutter lanes. */
public final class ExecutionMapEdgeRouter {

  private static final int GUTTER_CLEARANCE = 16;
  private static final int LANE_STAGGER = 6;
  private static final int SAME_COLUMN_THRESHOLD = 24;
  private static final int SIDE_BUS_OFFSET = 24;
  private static final int ARROW_SIZE = 6;

  private ExecutionMapEdgeRouter() {}

  public record RouteRequest(
      Bounds from, Bounds to, List<Bounds> obstacles, int laneIndex, boolean targetOnRight) {}

  /** Builds an orthogonal polyline from source to target that avoids obstacle bounds. */
  public static int[] routeOrthogonal(RouteRequest request) {
    if (request == null || request.from() == null || request.to() == null) {
      return new int[0];
    }
    Bounds from = request.from();
    Bounds to = request.to();
    List<Bounds> obstacles = request.obstacles() != null ? request.obstacles() : List.of();
    int laneIndex = Math.max(0, request.laneIndex());

    Point sourceAnchor;
    Point targetAnchor;
    boolean sameColumn = Math.abs(to.x() - from.x()) <= SAME_COLUMN_THRESHOLD;

    if (sameColumn) {
      sourceAnchor = rightMid(from);
      targetAnchor = rightMid(to);
      int sideBusX =
          Math.max(from.x() + from.width(), to.x() + to.width())
              + SIDE_BUS_OFFSET
              + laneIndex * LANE_STAGGER;
      sideBusX =
          nudgeVerticalBus(sideBusX, sourceAnchor.y, targetAnchor.y, obstacles, from, to, true);
      return polyline(
          sourceAnchor.x,
          sourceAnchor.y,
          sideBusX,
          sourceAnchor.y,
          sideBusX,
          targetAnchor.y,
          targetAnchor.x,
          targetAnchor.y);
    }

    if (request.targetOnRight() || to.centerX() >= from.centerX()) {
      sourceAnchor = rightMid(from);
      targetAnchor = leftMid(to);
      int busX = defaultBusX(from, to) + laneIndex * LANE_STAGGER;
      busX = nudgeVerticalBus(busX, sourceAnchor.y, targetAnchor.y, obstacles, from, to, false);
      return polyline(
          sourceAnchor.x,
          sourceAnchor.y,
          busX,
          sourceAnchor.y,
          busX,
          targetAnchor.y,
          targetAnchor.x,
          targetAnchor.y);
    }

    sourceAnchor = leftMid(from);
    targetAnchor = rightMid(to);
    int busX = defaultBusX(to, from) - laneIndex * LANE_STAGGER;
    busX = nudgeVerticalBus(busX, sourceAnchor.y, targetAnchor.y, obstacles, from, to, false);
    return polyline(
        sourceAnchor.x,
        sourceAnchor.y,
        busX,
        sourceAnchor.y,
        busX,
        targetAnchor.y,
        targetAnchor.x,
        targetAnchor.y);
  }

  /** Draws a polyline and a directional arrowhead at the target end. */
  public static void drawRoutedEdge(IGc gc, int[] polyline) {
    if (gc == null || polyline == null || polyline.length < 4) {
      return;
    }
    for (int i = 0; i < polyline.length - 2; i += 2) {
      gc.drawLine(polyline[i], polyline[i + 1], polyline[i + 2], polyline[i + 3]);
    }
    drawArrowhead(
        gc,
        polyline[polyline.length - 4],
        polyline[polyline.length - 3],
        polyline[polyline.length - 2],
        polyline[polyline.length - 1]);
  }

  /** Returns true when any segment of the polyline intersects an obstacle interior. */
  public static boolean intersectsObstacles(int[] polyline, List<Bounds> obstacles) {
    if (polyline == null || polyline.length < 4 || obstacles == null) {
      return false;
    }
    for (int i = 0; i < polyline.length - 2; i += 2) {
      int x1 = polyline[i];
      int y1 = polyline[i + 1];
      int x2 = polyline[i + 2];
      int y2 = polyline[i + 3];
      for (Bounds obstacle : obstacles) {
        if (obstacle != null && segmentIntersectsBounds(x1, y1, x2, y2, obstacle)) {
          return true;
        }
      }
    }
    return false;
  }

  private static int defaultBusX(Bounds left, Bounds right) {
    int gapStart = left.x() + left.width();
    int gapEnd = right.x();
    if (gapEnd <= gapStart) {
      return gapStart + ExecutionMapMetrics.HUB_GUTTER / 2;
    }
    return gapStart + (gapEnd - gapStart) / 2;
  }

  private static int nudgeVerticalBus(
      int busX,
      int y1,
      int y2,
      List<Bounds> obstacles,
      Bounds from,
      Bounds to,
      boolean preferRightSide) {
    int minY = Math.min(y1, y2);
    int maxY = Math.max(y1, y2);
    if (!verticalBusBlocked(busX, minY, maxY, obstacles, from, to)) {
      return busX;
    }
    for (int step = 1; step <= 12; step++) {
      int offset = step * LANE_STAGGER;
      int candidate = preferRightSide ? busX + offset : busX - offset;
      if (!verticalBusBlocked(candidate, minY, maxY, obstacles, from, to)) {
        return candidate;
      }
      candidate = preferRightSide ? busX - offset : busX + offset;
      if (!verticalBusBlocked(candidate, minY, maxY, obstacles, from, to)) {
        return candidate;
      }
    }
    return busX;
  }

  private static boolean sameBounds(Bounds a, Bounds b) {
    if (a == null || b == null) {
      return false;
    }
    return a.x() == b.x() && a.y() == b.y() && a.width() == b.width() && a.height() == b.height();
  }

  private static boolean verticalBusBlocked(
      int busX, int minY, int maxY, List<Bounds> obstacles, Bounds from, Bounds to) {
    for (Bounds obstacle : obstacles) {
      if (obstacle == null || sameBounds(obstacle, from) || sameBounds(obstacle, to)) {
        continue;
      }
      if (verticalLineIntersectsBounds(busX, minY, maxY, obstacle)) {
        return true;
      }
    }
    return false;
  }

  private static boolean verticalLineIntersectsBounds(int x, int minY, int maxY, Bounds bounds) {
    if (x < bounds.x() - GUTTER_CLEARANCE || x > bounds.x() + bounds.width() + GUTTER_CLEARANCE) {
      return false;
    }
    return maxY >= bounds.y() - GUTTER_CLEARANCE
        && minY <= bounds.y() + bounds.height() + GUTTER_CLEARANCE;
  }

  private static boolean segmentIntersectsBounds(int x1, int y1, int x2, int y2, Bounds bounds) {
    int minX = Math.min(x1, x2);
    int maxX = Math.max(x1, x2);
    int minY = Math.min(y1, y2);
    int maxY = Math.max(y1, y2);
    return minX <= bounds.x() + bounds.width()
        && maxX >= bounds.x()
        && minY <= bounds.y() + bounds.height()
        && maxY >= bounds.y();
  }

  private static void drawArrowhead(IGc gc, int fromX, int fromY, int toX, int toY) {
    int dx = toX - fromX;
    int dy = toY - fromY;
    if (dx == 0 && dy == 0) {
      return;
    }
    double length = Math.hypot(dx, dy);
    double ux = dx / length;
    double uy = dy / length;
    double px = -uy;
    double py = ux;

    int tipX = toX;
    int tipY = toY;
    int backX = (int) Math.round(tipX - ux * ARROW_SIZE);
    int backY = (int) Math.round(tipY - uy * ARROW_SIZE);
    int leftX = (int) Math.round(backX + px * (ARROW_SIZE / 2.0));
    int leftY = (int) Math.round(backY + py * (ARROW_SIZE / 2.0));
    int rightX = (int) Math.round(backX - px * (ARROW_SIZE / 2.0));
    int rightY = (int) Math.round(backY - py * (ARROW_SIZE / 2.0));

    gc.fillPolygon(new int[] {tipX, tipY, leftX, leftY, rightX, rightY});
  }

  private static Point rightMid(Bounds bounds) {
    return new Point(bounds.x() + bounds.width(), bounds.centerY());
  }

  private static Point leftMid(Bounds bounds) {
    return new Point(bounds.x(), bounds.centerY());
  }

  private static int[] polyline(int... coordinates) {
    return coordinates;
  }

  /** Collects obstacle bounds excluding the endpoints of a single edge. */
  public static List<Bounds> obstaclesForEdge(List<Bounds> allBounds, Bounds from, Bounds to) {
    List<Bounds> obstacles = new ArrayList<>();
    if (allBounds == null) {
      return obstacles;
    }
    for (Bounds bounds : allBounds) {
      if (bounds == null || sameBounds(bounds, from) || sameBounds(bounds, to)) {
        continue;
      }
      obstacles.add(bounds);
    }
    return obstacles;
  }
}
