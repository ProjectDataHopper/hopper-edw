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
package org.hopper.edw.datavault.hopgui.perspective.journey.panel;

import java.util.Arrays;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;
import org.hopper.presentation.theme.HTheme;

/**
 * Light theme for Journey dashboards. The poster is always light, so these presentations stay light
 * even when Hop itself is dark. Series colors are the default Hop presentation palette.
 */
public final class EdwJourneyPanelTheme {

  public static final String NAME = "EDW Journey";
  public static final String PAGE = "#FAFBFC";
  public static final String INK = "#181818";
  public static final String MUTED = "#3d4a5c";
  public static final String HOP_NAVY = "#003f5c";
  public static final String HEADER = "#e7eef3";
  public static final String GRID = "#e6edf2";
  public static final String BORDER = "#d5dde6";

  private EdwJourneyPanelTheme() {}

  public static HTheme light() {
    HTheme theme = new HTheme();
    theme.setName(NAME);
    theme.setDescription("Light Journey dashboard theme, aligned with the architecture poster");
    theme
        .getColors()
        .addAll(
            Arrays.asList(
                new HColorRGB(HOP_NAVY),
                new HColorRGB("#2f4b7c"),
                new HColorRGB("#665191"),
                new HColorRGB("#a05195"),
                new HColorRGB("#d45087"),
                new HColorRGB("#f95d6a"),
                new HColorRGB("#ff7c43"),
                new HColorRGB("#ffa600")));
    theme.setBackgroundColor(new HColorRGB(PAGE));
    theme.setDefaultColor(new HColorRGB(INK));
    theme.setDefaultFont(new HFont("Arial", "12", false, false));
    theme.setBorderColor(new HColorRGB(BORDER));
    theme.setHorizontalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setHorizontalDimensionsColor(new HColorRGB(MUTED));
    theme.setVerticalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setVerticalDimensionsColor(new HColorRGB(MUTED));
    theme.setFactsFont(new HFont("Arial", "12", false, false));
    theme.setFactsColor(new HColorRGB(INK));
    theme.setTitleFont(new HFont("Arial", "16", true, false));
    theme.setTitleColor(new HColorRGB(HOP_NAVY));
    theme.setAxisColor(new HColorRGB(MUTED));
    theme.setGridColor(new HColorRGB(GRID));
    theme.setHeaderFont(new HFont("Arial", "12", true, false));
    theme.setHeaderColor(new HColorRGB(HOP_NAVY));
    theme.setHeaderBackGroundColor(new HColorRGB(HEADER));
    return theme;
  }
}
