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
package org.hopper.edw.datavault.layout;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;

/** GUI plugin that applies ELK layout to the active workflow graph. */
@GuiPlugin
public class WorkflowElkLayout {

  protected static final Class<?> PKG = WorkflowElkLayout.class;
  private static final String KEY_PREFIX = "WorkflowElkLayout";

  public static final String ID_TOOLBAR_ITEM_ELK_LAYOUT =
      "HopGuiWorkflowGraph-ToolBar-10045-elk-layout";

  @GuiToolbarElement(
      root = HopGuiWorkflowGraph.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ELK_LAYOUT,
      toolTip = "i18n::WorkflowElkLayout.Toolbar.Layout.Tooltip",
      image = "elk-layout.svg")
  public void layoutActiveWorkflow() {
    HopGui hopGui = HopGui.getInstance();
    HopGuiWorkflowGraph workflowGraph = HopGui.getActiveWorkflowGraph();
    if (workflowGraph == null) {
      return;
    }

    WorkflowMeta workflowMeta = workflowGraph.getWorkflowMeta();
    if (workflowMeta == null) {
      return;
    }

    List<ActionMeta> actions = workflowMeta.getActions();
    if (actions == null || actions.isEmpty()) {
      return;
    }

    try {
      ElkLayout layout = ElkLayoutGuiSupport.promptForLayout(hopGui.getShell());
      if (layout == null) {
        return;
      }
      ActionMeta[] actionArray = actions.toArray(new ActionMeta[0]);
      int[] indexes = workflowMeta.getActionIndexes(actions);
      Point[] previousLocations = ElkLayoutGuiSupport.captureLocations(actions);

      ElkGraphLayout.fromWorkflow(workflowMeta).layout(layout);

      Point[] currentLocations = ElkLayoutGuiSupport.captureLocations(actions);
      workflowGraph.addUndoPosition(actionArray, indexes, previousLocations, currentLocations);

      workflowGraph.setChanged();
      workflowGraph.updateGui();
    } catch (HopException e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    } catch (Exception e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    }
  }
}
