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
package org.hopper.edw.datavault.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes overlapping layout rectangles apart. Force-directed placement treats nodes as points
 * unless a later pass restores the card gap.
 */
public final class ElkRectangleSeparation {

  private static final int MAX_ITERATIONS = 24;

  private ElkRectangleSeparation() {}

  public static Map<String, ElkLayoutBox> separate(Map<String, ElkLayoutBox> boxes, int gap) {
    if (boxes == null || boxes.size() < 2) {
      return boxes != null ? boxes : Map.of();
    }
    int spacing = Math.max(0, gap);
    List<Mutable> nodes = new ArrayList<>();
    for (ElkLayoutBox box : boxes.values()) {
      if (box != null) {
        nodes.add(new Mutable(box));
      }
    }
    nodes.sort(Comparator.comparing(node -> node.id, Comparator.nullsFirst(String::compareTo)));
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      boolean moved = false;
      for (int i = 0; i < nodes.size(); i++) {
        for (int j = i + 1; j < nodes.size(); j++) {
          if (pushApart(nodes.get(i), nodes.get(j), spacing)) {
            moved = true;
          }
        }
      }
      if (!moved) {
        break;
      }
    }
    Map<String, ElkLayoutBox> separated = new LinkedHashMap<>();
    for (Mutable node : nodes) {
      separated.put(node.id, node.toBox());
    }
    return separated;
  }

  private static boolean pushApart(Mutable a, Mutable b, int gap) {
    int overlapX = Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x);
    int overlapY = Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y);
    if (overlapX + gap <= 0 || overlapY + gap <= 0) {
      return false;
    }
    int centerAx = a.x + a.width / 2;
    int centerBx = b.x + b.width / 2;
    int centerAy = a.y + a.height / 2;
    int centerBy = b.y + b.height / 2;
    boolean separateOnX = overlapX <= overlapY;
    if (separateOnX && centerAx == centerBx) {
      separateOnX = a.id.compareTo(b.id) <= 0;
    }
    if (!separateOnX && centerAy == centerBy && overlapX > overlapY) {
      separateOnX = false;
    }
    if (separateOnX) {
      int push = overlapX + gap;
      Mutable min =
          centerAx < centerBx || (centerAx == centerBx && a.id.compareTo(b.id) <= 0) ? a : b;
      Mutable max = min == a ? b : a;
      int half = push / 2;
      min.x -= half;
      max.x += push - half;
    } else {
      int push = overlapY + gap;
      Mutable min =
          centerAy < centerBy || (centerAy == centerBy && a.id.compareTo(b.id) <= 0) ? a : b;
      Mutable max = min == a ? b : a;
      int half = push / 2;
      min.y -= half;
      max.y += push - half;
    }
    return true;
  }

  private static final class Mutable {
    private final String id;
    private int x;
    private int y;
    private final int width;
    private final int height;

    private Mutable(ElkLayoutBox box) {
      id = box.id();
      x = box.x();
      y = box.y();
      width = box.width();
      height = box.height();
    }

    private ElkLayoutBox toBox() {
      return new ElkLayoutBox(id, x, y, width, height);
    }
  }
}
