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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.hopper.core.HAttachment;
import org.hopper.core.exception.HException;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelSpec.ChartSpec;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelSpec.TableSpec;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.HBarChartComponent;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionLocation;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.simple.HSimplePresentation;
import org.hopper.presentation.theme.HTheme;

/** Turns a panel spec into an in-memory Hopper presentation with row links. */
public final class EdwJourneyPanelBuilder {

  static final int PAGE_WIDTH = 1400;
  static final int MARGIN = 16;
  static final int GAP = 12;
  static final int CHART_HEIGHT = 220;

  /** Extra room under a chart whose category labels are drawn at an angle. */
  static final int ANGLED_CHART_HEIGHT = 320;

  static final int TITLE_HEIGHT = 40;
  static final int NOTE_HEIGHT = 48;
  static final int TABLE_CHROME = 28;
  static final int TABLE_ROW = 22;

  private EdwJourneyPanelBuilder() {}

  public static HGeneratedCatalog build(EdwJourneyPanelSpec spec) throws HException {
    EdwJourneyPanelSpec panel =
        spec == null
            ? new EdwJourneyPanelSpec(
                EdwJourneyPanelId.PROJECT_PULSE, "Project pulse", null, List.of(), List.of())
            : spec;
    HSimplePresentation presentation =
        HSimplePresentation.dashboard("edw-journey-" + panel.id().name())
            .continuous()
            .designWidth(PAGE_WIDTH)
            .trendTileHeight(CHART_HEIGHT);
    presentation.addTitle(panel.title() == null ? "" : panel.title());
    int index = 1;
    List<TileLink> links = new ArrayList<>();
    Map<Integer, Integer> labelAngles = new LinkedHashMap<>();
    if (panel.note() != null && !panel.note().isBlank()) {
      presentation.addNote(panel.note());
      index++;
    }
    for (ChartSpec chart : panel.charts()) {
      List<RowMetaAndData> rows = toRows(chart.rows(), chart.value());
      boolean trend = rows.size() >= 2 && (chart.series() == null || chart.series().isBlank());
      if (trend) {
        presentation.addTrend(chart.title(), rows, chart.category(), chart.value());
      } else if (chart.series() == null || chart.series().isBlank()) {
        presentation.addComparison(chart.title(), rows, chart.category(), null, chart.value());
      } else {
        presentation.addComparison(
            chart.title(), rows, chart.category(), chart.series(), chart.value());
      }
      if (chart.linkTemplate() != null && !chart.linkTemplate().isBlank()) {
        links.add(new TileLink(index, chart.linkTemplate(), false));
      }
      if (chart.labelAngle() != null) {
        labelAngles.put(index, chart.labelAngle());
      }
      index++;
    }
    for (TableSpec table : panel.tables()) {
      presentation.addTable(
          table.title(), toRows(table.rows(), null), table.columns().toArray(String[]::new));
      links.add(new TileLink(index, "{link}", true));
      index++;
    }
    HGeneratedCatalog catalog = presentation.build();
    applyTheme(catalog);
    applyLabelAngles(catalog.getPresentation(), labelAngles);
    linkLayout(catalog.getPresentation(), panel);
    attach(catalog.getPresentation(), links);
    return catalog;
  }

  private static void applyTheme(HGeneratedCatalog catalog) throws HException {
    try {
      HTheme theme = EdwJourneyPanelTheme.light();
      catalog.getProvider().getSerializer(HTheme.class).save(theme);
      HPresentation presentation = catalog.getPresentation();
      presentation.setDefaultThemeName(EdwJourneyPanelTheme.NAME);
      presentation.setDarkThemeName(EdwJourneyPanelTheme.NAME);
    } catch (HopException e) {
      throw new HException("Unable to save the Journey presentation theme", e);
    }
  }

