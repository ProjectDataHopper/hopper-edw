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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/**
 * Computes per-relationship edge anchors with side spreading so multiple relationships on the same
 * side of a table do not stack on one midpoint.
 */
public final class SourceRelationshipEdgeLayout {

  public enum Side {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
  }

  public record EdgeGeometry(
      Point childAnchor, Point parentAnchor, Side childSide, Side parentSide) {}

  private SourceRelationshipEdgeLayout() {}

  public static Map<SourceRelationship, EdgeGeometry> layout(
      SourceModel model, Map<String, SourceTable> tableByName) {
    Map<SourceRelationship, EdgeGeometry> result = new HashMap<>();
    if (model == null || tableByName == null) {
      return result;
    }

    // Provisional mid anchors to determine facing sides.
    record Provisional(
        SourceRelationship rel, Bounds childB, Bounds parentB, Side childSide, Side parentSide) {}
    List<Provisional> provisionals = new ArrayList<>();
    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship == null || !relationship.isValid()) {
        continue;
      }
      SourceTable child = tableByName.get(relationship.getChildTableName());
      SourceTable parent = tableByName.get(relationship.getParentTableName());
      if (child == null
          || parent == null
          || child.getLocation() == null
          || parent.getLocation() == null) {
        continue;
      }
      Bounds childB = boundsOf(child);
      Bounds parentB = boundsOf(parent);
      Side childSide = sideFacing(childB, parentB);
      Side parentSide = sideFacing(parentB, childB);
      provisionals.add(new Provisional(relationship, childB, parentB, childSide, parentSide));
    }

    // Count edges per (tableName, side).
    Map<String, Integer> sideCounts = new HashMap<>();
    Map<String, Integer> sideIndexes = new HashMap<>();
    for (Provisional p : provisionals) {
      String childKey = key(p.rel.getChildTableName(), p.childSide);
      String parentKey = key(p.rel.getParentTableName(), p.parentSide);
      sideCounts.merge(childKey, 1, Integer::sum);
      sideCounts.merge(parentKey, 1, Integer::sum);
    }

    for (Provisional p : provisionals) {
      String childKey = key(p.rel.getChildTableName(), p.childSide);
      String parentKey = key(p.rel.getParentTableName(), p.parentSide);
      int childIndex = sideIndexes.getOrDefault(childKey, 0);
      int parentIndex = sideIndexes.getOrDefault(parentKey, 0);
      sideIndexes.put(childKey, childIndex + 1);
      sideIndexes.put(parentKey, parentIndex + 1);

      Point childAnchor =
          anchorOnSide(p.childB, p.childSide, childIndex, sideCounts.getOrDefault(childKey, 1));
      Point parentAnchor =
          anchorOnSide(p.parentB, p.parentSide, parentIndex, sideCounts.getOrDefault(parentKey, 1));
      result.put(p.rel, new EdgeGeometry(childAnchor, parentAnchor, p.childSide, p.parentSide));
    }
    return result;
  }

  static Side sideFacing(Bounds from, Bounds to) {
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

  static Point anchorOnSide(Bounds bounds, Side side, int index, int count) {
    int n = Math.max(1, count);
    int i = Math.min(Math.max(0, index), n - 1);
    double fraction = (i + 1.0) / (n + 1.0); // spread with padding
    int pad = 8;
    return switch (side) {
      case LEFT ->
          new Point(bounds.x(), bounds.y() + pad + (int) ((bounds.height() - 2 * pad) * fraction));
      case RIGHT ->
          new Point(
              bounds.x() + bounds.width(),
              bounds.y() + pad + (int) ((bounds.height() - 2 * pad) * fraction));
      case TOP ->
          new Point(bounds.x() + pad + (int) ((bounds.width() - 2 * pad) * fraction), bounds.y());
      case BOTTOM ->
          new Point(
              bounds.x() + pad + (int) ((bounds.width() - 2 * pad) * fraction),
              bounds.y() + bounds.height());
    };
  }

  private static Bounds boundsOf(SourceTable table) {
    Point loc = table.getLocation();
    int w = Math.max(140, table.getDrawnBoxWidth() > 0 ? table.getDrawnBoxWidth() : 160);
    int h = Math.max(70, table.getDrawnBoxHeight() > 0 ? table.getDrawnBoxHeight() : 90);
    return new Bounds(loc.x, loc.y, w, h);
  }

  private static String key(String tableName, Side side) {
    return ConstNvl(tableName) + "|" + side.name();
  }

  private static String ConstNvl(String value) {
    return Utils.isEmpty(value) ? "?" : value;
  }
}
