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
package org.hopper.edw.datavault.hopgui.perspective.journey.poster;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hit rectangles from {@code docs/presentations/edw-component-explorer.html}, in poster coordinates
 * ({@code viewBox 0 0 1280 708}). {@code publish} is an act under the drawing and has no rectangle.
 */
public final class EdwJourneyPosterHotspots {

  public static final int POSTER_WIDTH = 1280;
  public static final int POSTER_HEIGHT = 708;

  public static final String PUBLISH = "publish";

  private static final List<Hotspot> HOTSPOTS =
      List.of(
          rect("harvester", 4, 222, 40, 356),
          rect("landing", 48, 188, 112, 266),
          rect("source-models", 178, 198, 154, 316),
          rect("dv-models", 412, 198, 154, 316),
          rect("bv-models", 646, 198, 154, 316),
          rect("dm-models", 880, 198, 154, 316),
          rect("hop-jdbc", 180, 148, 234, 50),
          rect("hop-engine", 414, 148, 634, 50),
          rect("ops", 1048, 148, 206, 50),
          rect("clients", 185, 44, 140, 68),
          rect("dv-db", 449, 42, 80, 92),
          rect("bv-db", 683, 42, 80, 92),
          rect("dm-db", 917, 42, 80, 92),
          rect("coach", 330, 252, 80, 58),
          rect("coach", 330, 422, 80, 88),
          rect("jdbc-clients", 1112, 222, 148, 108),
          rect("presentations", 1112, 406, 148, 120),
          rect("catalog", 178, 540, 856, 60),
          rect("gates", 237, 515, 36, 28),
          rect("gates", 471, 515, 36, 28),
          rect("gates", 705, 515, 36, 28),
          rect("gates", 939, 515, 36, 28),
          rect("documentation", 182, 620, 354, 52),
          rect("edw-journey", 538, 620, 356, 52),
          rect("lineage-view", 896, 620, 356, 52));

  private EdwJourneyPosterHotspots() {}

  public static List<Hotspot> hotspots() {
    return HOTSPOTS;
  }

  /** Block ids in poster order, including {@code publish}. */
  public static List<String> ids() {
    Set<String> ids = new LinkedHashSet<>();
    for (Hotspot hotspot : HOTSPOTS) {
      ids.add(hotspot.id());
    }
    ids.add(PUBLISH);
    return List.copyOf(ids);
  }

  /** Later rectangles win when they overlap, matching the explorer's paint order. */
  public static String hit(int posterX, int posterY) {
    if (posterX < 0 || posterY < 0 || posterX > POSTER_WIDTH || posterY > POSTER_HEIGHT) {
      return null;
    }
    for (int i = HOTSPOTS.size() - 1; i >= 0; i--) {
      Hotspot hotspot = HOTSPOTS.get(i);
      if (hotspot.contains(posterX, posterY)) {
        return hotspot.id();
      }
    }
    return null;
  }

  public static List<Hotspot> forId(String id) {
    List<Hotspot> found = new ArrayList<>();
    for (Hotspot hotspot : HOTSPOTS) {
      if (hotspot.id().equals(id)) {
        found.add(hotspot);
      }
    }
    return found;
  }

  /** Top-right of the union, where the status mark is drawn. */
  public static int[] badgeAnchor(String id) {
    List<Hotspot> rects = forId(id);
    if (rects.isEmpty()) {
      return null;
    }
    int right = Integer.MIN_VALUE;
    int top = Integer.MAX_VALUE;
    for (Hotspot hotspot : rects) {
      right = Math.max(right, hotspot.x() + hotspot.width());
      top = Math.min(top, hotspot.y());
    }
    return new int[] {right, top};
  }

  private static Hotspot rect(String id, int x, int y, int width, int height) {
    return new Hotspot(id, x, y, width, height);
  }

  public record Hotspot(String id, int x, int y, int width, int height) {
    public boolean contains(int px, int py) {
      return px >= x && py >= y && px < x + width && py < y + height;
    }
  }
}
