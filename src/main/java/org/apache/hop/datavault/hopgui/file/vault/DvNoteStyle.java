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

package org.apache.hop.datavault.hopgui.file.vault;

import org.apache.hop.core.gui.IGc;
import org.apache.hop.datavault.metadata.DvNoteType;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.graphics.Color;

/**
 * Fixed visual styles for {@link org.apache.hop.datavault.metadata.DvNote} types. Centralizes
 * colors and icons so the painter and dialog preview stay consistent.
 *
 * <p>Important and Information use distinct light fills in light mode (yellow / blue) and dark
 * fills in dark mode (dark orange / dark gray). Text uses Hop's black color, which is inverted to
 * light in dark mode, so contrast stays readable on both palettes.
 */
public final class DvNoteStyle {

  /** Dark-mode Important fill: dark orange (readable under light text). */
  private static final RgbColor IMPORTANT_BG_DARK = new RgbColor(160, 85, 20);

  /** Light-mode Important fill fallback when GuiResource is unavailable. */
  private static final RgbColor IMPORTANT_BG_LIGHT = new RgbColor(255, 220, 100);

  /** Dark-mode Information fill: dark gray (readable under light text). */
  private static final RgbColor INFORMATION_BG_DARK = new RgbColor(70, 72, 78);

  /** Light-mode Information fill fallback when GuiResource is unavailable. */
  private static final RgbColor INFORMATION_BG_LIGHT = new RgbColor(180, 210, 255);

  private DvNoteStyle() {}

  public record RgbColor(int red, int green, int blue) {}

  private static RgbColor fromColor(Color color) {
    return new RgbColor(color.getRed(), color.getGreen(), color.getBlue());
  }

  private static GuiResource resourcesOrNull() {
    try {
      return GuiResource.getInstance();
    } catch (Throwable ignored) {
      // Headless CLI (hop svg) and unit tests have no SWT GuiResource implementation.
      return null;
    }
  }

  private static boolean isDarkMode() {
    try {
      return PropsUi.getInstance().isDarkMode();
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static RgbColor backgroundColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    boolean dark = isDarkMode();
    GuiResource res = resourcesOrNull();
    if (res != null) {
      return switch (type) {
        case GENERAL -> fromColor(res.getColorDemoGray());
        case IMPORTANT -> dark ? IMPORTANT_BG_DARK : fromColor(res.getColorYellow());
        case WARNING -> fromColor(res.getColorLightRed());
        case INFORMATION -> dark ? INFORMATION_BG_DARK : fromColor(res.getColorBlueCustomGrid());
      };
    }
    return switch (type) {
      case GENERAL -> new RgbColor(230, 230, 230);
      case IMPORTANT -> dark ? IMPORTANT_BG_DARK : IMPORTANT_BG_LIGHT;
      case WARNING -> new RgbColor(255, 200, 200);
      case INFORMATION -> dark ? INFORMATION_BG_DARK : INFORMATION_BG_LIGHT;
    };
  }

  public static RgbColor borderColor(DvNoteType type) {
    if (type == null) {
      type = DvNoteType.GENERAL;
    }
    GuiResource res = resourcesOrNull();
    if (res != null) {
      return switch (type) {
        case GENERAL -> fromColor(res.getColorDarkGray());
        case IMPORTANT, INFORMATION ->
            isDarkMode() ? fromColor(res.getColorLightGray()) : fromColor(res.getColorWhite());
        case WARNING -> fromColor(res.getColorRed());
      };
    }
    return switch (type) {
      case GENERAL -> new RgbColor(80, 80, 80);
      case IMPORTANT, INFORMATION ->
          isDarkMode() ? new RgbColor(200, 200, 200) : new RgbColor(255, 255, 255);
      case WARNING -> new RgbColor(200, 0, 0);
    };
  }

  /**
   * Text foreground for note body text. Uses Hop's black color (inverted to light in dark mode) so
   * contrast works on light fills (light mode) and dark orange/gray Important/Information fills
   * (dark mode). {@code type} is retained for call-site consistency with other style methods.
   */
  public static RgbColor textColor(DvNoteType type) {
    GuiResource res = resourcesOrNull();
    if (res != null) {
      return fromColor(res.getColorBlack());
    }
    return isDarkMode() ? new RgbColor(240, 240, 240) : new RgbColor(0, 0, 0);
  }

  /**
   * Hyperlink foreground; same contrast rules as {@link #textColor(DvNoteType)} so links stay
   * readable on note fills.
   */
  public static RgbColor linkColor(DvNoteType type) {
    return textColor(type);
  }

  public static int borderWidth(DvNoteType type, boolean selected) {
    int base =
        switch (type == null ? DvNoteType.GENERAL : type) {
          case WARNING, IMPORTANT -> 2;
          default -> 1;
        };
    return selected ? base + 1 : base;
  }

  /** Optional accent icon for the note type; {@code null} means no icon. */
  public static IGc.EImage icon(DvNoteType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case IMPORTANT -> IGc.EImage.INFO;
      case WARNING -> IGc.EImage.ERROR;
      case INFORMATION -> IGc.EImage.INFO_DISABLED;
      default -> null;
    };
  }
}
