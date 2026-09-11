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
import org.apache.hop.core.gui.WorkflowTracker;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.tab.GuiTab;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.hopper.edw.datavault.hopgui.PresentationGuiPlugin;
import org.hopper.edw.datavault.presentation.WorkflowGanttTasks;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.GanttTask;
import org.hopper.presentation.component.types.chart.HGanttChartComponent;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.simple.HSimplePresentation;
import org.hopper.presentation.swt.HPresentationChrome;
import org.hopper.presentation.swt.HPresentationViewer;
import org.hopper.presentation.swt.HPresentationZoom;

/**
 * Workflow execution-results tab: action duration Gantt drawn by the Hopper presentation engine.
 */
@GuiPlugin
public class WorkflowGanttResultsTab {

  public static final Class<?> PKG = PresentationGuiPlugin.class;

  private final HopGui hopGui;
  private final HopGuiWorkflowGraph workflowGraph;
  private CTabItem tab;
  private HPresentationViewer viewer;
  private HGeneratedCatalog catalog;
  private String lastFingerprint;

  public WorkflowGanttResultsTab(HopGui hopGui, HopGuiWorkflowGraph workflowGraph) {
    this.hopGui = hopGui;
    this.workflowGraph = workflowGraph;
  }

  @GuiTab(
      id = "20000-workflow-graph-gantt-tab",
      parentId = HopGuiWorkflowGraph.WORKFLOW_GRAPH_TABS,
      description = "Gantt")
  public CTabItem addGanttTab(CTabFolder tabFolder) {
    tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setFont(GuiResource.getInstance().getFontDefault());
    tab.setImage(GuiResource.getInstance().getImageShowPerf());
    tab.setText(BaseMessages.getString(PKG, "WorkflowGanttResultsTab.Tab"));

    Composite composite = new Composite(tabFolder, SWT.NONE);
    composite.setLayout(new FormLayout());
    PropsUi.setLook(composite);

    try {
      PresentationGuiPlugin.ensureEnvironment();
      int rows = actionCount();
      catalog = buildCatalog(List.of(), rows);
      viewer =
          new HPresentationViewer(
              composite,
              new LoggingObject("workflow-gantt"),
              catalog.getProvider(),
              catalog.getPresentation(),
              HPresentationChrome.ZOOM,
              null);
      viewer.setFitMode(HPresentationZoom.WIDTH);
      viewer.setLiveRefresh(this::refreshChart);
      FormData fdViewer = new FormData();
      fdViewer.left = new FormAttachment(0, 0);
      fdViewer.top = new FormAttachment(0, 0);
      fdViewer.right = new FormAttachment(100, 0);
      fdViewer.bottom = new FormAttachment(100, 0);
      viewer.setLayoutData(fdViewer);
    } catch (Exception e) {
      Label error = new Label(composite, SWT.WRAP);
      PropsUi.setLook(error);
      error.setText(BaseMessages.getString(PKG, "WorkflowGanttResultsTab.Error", e.getMessage()));
      FormData fdError = new FormData();
      fdError.left = new FormAttachment(0, 0);
      fdError.top = new FormAttachment(0, 0);
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
    IWorkflowEngine<?> workflow = workflowGraph.getWorkflow();
    WorkflowTracker<?> tracker = workflow != null ? workflow.getWorkflowTracker() : null;
    List<GanttTask> tasks = WorkflowGanttTasks.from(tracker, System.currentTimeMillis());
    int rows = tasks.isEmpty() ? Math.max(1, actionCount()) : tasks.size();
    String fingerprint = rows + ":" + WorkflowGanttTasks.fingerprint(tasks);
    if (fingerprint.equals(lastFingerprint)) {
      return;
    }
    lastFingerprint = fingerprint;
    HGanttChartComponent gantt = catalog.findGanttComponent();
    if (gantt != null) {
      gantt.setInlineTasks(tasks);
      gantt.setEmbeddedTasks(null);
    }
    applyRowCount(rows);
    viewer.reloadSurface();
  }

  private int actionCount() {
    WorkflowMeta meta = workflowGraph.getWorkflowMeta();
    return meta != null ? Math.max(0, meta.nrActions()) : 0;
  }

  /** Page height follows the known action count (fixed row pitch), not the window size. */
  private void applyRowCount(int rows) {
    if (catalog == null || catalog.getPresentation() == null) {
      return;
    }
    if (catalog.getPresentation().getPages() == null
        || catalog.getPresentation().getPages().isEmpty()) {
      return;
    }
    int tileH = HSimplePresentation.ganttPixelHeight(rows);
    int pageH = tileH + 32;
    HPage page = catalog.getPresentation().getPages().get(0);
    page.setHeight(pageH);
    if (page.getComponents() != null && !page.getComponents().isEmpty()) {
      HComponent component = page.getComponents().get(0);
      component.setLayout(
          new HLayoutBuilder().left(16).right(-16).top(16).bottomFromTop(0, 16 + tileH).build());
    }
  }

  private static HGeneratedCatalog buildCatalog(List<GanttTask> tasks, int reservedRows)
      throws Exception {
    return HSimplePresentation.dashboard("workflow-gantt")
        .description("Action timings")
        .continuous()
        .designWidth(960)
        .addGantt(
            BaseMessages.getString(PKG, "WorkflowGanttResultsTab.Chart.Title"), tasks, reservedRows)
        .build();
  }
}
