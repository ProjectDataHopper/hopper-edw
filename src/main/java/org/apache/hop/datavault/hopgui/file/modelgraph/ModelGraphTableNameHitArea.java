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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import org.apache.hop.core.gui.Point;

/** Screen-space hit target for editable table names (includes the hover underline). */
public final class ModelGraphTableNameHitArea {

  private static final int PAD_LEFT = 2;
  private static final int PAD_TOP = 2;
  private static final int PAD_RIGHT = 2;

  /** Extra space below the text baseline where the hover underline is drawn. */
  private static final int PAD_BELOW_UNDERLINE = 4;

  private ModelGraphTableNameHitArea() {}

  public record Bounds(int x, int y, int width, int height) {}

  public static Bounds bounds(int nameX, int nameY, Point textExtent) {
    if (textExtent == null) {
      return bounds(nameX, nameY, 1, 1);
    }
    return bounds(nameX, nameY, textExtent.x, textExtent.y);
  }

  public static Bounds bounds(int nameX, int nameY, int textWidth, int textHeight) {
    int width = Math.max(1, textWidth) + PAD_LEFT + PAD_RIGHT;
    int height = Math.max(1, textHeight) + PAD_TOP + PAD_BELOW_UNDERLINE;
    return new Bounds(nameX - PAD_LEFT, nameY - PAD_TOP, width, height);
  }
}
