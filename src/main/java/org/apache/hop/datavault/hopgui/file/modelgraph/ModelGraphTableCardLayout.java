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
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;

/** Shared table-card layout metrics and drawing helpers for model graph painters. */
public final class ModelGraphTableCardLayout {

  public static final int ICON_SIZE = 32;
  public static final int MARGIN = 5;
  public static final int SECONDARY_LABEL_GAP = 2;

  private ModelGraphTableCardLayout() {}

  public record BoxSize(int width, int height) {}

  public static BoxSize computeBoxSize(
      IGc gc, String name, String secondaryLine, String typeLabel, String extraLineBelowType) {
    String resolvedName = Utils.isEmpty(name) ? "?" : name;
    String resolvedType = Utils.isEmpty(typeLabel) ? "" : typeLabel;
    String resolvedSecondary = Utils.isEmpty(secondaryLine) ? "" : secondaryLine;
    String resolvedExtra = Utils.isEmpty(extraLineBelowType) ? "" : extraLineBelowType;

    gc.setFont(EFont.GRAPH);
    Point nameExtent = gc.textExtent(resolvedName);
    gc.setFont(EFont.SMALL);
    Point typeExtent = gc.textExtent(resolvedType);
    Point secondaryExtent =
        Utils.isEmpty(resolvedSecondary) ? new Point(0, 0) : gc.textExtent(resolvedSecondary);
    Point extraExtent =
        Utils.isEmpty(resolvedExtra) ? new Point(0, 0) : gc.textExtent(resolvedExtra);
    gc.setFont(EFont.GRAPH);

    int textColumnWidth = Math.max(nameExtent.x, secondaryExtent.x);
    int textColumnHeight =
        nameExtent.y + (secondaryExtent.y > 0 ? SECONDARY_LABEL_GAP + secondaryExtent.y : 0);
    int leftColumnHeight = ICON_SIZE + MARGIN + typeExtent.y;
    if (extraExtent.y > 0) {
      leftColumnHeight += SECONDARY_LABEL_GAP + extraExtent.y;
    }
    int boxWidth = MARGIN + ICON_SIZE + MARGIN + textColumnWidth + MARGIN;
    int boxHeight = MARGIN + Math.max(leftColumnHeight, textColumnHeight) + MARGIN;
    return new BoxSize(boxWidth, boxHeight);
  }

  public static int nameX(int boxX) {
    return boxX + MARGIN + ICON_SIZE + MARGIN;
  }

  public static int nameY(int boxY) {
    return boxY + MARGIN;
  }

  public static int typeLabelY(int boxY) {
    return boxY + MARGIN + ICON_SIZE + MARGIN;
  }

  public static int typeLabelX(int boxX) {
    return boxX + MARGIN;
  }

  public static int secondaryLineY(int boxY, Point nameExtent) {
    return nameY(boxY) + nameExtent.y + SECONDARY_LABEL_GAP;
  }

  public static int extraLineBelowTypeY(int boxY, Point typeExtent) {
    return typeLabelY(boxY) + typeExtent.y + SECONDARY_LABEL_GAP;
  }

  public static void drawSvgIcon(
      IGc gc, ClassLoader classLoader, String imagePath, int boxX, int boxY, float magnification) {
    boolean drawn =
        ModelGraphSvgIconCache.drawIcon(
            gc,
            classLoader,
            imagePath,
            boxX + MARGIN,
            boxY + MARGIN,
            ICON_SIZE,
            ICON_SIZE,
            magnification);
    if (!drawn) {
      gc.setBackground(EColor.BLUE);
      gc.fillRectangle(boxX + MARGIN, boxY + MARGIN, ICON_SIZE, ICON_SIZE);
    }
  }

  public static Point drawName(IGc gc, String name, int boxX, int boxY, boolean underline) {
    gc.setFont(EFont.GRAPH);
    gc.setForeground(EColor.BLACK);
    String resolvedName = Utils.isEmpty(name) ? "?" : name;
    int textX = nameX(boxX);
    int textY = nameY(boxY);
    gc.drawText(resolvedName, textX, textY, true);
    Point extent = gc.textExtent(resolvedName);
    if (underline) {
      gc.drawLine(textX, textY + extent.y, textX + extent.x, textY + extent.y);
    }
    return extent;
  }

  public static void drawSecondaryLine(
      IGc gc, String secondaryLine, int boxX, int boxY, Point nameExtent) {
    if (Utils.isEmpty(secondaryLine)) {
      return;
    }
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.DARKGRAY);
    gc.drawText(secondaryLine, nameX(boxX), secondaryLineY(boxY, nameExtent), true);
  }

  public static Point drawTypeBelowIcon(IGc gc, String typeLabel, int boxX, int boxY) {
    gc.setFont(EFont.SMALL);
    gc.setForeground(EColor.BLACK);
    String resolvedType = Utils.isEmpty(typeLabel) ? "" : typeLabel;
    gc.drawText(resolvedType, typeLabelX(boxX), typeLabelY(boxY), true);
    return gc.textExtent(resolvedType);
  }

  public static void drawExtraLineBelowType(
      IGc gc, String extraLine, int boxX, int boxY, Point typeExtent, int[] accentRgb) {
    if (Utils.isEmpty(extraLine)) {
      return;
    }
    gc.setFont(EFont.SMALL);
    if (accentRgb != null && accentRgb.length == 3) {
      gc.setForeground(accentRgb[0], accentRgb[1], accentRgb[2]);
    } else {
      gc.setForeground(EColor.DARKGRAY);
    }
    gc.drawText(extraLine, typeLabelX(boxX), extraLineBelowTypeY(boxY, typeExtent), true);
  }
}
