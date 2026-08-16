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
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphEdgeLayout;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/**
 * Computes per-relationship edge anchors with side spreading so multiple relationships on the same
 * side of a node do not stack on one midpoint. Endpoints may be tables, queries, JSON, or pipeline
 * sources.
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
    if (model == null) {
      return result;
    }

    record Provisional(
        SourceRelationship rel, Bounds childB, Bounds parentB, Side childSide, Side parentSide) {}
    List<Provisional> provisionals = new ArrayList<>();
    for (SourceRelationship relationship : model.getRelationships()) {
      if (relationship == null || !relationship.isValid()) {
        continue;
      }
      Bounds childB =
          boundsOfEndpoint(
              model,
              tableByName,
              relationship.resolveChildEndpointKind(),
              relationship.getChildTableName());
      Bounds parentB =
          boundsOfEndpoint(
              model,
              tableByName,
              relationship.resolveParentEndpointKind(),
              relationship.getParentTableName());
      if (childB == null || parentB == null) {
        continue;
      }
      Side childSide = sideFacing(childB, parentB);
      Side parentSide = sideFacing(parentB, childB);
      provisionals.add(new Provisional(relationship, childB, parentB, childSide, parentSide));
    }

    Map<String, Integer> sideCounts = new HashMap<>();
    Map<String, Integer> sideIndexes = new HashMap<>();
    for (Provisional p : provisionals) {
      String childKey =
          key(p.rel.resolveChildEndpointKind(), p.rel.getChildTableName(), p.childSide);
      String parentKey =
          key(p.rel.resolveParentEndpointKind(), p.rel.getParentTableName(), p.parentSide);
      sideCounts.merge(childKey, 1, Integer::sum);
      sideCounts.merge(parentKey, 1, Integer::sum);
    }

    for (Provisional p : provisionals) {
      String childKey =
          key(p.rel.resolveChildEndpointKind(), p.rel.getChildTableName(), p.childSide);
      String parentKey =
          key(p.rel.resolveParentEndpointKind(), p.rel.getParentTableName(), p.parentSide);
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

  static Bounds boundsOfEndpoint(
      SourceModel model,
      Map<String, SourceTable> tableByName,
      SourceEndpointKind kind,
      String name) {
    if (Utils.isEmpty(name)) {
      return null;
    }
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    if (resolved == SourceEndpointKind.TABLE) {
      SourceTable table = tableByName != null ? tableByName.get(name) : null;
      if (table == null && model != null) {
        table = model.findTable(name);
      }
      if (table == null || table.getLocation() == null) {
        return null;
      }
      return boundsOf(table);
    }
    Point loc = SourceEndpointSupport.locationOf(model, resolved, name);
    if (loc == null) {
      return null;
    }
    return new Bounds(loc.x, loc.y, 160, 80);
  }

  static Side sideFacing(Bounds from, Bounds to) {
    return fromShared(ModelGraphEdgeLayout.sideFacing(from, to));
  }

  static Point anchorOnSide(Bounds bounds, Side side, int index, int count) {
    return ModelGraphEdgeLayout.anchorOnSide(bounds, toShared(side), index, count);
  }

  private static ModelGraphEdgeLayout.Side toShared(Side side) {
    return switch (side) {
      case LEFT -> ModelGraphEdgeLayout.Side.LEFT;
      case RIGHT -> ModelGraphEdgeLayout.Side.RIGHT;
      case TOP -> ModelGraphEdgeLayout.Side.TOP;
      case BOTTOM -> ModelGraphEdgeLayout.Side.BOTTOM;
    };
  }

  private static Side fromShared(ModelGraphEdgeLayout.Side side) {
    return switch (side) {
      case LEFT -> Side.LEFT;
      case RIGHT -> Side.RIGHT;
      case TOP -> Side.TOP;
      case BOTTOM -> Side.BOTTOM;
    };
  }

  private static Bounds boundsOf(SourceTable table) {
    Point loc = table.getLocation();
    int w = Math.max(140, table.getDrawnBoxWidth() > 0 ? table.getDrawnBoxWidth() : 160);
    int h = Math.max(70, table.getDrawnBoxHeight() > 0 ? table.getDrawnBoxHeight() : 90);
    return new Bounds(loc.x, loc.y, w, h);
  }

  private static String key(SourceEndpointKind kind, String name, Side side) {
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    return resolved.name() + "|" + ConstNvl(name) + "|" + side.name();
  }

  private static String ConstNvl(String value) {
    return Utils.isEmpty(value) ? "?" : value;
  }
}
