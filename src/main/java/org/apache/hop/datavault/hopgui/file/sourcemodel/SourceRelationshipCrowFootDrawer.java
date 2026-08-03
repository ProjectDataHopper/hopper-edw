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

import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity;

/**
 * Draws crow's-foot cardinality symbols near a relationship endpoint.
 *
 * <p>Convention: max cardinality is drawn closest to the entity; min (optionality) sits further
 * toward the line midpoint.
 */
public final class SourceRelationshipCrowFootDrawer {

  private static final int SYMBOL_SPAN = 14;
  private static final int BAR_HALF = 5;
  private static final int FOOT_HALF = 6;
  private static final int CIRCLE_R = 3;

  private SourceRelationshipCrowFootDrawer() {}

  /**
   * @param entityAnchor attachment point on the table box
   * @param otherAnchor other end of the edge (for direction)
   * @param multiplicity multiplicity of this end
   */
  public static void draw(
      IGc gc, Point entityAnchor, Point otherAnchor, SourceRelationshipMultiplicity multiplicity) {
    if (gc == null || entityAnchor == null || otherAnchor == null) {
      return;
    }
    SourceRelationshipMultiplicity m =
        multiplicity != null ? multiplicity : SourceRelationshipMultiplicity.UNKNOWN;
    if (m == SourceRelationshipMultiplicity.UNKNOWN) {
      drawUnknown(gc, entityAnchor, otherAnchor);
      return;
    }

    double dx = otherAnchor.x - entityAnchor.x;
    double dy = otherAnchor.y - entityAnchor.y;
    double len = Math.hypot(dx, dy);
    if (len < 1e-3) {
      return;
    }
    // Unit vector from entity toward midpoint (along the edge).
    double ux = dx / len;
    double uy = dy / len;
    // Perpendicular.
    double px = -uy;
    double py = ux;

    // Max symbol near entity (distance ~4), min slightly further (~11).
    Point maxCenter =
        new Point(
            (int) Math.round(entityAnchor.x + ux * 5), (int) Math.round(entityAnchor.y + uy * 5));
    Point minCenter =
        new Point(
            (int) Math.round(entityAnchor.x + ux * 12), (int) Math.round(entityAnchor.y + uy * 12));

    if (m.isMany()) {
      drawCrowsFoot(gc, maxCenter, ux, uy, px, py);
    } else {
      drawBar(gc, maxCenter, px, py);
    }
    if (m.isMandatory()) {
      drawBar(gc, minCenter, px, py);
    } else {
      drawCircle(gc, minCenter);
    }
  }

  private static void drawBar(IGc gc, Point center, double px, double py) {
    int x1 = (int) Math.round(center.x - px * BAR_HALF);
    int y1 = (int) Math.round(center.y - py * BAR_HALF);
    int x2 = (int) Math.round(center.x + px * BAR_HALF);
    int y2 = (int) Math.round(center.y + py * BAR_HALF);
    gc.drawLine(x1, y1, x2, y2);
  }

  private static void drawCircle(IGc gc, Point center) {
    // IGc has no oval primitive; approximate optional participation with a small square.
    gc.drawRectangle(center.x - CIRCLE_R, center.y - CIRCLE_R, CIRCLE_R * 2, CIRCLE_R * 2);
  }

  private static void drawCrowsFoot(
      IGc gc, Point center, double ux, double uy, double px, double py) {
    // Three short lines fanning toward the entity (opposite of edge direction).
    double bx = -ux;
    double by = -uy;
    int tipX = (int) Math.round(center.x + bx * 2);
    int tipY = (int) Math.round(center.y + by * 2);
    for (int i = -1; i <= 1; i++) {
      double fx = bx + px * i * 0.55;
      double fy = by + py * i * 0.55;
      double fl = Math.hypot(fx, fy);
      if (fl < 1e-3) {
        continue;
      }
      fx /= fl;
      fy /= fl;
      int ex = (int) Math.round(center.x + fx * FOOT_HALF);
      int ey = (int) Math.round(center.y + fy * FOOT_HALF);
      gc.drawLine(tipX, tipY, ex, ey);
    }
  }

  private static void drawUnknown(IGc gc, Point entityAnchor, Point otherAnchor) {
    double dx = otherAnchor.x - entityAnchor.x;
    double dy = otherAnchor.y - entityAnchor.y;
    double len = Math.hypot(dx, dy);
    if (len < 1e-3) {
      return;
    }
    int x = (int) Math.round(entityAnchor.x + (dx / len) * 8);
    int y = (int) Math.round(entityAnchor.y + (dy / len) * 8);
    gc.drawText("?", x - 3, y - 6, true);
  }
}
