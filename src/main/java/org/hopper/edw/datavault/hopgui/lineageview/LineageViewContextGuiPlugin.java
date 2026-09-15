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
package org.hopper.edw.datavault.hopgui.lineageview;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.action.GuiContextActionFilter;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewGraph;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewNodeContext;

/**
 * Node context actions for a Hop Lineage View.
 *
 * <p>These live on a plain {@code @GuiPlugin} (not the graph {@code Composite}) so {@code
 * GuiActionLambdaBuilder} can construct a plugin instance with a no-arg constructor. On Hop Web,
 * looking up extra methods on the RAP widget class fails with {@code NoSuchMethodException}.
 */
@GuiPlugin(description = "i18n::LineageViewContextGuiPlugin.Description")
public class LineageViewContextGuiPlugin {

  public static final String ACTION_ID_AI_HELP = "lineage-view-ai-help";

  @GuiContextActionFilter(parentId = HopGuiLineageViewNodeContext.CONTEXT_ID)
  public boolean filterNodeContextActions(
      String contextActionId, HopGuiLineageViewNodeContext context) {
    HopGuiLineageViewGraph graph = graph(context);
    if (graph == null) {
      return true;
    }
    return graph.filterNodeContextActions(contextActionId, context);
  }

  @GuiContextAction(
      id = HopGuiLineageViewGraph.ACTION_ID_OPEN_MODEL,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.Context.OpenModel.Name",
      tooltip =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.Context.OpenModel.Tooltip",
      image = "ui/images/open.svg",
      category = "Lineage",
      categoryOrder = "1")
  public void openModelFromContext(HopGuiLineageViewNodeContext context) {
    HopGuiLineageViewGraph graph = graph(context);
    if (graph != null) {
      graph.openModelFromContext(context);
    }
  }

  @GuiContextAction(
      id = HopGuiLineageViewGraph.ACTION_ID_OPEN_CATALOG,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.Context.OpenCatalog.Name",
      tooltip =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.Context.OpenCatalog.Tooltip",
      image = "data-catalog.svg",
      category = "Lineage",
      categoryOrder = "2")
  public void openCatalogFromContext(HopGuiLineageViewNodeContext context) {
    HopGuiLineageViewGraph graph = graph(context);
    if (graph != null) {
      graph.openCatalogFromContext(context);
    }
  }

  @GuiContextAction(
      id = HopGuiLineageViewGraph.ACTION_ID_SHOW_UPDATE_PIPELINE,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.Context.ShowUpdatePipeline.Name",
      tooltip =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.Context.ShowUpdatePipeline.Tooltip",
      image = "ui/images/pipeline.svg",
      category = "Lineage",
      categoryOrder = "3")
  public void showUpdatePipelineFromContext(HopGuiLineageViewNodeContext context) {
    HopGuiLineageViewGraph graph = graph(context);
    if (graph != null) {
      graph.showUpdatePipelineFromContext(context);
    }
  }

  @GuiContextAction(
      id = HopGuiLineageViewGraph.ACTION_ID_SHOW_BUILD_PIPELINE,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.Context.ShowBuildPipeline.Name",
      tooltip =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.Context.ShowBuildPipeline.Tooltip",
      image = "ui/images/pipeline.svg",
      category = "Lineage",
      categoryOrder = "4")
  public void showBuildPipelineFromContext(HopGuiLineageViewNodeContext context) {
    HopGuiLineageViewGraph graph = graph(context);
    if (graph != null) {
      graph.showBuildPipelineFromContext(context);
    }
  }

  @GuiContextAction(
      id = ACTION_ID_AI_HELP,
      parentId = HopGuiLineageViewNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.AiHelp.Name",
      tooltip =
          "i18n:org.hopper.edw.datavault.hopgui.file.lineageview:HopGuiLineageViewGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorNodeContext(HopGuiLineageViewNodeContext context) {
    HopGuiLineageViewGraph graph = graph(context);
    if (graph != null) {
      String focus = context.getNode() != null ? context.getNode().getName() : null;
      graph.openAiAdvisor(focus);
    }
  }

  private static HopGuiLineageViewGraph graph(HopGuiLineageViewNodeContext context) {
    return context != null ? context.getLineageViewGraph() : null;
  }
}
