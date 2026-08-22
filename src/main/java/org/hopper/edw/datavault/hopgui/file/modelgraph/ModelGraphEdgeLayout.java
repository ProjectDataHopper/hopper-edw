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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;

/**
 * Spreads relationship anchors along the facing side of each node so multiple edges do not stack on
 * one midpoint. Callers collect every visible edge, then look up per-edge geometry by id.
 */
public final class ModelGraphEdgeLayout {

  public enum Side {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
  }

  public record Edge(String id, String fromKey, Bounds fromBounds, String toKey, Bounds toBounds) {}

  public record EdgeGeometry(Point fromAnchor, Point toAnchor, Side fromSide, Side toSide) {}

  private static final int SIDE_PAD = 8;

  private ModelGraphEdgeLayout() {}

  public static Map<String, EdgeGeometry> layout(List<Edge> edges) {
    Map<String, EdgeGeometry> result = new LinkedHashMap<>();
    if (edges == null || edges.isEmpty()) {
      return result;
    }

    record Provisional(Edge edge, Side fromSide, Side toSide) {}
    record Endpoint(String edgeId, boolean fromEnd, Bounds thisBounds, Bounds otherBounds) {}
    record SideGroup(String nodeKey, Side side) {}

    List<Provisional> provisionals = new ArrayList<>();
    for (Edge edge : edges) {
      if (edge == null
          || Utils.isEmpty(edge.id())
          || Utils.isEmpty(edge.fromKey())
          || Utils.isEmpty(edge.toKey())
          || edge.fromKey().equals(edge.toKey())
          || edge.fromBounds() == null
          || edge.toBounds() == null) {
        continue;
      }
      Side fromSide = sideFacing(edge.fromBounds(), edge.toBounds());
      Side toSide = sideFacing(edge.toBounds(), edge.fromBounds());
      provisionals.add(new Provisional(edge, fromSide, toSide));
    }

    Map<SideGroup, List<Endpoint>> groups = new LinkedHashMap<>();
    for (Provisional p : provisionals) {
      groups
          .computeIfAbsent(new SideGroup(p.edge.fromKey(), p.fromSide), unused -> new ArrayList<>())
          .add(new Endpoint(p.edge.id(), true, p.edge.fromBounds(), p.edge.toBounds()));
      groups
          .computeIfAbsent(new SideGroup(p.edge.toKey(), p.toSide), unused -> new ArrayList<>())
          .add(new Endpoint(p.edge.id(), false, p.edge.toBounds(), p.edge.fromBounds()));
    }

    record Partial(Point fromAnchor, Point toAnchor, Side fromSide, Side toSide) {
      private Partial withFrom(Point anchor, Side side) {
        return new Partial(anchor, toAnchor, side, toSide);
      }

      private Partial withTo(Point anchor, Side side) {
        return new Partial(fromAnchor, anchor, fromSide, side);
      }
    }

    Map<String, Partial> partials = new HashMap<>();
    for (Provisional p : provisionals) {
      partials.put(p.edge.id(), new Partial(null, null, p.fromSide, p.toSide));
    }

    for (Map.Entry<SideGroup, List<Endpoint>> entry : groups.entrySet()) {
      Side side = entry.getKey().side();
      List<Endpoint> group = entry.getValue();
      group.sort(
          (a, b) -> {
            int byOther = compareAlongSide(side, a.otherBounds(), b.otherBounds());
            if (byOther != 0) {
              return byOther;
            }
            return a.edgeId().compareTo(b.edgeId());
          });
      int count = group.size();
      for (int i = 0; i < count; i++) {
        Endpoint endpoint = group.get(i);
        Point anchor = anchorOnSide(endpoint.thisBounds(), side, i, count);
        Partial current = partials.get(endpoint.edgeId());
        if (current == null) {
          continue;
        }
        partials.put(
            endpoint.edgeId(),
            endpoint.fromEnd() ? current.withFrom(anchor, side) : current.withTo(anchor, side));
      }
    }

    for (Provisional p : provisionals) {
      Partial partial = partials.get(p.edge.id());
      if (partial == null || partial.fromAnchor() == null || partial.toAnchor() == null) {
        continue;
      }
      result.put(
          p.edge.id(),
          new EdgeGeometry(
              partial.fromAnchor(), partial.toAnchor(), partial.fromSide(), partial.toSide()));
    }
    return result;
  }

  public static Side sideFacing(Bounds from, Bounds to) {
    Point mid = ModelGraphConnectionGeometry.anchorToward(from, to);
    if (mid.x <= from.x()) {
      return Side.LEFT;
    }
    if (mid.x >= from.x() + from.width()) {
      return Side.RIGHT;
    }
    if (mid.y <= from.y()) {
      return Side.TOP;
    }
    return Side.BOTTOM;
  }

  public static Point anchorOnSide(Bounds bounds, Side side, int index, int count) {
    int n = Math.max(1, count);
    int i = Math.min(Math.max(0, index), n - 1);
    double fraction = (i + 1.0) / (n + 1.0);
    return switch (side) {
      case LEFT ->
          new Point(
              bounds.x(),
              bounds.y() + SIDE_PAD + (int) ((bounds.height() - 2 * SIDE_PAD) * fraction));
      case RIGHT ->
          new Point(
              bounds.x() + bounds.width(),
              bounds.y() + SIDE_PAD + (int) ((bounds.height() - 2 * SIDE_PAD) * fraction));
      case TOP ->
          new Point(
              bounds.x() + SIDE_PAD + (int) ((bounds.width() - 2 * SIDE_PAD) * fraction),
              bounds.y());
      case BOTTOM ->
          new Point(
              bounds.x() + SIDE_PAD + (int) ((bounds.width() - 2 * SIDE_PAD) * fraction),
              bounds.y() + bounds.height());
    };
  }

  private static int compareAlongSide(Side side, Bounds a, Bounds b) {
    if (side == Side.LEFT || side == Side.RIGHT) {
      int byY = Integer.compare(a.centerY(), b.centerY());
      if (byY != 0) {
        return byY;
      }
      return Integer.compare(a.centerX(), b.centerX());
    }
    int byX = Integer.compare(a.centerX(), b.centerX());
    if (byX != 0) {
      return byX;
    }
    return Integer.compare(a.centerY(), b.centerY());
  }
}
