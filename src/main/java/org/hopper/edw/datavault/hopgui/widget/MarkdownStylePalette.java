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
package org.hopper.edw.datavault.hopgui.widget;

import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.graphics.Color;

/** Grok Build-inspired markdown accent colors for StyledText rendering. */
public final class MarkdownStylePalette {

  // Dark: bright cyan → amber → violet on deep backgrounds
  private static final int H1_DARK_RGB = 0x67E8F9;
  private static final int H2_DARK_RGB = 0xFCD34D;
  private static final int H3_DARK_RGB = 0xA78BFA;
  private static final int CODE_FOREGROUND_DARK_RGB = 0x79B8FF;
  private static final int CODE_BACKGROUND_DARK_RGB = 0x0F2744;
  private static final int CODE_BLOCK_BACKGROUND_DARK_RGB = 0x0B1F38;

  // Light: saturated hues with pale blue code backgrounds
  private static final int H1_LIGHT_RGB = 0x0E7490;
  private static final int H2_LIGHT_RGB = 0xB45309;
  private static final int H3_LIGHT_RGB = 0x6D28D9;
  private static final int CODE_FOREGROUND_LIGHT_RGB = 0x0550AE;
  private static final int CODE_BACKGROUND_LIGHT_RGB = 0xE8F4FF;
  private static final int CODE_BLOCK_BACKGROUND_LIGHT_RGB = 0xDBEAFE;

  private MarkdownStylePalette() {}

  public static Color heading1(GuiResource resources) {
    return resolve(resources, H1_DARK_RGB, H1_LIGHT_RGB);
  }

  public static Color heading2(GuiResource resources) {
    return resolve(resources, H2_DARK_RGB, H2_LIGHT_RGB);
  }

  public static Color heading3(GuiResource resources) {
    return resolve(resources, H3_DARK_RGB, H3_LIGHT_RGB);
  }

  public static Color codeForeground(GuiResource resources) {
    return resolve(resources, CODE_FOREGROUND_DARK_RGB, CODE_FOREGROUND_LIGHT_RGB);
  }

  public static Color codeBackground(GuiResource resources) {
    return resolve(resources, CODE_BACKGROUND_DARK_RGB, CODE_BACKGROUND_LIGHT_RGB);
  }

  public static Color codeBlockBackground(GuiResource resources) {
    return resolve(resources, CODE_BLOCK_BACKGROUND_DARK_RGB, CODE_BLOCK_BACKGROUND_LIGHT_RGB);
  }

  public static Color link(GuiResource resources) {
    if (isDarkMode()) {
      Color hopBlue = resources.getColorLightBlue();
      return hopBlue != null ? hopBlue : codeForeground(resources);
    }
    Color hopBlue = resources.getColorBlue();
    return hopBlue != null ? hopBlue : codeForeground(resources);
  }

  static int resolveHex(boolean darkMode, int darkHex, int lightHex) {
    return darkMode ? darkHex : lightHex;
  }

  static double relativeLuminance(int hex) {
    double r = channelLuminance((hex >> 16) & 0xFF);
    double g = channelLuminance((hex >> 8) & 0xFF);
    double b = channelLuminance(hex & 0xFF);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  static double contrastRatioAgainstWhite(int foregroundHex) {
    double l1 = relativeLuminance(foregroundHex);
    double l2 = 1.0;
    double lighter = Math.max(l1, l2);
    double darker = Math.min(l1, l2);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static double channelLuminance(int channel) {
    double normalized = channel / 255.0;
    if (normalized <= 0.03928) {
      return normalized / 12.92;
    }
    return Math.pow((normalized + 0.055) / 1.055, 2.4);
  }

  private static Color resolve(GuiResource resources, int darkHex, int lightHex) {
    return rgb(resources, resolveHex(isDarkMode(), darkHex, lightHex));
  }

  private static boolean isDarkMode() {
    try {
      return PropsUi.getInstance().isDarkMode();
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private static Color rgb(GuiResource resources, int hex) {
    int r = (hex >> 16) & 0xFF;
    int g = (hex >> 8) & 0xFF;
    int b = hex & 0xFF;
    return resources.getColor(r, g, b);
  }
}
