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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.hopper.core.HAttachment;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyOpsOverlay.LoadOverviewSummary;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.types.chart.HBarChartComponent;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.theme.HTheme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EdwJourneyPanelLayoutTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void chartAndTableAreLinkedAndTheTableIsNotAFixedSlot() throws Exception {
    EdwJourneySnapshot snapshot = EdwJourneySnapshot.empty();
    EdwJourneyOpsOverlay overlay =
        new EdwJourneyOpsOverlay(
            null,
            null,
            null,
            null,
            new LoadOverviewSummary("e", "run-retail", null, 12_000L, 3L, 0L, true),
            List.of(),
            List.of());
    EdwJourneyPanelSpec spec =
        EdwJourneyPanelContent.build(
            EdwJourneyPanelId.PROJECT_PULSE,
            EdwJourneyPanelContent.Inputs.of(snapshot, overlay, EdwJourneyHistory.empty(null)));
    HGeneratedCatalog catalog = EdwJourneyPanelBuilder.build(spec);
    HPresentation presentation = catalog.getPresentation();
    HPage page = presentation.getPages().get(0);
    HComponent chart = component(page, "HBarChartComponent", "HLineChartComponent");
    HComponent table = component(page, "HTableComponent");
    assertNotNull(chart);
    assertNotNull(table);
    assertTrue(chart.getComponent() instanceof HBarChartComponent);
    HBarChartComponent bars = (HBarChartComponent) chart.getComponent();
    assertTrue(bars.isUsingAngledHorizontalLabels());
    assertEquals("-25", bars.getHorizontalLabelAngle());
    HLayout tableLayout = table.getLayout();
    assertEquals(chart.getName(), tableLayout.getTop().getComponentName());
    assertEquals(HAttachment.Alignment.TOP, tableLayout.getTop().getAlignment());
    assertNotEquals(320, tableLayout.getBottom().getOffset());
    assertTrue(page.getHeight() < 800);
    assertTrue(page.getHeight() > EdwJourneyPanelBuilder.CHART_HEIGHT);

    HTheme theme =
        catalog.getProvider().getSerializer(HTheme.class).load(EdwJourneyPanelTheme.NAME);
    assertTrue(
        EdwJourneyPanelTheme.PAGE.equalsIgnoreCase(theme.getBackgroundColor().getHexColor()));
    assertTrue(
        EdwJourneyPanelTheme.HEADER.equalsIgnoreCase(
            theme.getHeaderBackGroundColor().getHexColor()));
    assertEquals(0x00, theme.getColors().get(0).getR());
    assertEquals(0x3F, theme.getColors().get(0).getG());
    assertEquals(0x5C, theme.getColors().get(0).getB());
    assertEquals(EdwJourneyPanelTheme.NAME, presentation.getDefaultThemeName());
    assertEquals(EdwJourneyPanelTheme.NAME, presentation.getDarkThemeName());
  }

  private static HComponent component(HPage page, String... pluginIds) {
    for (HComponent component : page.getComponents()) {
      if (component.getComponent() == null) {
        continue;
      }
      String plugin = component.getComponent().getPluginId();
      for (String id : pluginIds) {
        if (id.equals(plugin)) {
          return component;
        }
      }
    }
    return null;
  }
}
