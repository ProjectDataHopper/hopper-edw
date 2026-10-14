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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.layout.SourceModelEndpoints;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;

/**
 * Places one source-model card at the origin and its neighbors on rings. Positions are a view. They
 * are not written back to the model.
 */
public final class SourceModelBrowseLayout {

  public static final int GAP = 48;
  private static final int MAX_GROWTH = 16;

  private SourceModelBrowseLayout() {}

  public record Node(
      SourceEndpointKind kind, String name, int ring, int x, int y, int width, int height) {

    public Point location() {
      return new Point(x, y);
    }

    public int centerX() {
      return x + width / 2;
    }

    public int centerY() {
      return y + height / 2;
    }
  }

  public record Result(
      SourceEndpointKind focusKind, String focusName, int depth, Map<String, Node> nodes) {

    public boolean isVisible(SourceEndpointKind kind, String name) {
      return node(kind, name) != null;
    }

    public Point location(SourceEndpointKind kind, String name) {
      Node node = node(kind, name);
      return node != null ? node.location() : null;
    }

    public Node node(SourceEndpointKind kind, String name) {
      if (nodes == null || Utils.isEmpty(name)) {
        return null;
      }
      return nodes.get(SourceModelEndpoints.id(kind, name));
    }

    public Node focus() {
      return node(focusKind, focusName);
    }

    public int ringOf(SourceEndpointKind kind, String name) {
      Node node = node(kind, name);
      return node != null ? node.ring() : -1;
    }

    public List<Node> ring(int ring) {
      if (nodes == null) {
        return List.of();
      }
      return nodes.values().stream()
          .filter(node -> node.ring() == ring)
          .sorted(Comparator.comparing(Node::name, Comparator.nullsFirst(String::compareTo)))
          .toList();
    }

    /**
     * An outer-ring edge is drawn only when it touches the focus or the inner ring, so the second
     * ring does not become another hairball.
     */
    public boolean drawsRelationship(SourceRelationship relationship) {
      if (relationship == null) {
        return false;
      }
      int childRing =
          ringOf(relationship.resolveChildEndpointKind(), relationship.getChildTableName());
      int parentRing =
          ringOf(relationship.resolveParentEndpointKind(), relationship.getParentTableName());
      if (childRing < 0 || parentRing < 0) {
        return false;
      }
      if (depth <= 1) {
        return true;
      }
      return childRing <= 1 || parentRing <= 1;
    }

    /** Move every card by the same delta. The ring shape stays; only the origin changes. */
    public Result translated(int dx, int dy) {
      if ((dx == 0 && dy == 0) || nodes == null || nodes.isEmpty()) {
        return this;
      }
      Map<String, Node> moved = new LinkedHashMap<>();
      for (Node node : nodes.values()) {
        Node shifted =
            new Node(
                node.kind(),
                node.name(),
                node.ring(),
                node.x() + dx,
                node.y() + dy,
                node.width(),
                node.height());
        moved.put(SourceModelEndpoints.id(shifted.kind(), shifted.name()), shifted);
      }
      return new Result(focusKind, focusName, depth, moved);
    }

    public Point maximum() {
      int maxX = 1;
      int maxY = 1;
      if (nodes != null) {
        for (Node node : nodes.values()) {
          maxX = Math.max(maxX, node.x() + node.width());
          maxY = Math.max(maxY, node.y() + node.height());
        }
      }
      return new Point(maxX, maxY);
    }
  }

  public static Result layout(
      SourceModel model, SourceEndpointKind focusKind, String focusName, int depth) {
    int hops = depth >= 2 ? 2 : 1;
    SourceEndpointKind kind = focusKind != null ? focusKind : SourceEndpointKind.TABLE;
    Map<String, Node> nodes = new LinkedHashMap<>();
    if (model == null
        || Utils.isEmpty(focusName)
        || !SourceModelEndpoints.exists(model, kind, focusName)) {
      return new Result(kind, focusName, hops, nodes);
    }
    Map<String, Integer> distance = distances(model, kind, focusName, hops);
    SourceModelEndpoints.Endpoint focusCard = card(model, kind, focusName);
    if (focusCard == null) {
      return new Result(kind, focusName, hops, nodes);
    }
    Node focus =
        new Node(
            kind,
            focusName,
            0,
            -focusCard.width() / 2,
            -focusCard.height() / 2,
            focusCard.width(),
            focusCard.height());
    nodes.put(SourceModelEndpoints.id(kind, focusName), focus);

    List<SourceModelEndpoints.Endpoint> inner = ringCards(model, distance, 1);
    List<Node> placed = new ArrayList<>();
    placed.add(focus);
    double innerRadius = placeRing(placed, inner, 1, nodes, initialRadius(focus, inner));
    if (hops >= 2) {
      List<SourceModelEndpoints.Endpoint> outer = ringCards(model, distance, 2);
      double outerStart = innerRadius + halfDiagonal(focus) + maxHalfDiagonal(outer) + GAP;
      placeRing(placed, outer, 2, nodes, Math.max(innerRadius + GAP, outerStart));
    }
    return new Result(kind, focusName, hops, nodes);
  }

