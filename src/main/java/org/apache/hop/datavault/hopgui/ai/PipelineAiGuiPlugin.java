/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.hopgui.ai;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.apache.hop.ui.hopgui.file.pipeline.context.HopGuiPipelineContext;
import org.apache.hop.ui.hopgui.file.pipeline.context.HopGuiPipelineTransformContext;

/** Toolbar and context-menu entry points for pipeline AI advisory. */
@GuiPlugin(description = "i18n::PipelineAiGuiPlugin.Description")
public class PipelineAiGuiPlugin {

  public static final String TOOLBAR_ITEM_AI_HELP = "HopGuiPipelineGraph-ToolBar-10046-ai-help";

  @GuiToolbarElement(
      root = HopGuiPipelineGraph.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_AI_HELP,
      toolTip = "i18n::PipelineAiGuiPlugin.Toolbar.AiHelp.Tooltip",
      image = "datavault-ai-help.svg")
  public void openAiAdvisorToolbar() {
    HopGui hopGui = HopGui.getInstance();
    HopGuiPipelineGraph pipelineGraph = HopGui.getActivePipelineGraph();
    if (pipelineGraph == null) {
      return;
    }
    openAiAdvisor(pipelineGraph, null);
  }

  @GuiContextAction(
      id = "pipeline-graph-zzz-ai-help",
      parentId = HopGuiPipelineContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::PipelineAiGuiPlugin.AiHelp.Name",
      tooltip = "i18n::PipelineAiGuiPlugin.AiHelp.Tooltip",
      image = "datavault-ai-help.svg",
      category =
          "i18n:org.apache.hop.ui.hopgui.file.pipeline:HopGuiPipelineGraph.ContextualAction.Category.Basic.Text",
      categoryOrder = "1")
  public void openAiAdvisorPipelineContext(HopGuiPipelineContext context) {
    if (context.getPipelineGraph() != null) {
      openAiAdvisor(context.getPipelineGraph(), null);
    }
  }

  @GuiContextAction(
      id = "pipeline-graph-transform-zzz-ai-help",
      parentId = HopGuiPipelineTransformContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::PipelineAiGuiPlugin.AiHelp.Name",
      tooltip = "i18n::PipelineAiGuiPlugin.AiHelp.Tooltip",
      image = "datavault-ai-help.svg",
      category =
          "i18n:org.apache.hop.ui.hopgui.file.pipeline:HopGuiPipelineGraph.ContextualAction.Category.Basic.Text",
      categoryOrder = "1")
  public void openAiAdvisorTransformContext(HopGuiPipelineTransformContext context) {
    if (context.getPipelineGraph() != null) {
      String focus =
          context.getTransformMeta() != null ? context.getTransformMeta().getName() : null;
      openAiAdvisor(context.getPipelineGraph(), focus);
    }
  }

  private static void openAiAdvisor(HopGuiPipelineGraph pipelineGraph, String focusTransformName) {
    PipelineMeta pipelineMeta = pipelineGraph.getPipelineMeta();
    if (pipelineMeta == null) {
      return;
    }
    HopGui hopGui = pipelineGraph.getHopGui();
    HopAiGuiPluginSupport.runAdvisorAction(
        hopGui.getShell(),
        () ->
            PipelineAiAdvisorDialog.open(
                hopGui,
                pipelineGraph,
                pipelineMeta,
                pipelineGraph.getVariables(),
                () -> HopAiLogTextSupport.readPipelineLog(pipelineGraph),
                focusTransformName));
  }
}