  /**
   * Replace the absolute stacked tiles with attachments. Text and tables take a content height.
   * Charts keep {@link #CHART_HEIGHT}. A chart and the next block sit side by side. The next band
   * hangs from the taller of the two.
   */
  static void linkLayout(HPresentation presentation, EdwJourneyPanelSpec spec) {
    if (presentation == null
        || presentation.getPages() == null
        || presentation.getPages().isEmpty()) {
      return;
    }
    HPage page = presentation.getPages().get(0);
    if (page.getComponents() == null || page.getComponents().isEmpty()) {
      return;
    }
    int tableCursor = 0;
    int chartCursor = 0;
    java.util.List<Block> headers = new java.util.ArrayList<>();
    java.util.List<Block> flow = new java.util.ArrayList<>();
    for (HComponent component : page.getComponents()) {
      String plugin =
          component.getComponent() == null ? "" : component.getComponent().getPluginId();
      if ("HLabelComponent".equals(plugin)) {
        headers.add(new Block(component, TITLE_HEIGHT));
      } else if ("HTextBlockComponent".equals(plugin)) {
        headers.add(new Block(component, NOTE_HEIGHT));
      } else if ("HLineChartComponent".equals(plugin) || "HBarChartComponent".equals(plugin)) {
        int height = CHART_HEIGHT;
        if (spec != null && chartCursor < spec.charts().size()) {
          Integer angle = spec.charts().get(chartCursor).labelAngle();
          if (angle != null) {
            height = ANGLED_CHART_HEIGHT;
          }
        }
        chartCursor++;
        flow.add(new Block(component, height));
      } else if ("HTableComponent".equals(plugin)) {
        int rows =
            spec != null && tableCursor < spec.tables().size()
                ? spec.tables().get(tableCursor).rows().size()
                : 1;
        tableCursor++;
        flow.add(new Block(component, tableHeight(rows)));
      } else {
        flow.add(new Block(component, NOTE_HEIGHT));
      }
    }
    String anchor = null;
    int y = MARGIN;
    for (Block header : headers) {
      header.component().setLayout(fullWidth(anchor, header.height()));
      anchor = header.component().getName();
      y += header.height() + GAP;
    }
    int index = 0;
    while (index < flow.size()) {
      Block left = flow.get(index);
      Block right = index + 1 < flow.size() ? flow.get(index + 1) : null;
      if (right != null) {
        left.component().setLayout(leftColumn(anchor, left.height()));
        right.component().setLayout(rightColumn(left.component().getName(), right.height()));
        Block taller = left.height() >= right.height() ? left : right;
        anchor = taller.component().getName();
        y += taller.height() + GAP;
        index += 2;
      } else {
        left.component().setLayout(fullWidth(anchor, left.height()));
        anchor = left.component().getName();
        y += left.height() + GAP;
        index++;
      }
    }
    page.setWidth(PAGE_WIDTH);
    page.setHeight(Math.max(y + MARGIN, 120));
    presentation.setDesignWidth(PAGE_WIDTH);
  }

  static int tableHeight(int rows) {
    return TABLE_CHROME + TABLE_ROW * Math.max(rows, 1);
  }

  private static HLayout fullWidth(String anchor, int height) {
    HLayout layout = new HLayout();
    layout.setLeft(edge(null, MARGIN, HAttachment.Alignment.LEFT));
    layout.setRight(edge(null, -MARGIN, HAttachment.Alignment.RIGHT));
    if (anchor == null) {
      layout.setTop(edge(null, MARGIN, HAttachment.Alignment.TOP));
      layout.setBottom(edge(null, MARGIN + height, HAttachment.Alignment.TOP));
    } else {
      layout.setTop(edge(anchor, GAP, HAttachment.Alignment.BOTTOM));
      layout.setBottom(edge(anchor, GAP + height, HAttachment.Alignment.BOTTOM));
    }
    return layout;
  }

  private static HLayout leftColumn(String anchor, int height) {
    HLayout layout = fullWidth(anchor, height);
    layout.setRight(edge(null, -8, HAttachment.Alignment.CENTER));
    return layout;
  }

  private static HLayout rightColumn(String leftName, int height) {
    HLayout layout = new HLayout();
    layout.setLeft(edge(leftName, GAP, HAttachment.Alignment.RIGHT));
    layout.setRight(edge(null, -MARGIN, HAttachment.Alignment.RIGHT));
    layout.setTop(edge(leftName, 0, HAttachment.Alignment.TOP));
    layout.setBottom(edge(leftName, height, HAttachment.Alignment.TOP));
    return layout;
  }