  private static Map<String, Integer> distances(
      SourceModel model, SourceEndpointKind focusKind, String focusName, int hops) {
    Map<String, Integer> distance = new LinkedHashMap<>();
    String focusId = SourceModelEndpoints.id(focusKind, focusName);
    distance.put(focusId, 0);
    List<String> frontier = new ArrayList<>();
    frontier.add(focusId);
    for (int hop = 0; hop < hops && !frontier.isEmpty(); hop++) {
      List<String> next = new ArrayList<>();
      for (String current : frontier) {
        int fromRing = distance.get(current);
        for (SourceRelationship relationship : model.getRelationships()) {
          if (relationship == null) {
            continue;
          }
          String childId =
              SourceModelEndpoints.id(
                  relationship.resolveChildEndpointKind(), relationship.getChildTableName());
          String parentId =
              SourceModelEndpoints.id(
                  relationship.resolveParentEndpointKind(), relationship.getParentTableName());
          String other = null;
          SourceEndpointKind otherKind = null;
          String otherName = null;
          if (current.equals(childId)) {
            other = parentId;
            otherKind = relationship.resolveParentEndpointKind();
            otherName = relationship.getParentTableName();
          } else if (current.equals(parentId)) {
            other = childId;
            otherKind = relationship.resolveChildEndpointKind();
            otherName = relationship.getChildTableName();
          }
          if (other == null || distance.containsKey(other)) {
            continue;
          }
          if (!SourceModelEndpoints.exists(model, otherKind, otherName)) {
            continue;
          }
          distance.put(other, fromRing + 1);
          next.add(other);
        }
      }
      frontier = next;
    }
    return distance;
  }

  private static List<SourceModelEndpoints.Endpoint> ringCards(
      SourceModel model, Map<String, Integer> distance, int ring) {
    List<SourceModelEndpoints.Endpoint> cards = new ArrayList<>();
    for (SourceModelEndpoints.Endpoint card : SourceModelEndpoints.cards(model)) {
      Integer hop = distance.get(SourceModelEndpoints.id(card.kind(), card.name()));
      if (hop != null && hop == ring) {
        cards.add(card);
      }
    }
    cards.sort(Comparator.comparing(SourceModelEndpoints.Endpoint::name, String::compareTo));
    return cards;
  }

  private static SourceModelEndpoints.Endpoint card(
      SourceModel model, SourceEndpointKind kind, String name) {
    for (SourceModelEndpoints.Endpoint card : SourceModelEndpoints.cards(model)) {
      if (card.kind() == kind && name.equals(card.name())) {
        return card;
      }
    }
    return null;
  }

  private static double placeRing(
      List<Node> already,
      List<SourceModelEndpoints.Endpoint> cards,
      int ring,
      Map<String, Node> nodes,
      double startRadius) {
    if (cards.isEmpty()) {
      return startRadius;
    }
    double radius = Math.max(GAP, startRadius);
    List<Node> placed = List.of();
    for (int attempt = 0; attempt < MAX_GROWTH; attempt++) {
      placed = positions(cards, ring, radius);
      if (disjoint(already, placed)) {
        break;
      }
      radius = radius * 1.15 + GAP;
    }
    already.addAll(placed);
    for (Node node : placed) {
      nodes.put(SourceModelEndpoints.id(node.kind(), node.name()), node);
    }
    return radius;
  }

  private static List<Node> positions(
      List<SourceModelEndpoints.Endpoint> cards, int ring, double radius) {
    List<Node> placed = new ArrayList<>();
    int count = cards.size();
    for (int i = 0; i < count; i++) {
      SourceModelEndpoints.Endpoint card = cards.get(i);
      double theta = -Math.PI / 2.0 + (2.0 * Math.PI * i) / count;
      int centerX = (int) Math.round(radius * Math.cos(theta));
      int centerY = (int) Math.round(radius * Math.sin(theta));
      placed.add(
          new Node(
              card.kind(),
              card.name(),
              ring,
              centerX - card.width() / 2,
              centerY - card.height() / 2,
              card.width(),
              card.height()));
    }
    return placed;
  }

  private static boolean disjoint(List<Node> already, List<Node> placed) {
    for (int i = 0; i < placed.size(); i++) {
      if (overlapsAny(placed.get(i), already)) {
        return false;
      }
      for (int j = i + 1; j < placed.size(); j++) {
        if (!separated(placed.get(i), placed.get(j))) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean overlapsAny(Node node, List<Node> others) {
    for (Node other : others) {
      if (!separated(node, other)) {
        return true;
      }
    }
    return false;
  }

  private static boolean separated(Node a, Node b) {
    return a.x() + a.width() + GAP <= b.x()
        || b.x() + b.width() + GAP <= a.x()
        || a.y() + a.height() + GAP <= b.y()
        || b.y() + b.height() + GAP <= a.y();
  }

  private static double initialRadius(Node focus, List<SourceModelEndpoints.Endpoint> cards) {
    if (cards.isEmpty()) {
      return GAP;
    }
    double radius = 0;
    int widest = 0;
    for (SourceModelEndpoints.Endpoint card : cards) {
      widest = Math.max(widest, card.width());
      radius =
          Math.max(
              radius, halfDiagonal(focus) + Math.hypot(card.width(), card.height()) / 2.0 + GAP);
    }
    if (cards.size() >= 2) {
      double chord = widest + GAP;
      radius = Math.max(radius, chord / (2.0 * Math.sin(Math.PI / cards.size())));
    }
    return radius;
  }

  private static double halfDiagonal(Node node) {
    return Math.hypot(node.width(), node.height()) / 2.0;
  }

  private static double maxHalfDiagonal(List<SourceModelEndpoints.Endpoint> cards) {
    double max = 0;
    for (SourceModelEndpoints.Endpoint card : cards) {
      max = Math.max(max, Math.hypot(card.width(), card.height()) / 2.0);
    }
    return max;
  }
}
