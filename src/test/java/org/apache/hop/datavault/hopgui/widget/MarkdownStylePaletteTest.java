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
package org.apache.hop.datavault.hopgui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownStylePaletteTest {

  private static final int H1_DARK = 0x67E8F9;
  private static final int H1_LIGHT = 0x0E7490;
  private static final int H2_DARK = 0xFCD34D;
  private static final int H2_LIGHT = 0xB45309;
  private static final int H3_DARK = 0xA78BFA;
  private static final int H3_LIGHT = 0x6D28D9;
  private static final int CODE_FG_DARK = 0x79B8FF;
  private static final int CODE_FG_LIGHT = 0x0550AE;

  @Test
  void resolveHexSelectsDarkOrLightToken() {
    assertEquals(H1_DARK, MarkdownStylePalette.resolveHex(true, H1_DARK, H1_LIGHT));
    assertEquals(H1_LIGHT, MarkdownStylePalette.resolveHex(false, H1_DARK, H1_LIGHT));
    assertEquals(H2_DARK, MarkdownStylePalette.resolveHex(true, H2_DARK, H2_LIGHT));
    assertEquals(H2_LIGHT, MarkdownStylePalette.resolveHex(false, H2_DARK, H2_LIGHT));
  }

  @Test
  void lightHeadingColorsContrastWithWhiteBackground() {
    assertTrue(MarkdownStylePalette.contrastRatioAgainstWhite(H1_LIGHT) >= 4.5);
    assertTrue(MarkdownStylePalette.contrastRatioAgainstWhite(H2_LIGHT) >= 4.5);
    assertTrue(MarkdownStylePalette.contrastRatioAgainstWhite(H3_LIGHT) >= 4.5);
  }

  @Test
  void lightCodeForegroundContrastsWithWhiteBackground() {
    assertTrue(MarkdownStylePalette.contrastRatioAgainstWhite(CODE_FG_LIGHT) >= 4.5);
  }

  @Test
  void darkHeadingColorsAreLighterThanLightVariants() {
    assertTrue(
        MarkdownStylePalette.relativeLuminance(H1_DARK)
            > MarkdownStylePalette.relativeLuminance(H1_LIGHT));
    assertTrue(
        MarkdownStylePalette.relativeLuminance(H2_DARK)
            > MarkdownStylePalette.relativeLuminance(H2_LIGHT));
    assertTrue(
        MarkdownStylePalette.relativeLuminance(CODE_FG_DARK)
            > MarkdownStylePalette.relativeLuminance(CODE_FG_LIGHT));
  }
}
