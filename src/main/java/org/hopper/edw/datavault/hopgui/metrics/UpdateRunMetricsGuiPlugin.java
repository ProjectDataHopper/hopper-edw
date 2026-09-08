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
package org.hopper.edw.datavault.hopgui.metrics;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.action.GuiContextActionFilter;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;
import org.apache.hop.ui.hopgui.file.workflow.context.HopGuiWorkflowActionContext;
import org.apache.hop.workflow.action.ActionMeta;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveRegistry;
import org.hopper.edw.datavault.metrics.live.UpdateRunWavePhase;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshot;

/** Workflow canvas context menu for resource-group load metrics. */
@GuiPlugin(description = "Resource group update load metrics")
public class UpdateRunMetricsGuiPlugin {

  public static final String ACTION_VIEW_METRICS = "workflow-graph-action-view-load-metrics";

  @GuiContextAction(
      id = ACTION_VIEW_METRICS,
      parentId = HopGuiWorkflowActionContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::UpdateRunMetricsGuiPlugin.ViewMetrics.Name",
      tooltip = "i18n::UpdateRunMetricsGuiPlugin.ViewMetrics.Tooltip",
      image = "execution-metrics-profile.svg",
      category =
          "i18n:org.apache.hop.ui.hopgui.file.workflow:HopGuiWorkflowGraph.ContextualAction.Category.Basic.Text",
      categoryOrder = "1")
  public void viewLoadMetrics(HopGuiWorkflowActionContext context) {
    if (context == null || context.getWorkflowGraph() == null || context.getActionMeta() == null) {
      return;
    }
    HopGuiWorkflowGraph graph = context.getWorkflowGraph();
    ActionMeta actionMeta = context.getActionMeta();
    String workflowFilename =
        context.getWorkflowMeta() != null
            ? (Utils.isEmpty(context.getWorkflowMeta().getFilename())
                ? context.getWorkflowMeta().getName()
                : context.getWorkflowMeta().getFilename())
            : null;
    UpdateRunWaveSnapshot wave =
        UpdateRunLiveRegistry.findWaveByWorkflowAction(workflowFilename, actionMeta.getName())
            .orElse(null);
    boolean historyPreferred = wave == null || wave.getPhase() == UpdateRunWavePhase.FINISHED;
    ResourceGroupUpdateMetricsDialog.open(
        graph.getHopGui().getShell(),
        graph.getVariables(),
        graph.getHopGui().getMetadataProvider(),
        wave != null ? wave.getWaveId() : null,
        workflowFilename,
        actionMeta.getName(),
        historyPreferred);
  }

  @GuiContextActionFilter(parentId = HopGuiWorkflowActionContext.CONTEXT_ID)
  public boolean filterAction(String contextActionId, HopGuiWorkflowActionContext context) {
    if (!ACTION_VIEW_METRICS.equals(contextActionId)) {
      return true;
    }
    return context != null
        && UpdateRunLiveWorkflowPaintSupport.isGroupUpdateAction(context.getActionMeta());
  }
}