  private static HAttachment edge(String component, int offset, HAttachment.Alignment alignment) {
    return new HAttachment(component, 0, offset, alignment);
  }

  private static void applyLabelAngles(HPresentation presentation, Map<Integer, Integer> angles) {
    if (angles == null
        || angles.isEmpty()
        || presentation == null
        || presentation.getPages() == null
        || presentation.getPages().isEmpty()) {
      return;
    }
    List<HComponent> components = presentation.getPages().get(0).getComponents();
    if (components == null) {
      return;
    }
    for (Map.Entry<Integer, Integer> entry : angles.entrySet()) {
      if (entry.getKey() == null || entry.getKey() < 0 || entry.getKey() >= components.size()) {
        continue;
      }
      HComponent component = components.get(entry.getKey());
      if (component == null || component.getComponent() == null || entry.getValue() == null) {
        continue;
      }
      String degrees = Integer.toString(entry.getValue());
      if (component.getComponent() instanceof HBarChartComponent chart) {
        chart.setUsingAngledHorizontalLabels(true);
        chart.setHorizontalLabelAngle(degrees);
      } else if (component.getComponent() instanceof HLineChartComponent chart) {
        chart.setUsingAngledHorizontalLabels(true);
        chart.setHorizontalLabelAngle(degrees);
      }
    }
  }

  private record Block(HComponent component, int height) {}

  private static void attach(HPresentation presentation, List<TileLink> links) {
    if (presentation == null
        || presentation.getPages() == null
        || presentation.getPages().isEmpty()) {
      return;
    }
    HPage page = presentation.getPages().get(0);
    if (presentation.getInteractions() == null) {
      presentation.setInteractions(new ArrayList<>());
    }
    for (TileLink link : links) {
      if (page.getComponents() == null || link.index() >= page.getComponents().size()) {
        continue;
      }
      HComponent component = page.getComponents().get(link.index());
      if (component == null) {
        continue;
      }
      String plugin =
          component.getComponent() == null ? "" : component.getComponent().getPluginId();
      presentation
          .getInteractions()
          .add(
              new HInteraction(
                  HInteractionMethod.SINGLE_CLICK,
                  new HInteractionLocation(
                      component.getName(), plugin, "Component", null, new ArrayList<>()),
                  new HInteractionAction(
                      HInteractionAction.ActionType.OPEN_LINK_SAME_TAB, link.template())));
      if (link.editOnDoubleClick()) {
        presentation
            .getInteractions()
            .add(
                new HInteraction(
                    HInteractionMethod.DOUBLE_CLICK,
                    new HInteractionLocation(
                        component.getName(), plugin, "Component", null, new ArrayList<>()),
                    new HInteractionAction(
                        HInteractionAction.ActionType.OPEN_LINK_SAME_TAB, "{edit_link}")));
      }
    }
  }

  static List<RowMetaAndData> toRows(List<Map<String, String>> source, String numberColumn) {
    if (source == null || source.isEmpty()) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (String key : source.get(0).keySet()) {
      names.add(key);
    }
    for (Map<String, String> row : source) {
      for (String key : row.keySet()) {
        if (!names.contains(key)) {
          names.add(key);
        }
      }
    }
    List<RowMetaAndData> rows = new ArrayList<>();
    for (Map<String, String> sourceRow : source) {
      RowMeta meta = new RowMeta();
      Object[] data = new Object[names.size()];
      for (int i = 0; i < names.size(); i++) {
        String name = names.get(i);
        String text = sourceRow.get(name);
        if (name.equals(numberColumn)) {
          meta.addValueMeta(new ValueMetaNumber(name));
          data[i] = parse(text);
        } else {
          meta.addValueMeta(new ValueMetaString(name));
          data[i] = text == null ? "" : text;
        }
      }
      rows.add(new RowMetaAndData(meta, data));
    }
    return rows;
  }

  private static Double parse(String text) {
    if (text == null || text.isBlank()) {
      return 0d;
    }
    try {
      return Double.valueOf(text);
    } catch (NumberFormatException e) {
      return 0d;
    }
  }

  private record TileLink(int index, String template, boolean editOnDoubleClick) {}
}
