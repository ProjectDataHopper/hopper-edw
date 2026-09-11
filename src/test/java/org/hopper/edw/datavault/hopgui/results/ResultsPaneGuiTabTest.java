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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.tab.GuiTab;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HFact;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.simple.HSimplePresentation;
import org.hopper.presentation.swt.HPresentationZoom;
import org.junit.jupiter.api.Test;

class ResultsPaneGuiTabTest {

  @Test
  void performanceTabHook() throws Exception {
    assertEquals(
        GuiPlugin.class,
        PipelinePerformanceResultsTab.class.getAnnotation(GuiPlugin.class).annotationType());
    Constructor<PipelinePerformanceResultsTab> ctor =
        PipelinePerformanceResultsTab.class.getConstructor(HopGui.class, HopGuiPipelineGraph.class);
    assertEquals(2, ctor.getParameterCount());
    Method method =
        PipelinePerformanceResultsTab.class.getMethod("addPerformanceTab", CTabFolder.class);
    GuiTab tab = method.getAnnotation(GuiTab.class);
    assertEquals(HopGuiPipelineGraph.PIPELINE_GRAPH_TABS, tab.parentId());
    assertEquals(CTabItem.class, method.getReturnType());
  }

  @Test
  void performanceChartUsesIntegerAxisAndAngledLabels() {
    HLineChartComponent chart = new HLineChartComponent("trend");
    chart.setFacts(
        List.of(
            new HFact(
                "value",
                "Value",
                HHorizontalAlignment.RIGHT,
                HVerticalAlignment.MIDDLE,
                AggregationMethod.SUM,
                null)));
    chart.setShowingLegend(true);
    PipelinePerformanceResultsTab.applyChartStyle(chart);
    assertFalse(chart.isShowingHorizontalLabels());
    assertFalse(chart.isUsingAngledHorizontalLabels());
    assertEquals(3, chart.getDotSize());
    assertTrue(chart.isShowingLegend());
    assertEquals("RIGHT", chart.getLegendPosition());
    assertEquals("###,###,##0", chart.getFacts().get(0).getFormatMask());
  }

  @Test
  void ganttPageHeightFillsViewportUnderFitWidth() {
    int minPageH = HSimplePresentation.ganttPixelHeight(4) + 32;
    int pageH = WorkflowGanttResultsTab.pageHeightForViewport(960, minPageH, 1208, 408);
    assertTrue(pageH >= minPageH);
    float zoom = HPresentationZoom.compute(HPresentationZoom.WIDTH, 1208, 408, 960, pageH, 1f);
    assertEquals(408 - HPresentationZoom.MARGIN, Math.round(zoom * pageH));
  }

  @Test
  void ganttTabHook() throws Exception {
    Constructor<WorkflowGanttResultsTab> ctor =
        WorkflowGanttResultsTab.class.getConstructor(HopGui.class, HopGuiWorkflowGraph.class);
    assertEquals(2, ctor.getParameterCount());
    Method method = WorkflowGanttResultsTab.class.getMethod("addGanttTab", CTabFolder.class);
    GuiTab tab = method.getAnnotation(GuiTab.class);
    assertEquals(HopGuiWorkflowGraph.WORKFLOW_GRAPH_TABS, tab.parentId());
    assertEquals(CTabItem.class, method.getReturnType());
  }
}
