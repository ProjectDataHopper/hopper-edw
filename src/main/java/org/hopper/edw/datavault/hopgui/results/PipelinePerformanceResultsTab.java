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
package org.hopper.edw.datavault.hopgui.results;

import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.tab.GuiTab;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.hopper.edw.datavault.hopgui.PresentationGuiPlugin;
import org.hopper.edw.datavault.presentation.PerformanceSnapshotRows;
import org.hopper.edw.datavault.presentation.PerformanceSnapshotRows.Metric;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.connector.types.memory.HInMemoryRowsConnector;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.simple.HSimplePresentation;
import org.hopper.presentation.swt.HPresentationChrome;
import org.hopper.presentation.swt.HPresentationViewer;
import org.hopper.presentation.swt.HPresentationZoom;

/**
 * Pipeline execution-results tab: performance-over-time chart from transform snapshots, drawn by
 * the Hopper presentation engine (desktop Canvas and Hop Web Browser/SVG).
 */
@GuiPlugin
public class PipelinePerformanceResultsTab {

  public static final Class<?> PKG = PresentationGuiPlugin.class;

  private final HopGui hopGui;
  private final HopGuiPipelineGraph pipelineGraph;
  private CTabItem tab;
  private Combo wMetric;
  private HPresentationViewer viewer;
  private HGeneratedCatalog catalog;
  private String lastFingerprint;

  public PipelinePerformanceResultsTab(HopGui hopGui, HopGuiPipelineGraph pipelineGraph) {
    this.hopGui = hopGui;
    this.pipelineGraph = pipelineGraph;
  }

  @GuiTab(
      id = "20000-pipeline-graph-performance-tab",
      parentId = HopGuiPipelineGraph.PIPELINE_GRAPH_TABS,
      description = "Performance")
  public CTabItem addPerformanceTab(CTabFolder tabFolder) {
    tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setImage(GuiResource.getInstance().getImageShowPerf());
    tab.setText(BaseMessages.getString(PKG, "PipelinePerformanceResultsTab.Tab"));

    Composite composite = new Composite(tabFolder, SWT.NONE);
    composite.setLayout(new FormLayout());
    PropsUi.setLook(composite);
    int margin = PropsUi.getMargin();

    Label wlMetric = new Label(composite, SWT.RIGHT);
    PropsUi.setLook(wlMetric);
    wlMetric.setText(BaseMessages.getString(PKG, "PipelinePerformanceResultsTab.Metric.Label"));
    FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.top = new FormAttachment(0, margin);
    wlMetric.setLayoutData(fdLabel);

    wMetric = new Combo(composite, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wMetric);
    for (Metric metric : Metric.values()) {
      wMetric.add(metricLabel(metric));
    }
    wMetric.select(0);
    FormData fdCombo = new FormData();
    fdCombo.left = new FormAttachment(wlMetric, margin);
    fdCombo.top = new FormAttachment(0, margin);
    fdCombo.right = new FormAttachment(60, 0);
    wMetric.setLayoutData(fdCombo);
    wMetric.addListener(
        SWT.Selection,
        e -> {
          lastFingerprint = null;
          refreshChart();
        });

    try {
      PresentationGuiPlugin.ensureEnvironment();
      catalog = buildCatalog(List.of());
      viewer =
          new HPresentationViewer(
              composite,
              new LoggingObject("pipeline-performance"),
              catalog.getProvider(),
              catalog.getPresentation(),
              HPresentationChrome.ZOOM,
              null);
      viewer.setFitMode(HPresentationZoom.WIDTH);
      viewer.setLiveRefresh(this::refreshChart);
      FormData fdViewer = new FormData();
      fdViewer.left = new FormAttachment(0, 0);
      fdViewer.top = new FormAttachment(wMetric, margin);
      fdViewer.right = new FormAttachment(100, 0);
      fdViewer.bottom = new FormAttachment(100, 0);
      viewer.setLayoutData(fdViewer);
    } catch (Exception e) {
      Label error = new Label(composite, SWT.WRAP);
      PropsUi.setLook(error);
      error.setText(
          BaseMessages.getString(PKG, "PipelinePerformanceResultsTab.Error", e.getMessage()));
      FormData fdError = new FormData();
      fdError.left = new FormAttachment(0, 0);
      fdError.top = new FormAttachment(wMetric, margin);
      fdError.right = new FormAttachment(100, 0);
      error.setLayoutData(fdError);
    }

    tab.setControl(composite);
    refreshChart();
    return tab;
  }

  private void refreshChart() {
    if (viewer == null || viewer.isDisposed() || catalog == null) {
      return;
    }
    Metric metric = selectedMetric();
    IPipelineEngine<?> pipeline = pipelineGraph.getPipeline();
    List<RowMetaAndData> rows = PerformanceSnapshotRows.from(pipeline, metric);
    String fingerprint = metric.name() + ":" + PerformanceSnapshotRows.fingerprint(rows);
    if (fingerprint.equals(lastFingerprint)) {
      return;
    }
    lastFingerprint = fingerprint;
    HInMemoryRowsConnector connector = catalog.findInMemoryConnector();
    if (connector != null) {
      connector.setRows(rows);
    }
    viewer.reloadSurface();
  }

  private Metric selectedMetric() {
    int index = wMetric != null ? wMetric.getSelectionIndex() : 0;
    Metric[] values = Metric.values();
    if (index < 0 || index >= values.length) {
      return Metric.ROWS_PER_SECOND;
    }
    return values[index];
  }

  private static HGeneratedCatalog buildCatalog(List<RowMetaAndData> rows) throws Exception {
    HGeneratedCatalog catalog =
        HSimplePresentation.dashboard("pipeline-performance")
            .description("Live transform performance")
            .continuous()
            .designWidth(960)
            .trendTileHeight(140)
            .addTrend(
                BaseMessages.getString(PKG, "PipelinePerformanceResultsTab.Chart.Title"),
                rows,
                PerformanceSnapshotRows.COL_ELAPSED_S,
                PerformanceSnapshotRows.COL_TRANSFORM,
                PerformanceSnapshotRows.COL_VALUE)
            .build();
    applyChartStyle(catalog.findLineChartComponent());
    return catalog;
  }

  /** Integer Y labels, no X labels, legend on the right. */
  static void applyChartStyle(HLineChartComponent chart) {
    if (chart == null) {
      return;
    }
    chart.setShowingHorizontalLabels(false);
    chart.setUsingAngledHorizontalLabels(false);
    chart.setDotSize(3);
    chart.setShowingLegend(true);
    chart.setLegendPosition("RIGHT");
    if (chart.getFacts() != null && !chart.getFacts().isEmpty()) {
      chart.getFacts().get(0).setFormatMask("###,###,##0");
    }
  }

  private static String metricLabel(Metric metric) {
    return BaseMessages.getString(PKG, "PipelinePerformanceResultsTab.Metric." + metric.name());
  }
}
