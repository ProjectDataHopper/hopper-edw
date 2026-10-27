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
package org.hopper.edw.datavault.hopgui.file.executionmap;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.action.GuiContextActionFilter;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;

/**
 * Context actions for an execution map.
 *
 * <p>These live on a plain {@code @GuiPlugin} (not the graph {@code Composite}) so {@code
 * GuiActionLambdaBuilder} can construct a plugin instance with a no-arg constructor. On Hop Web,
 * methods on the RAP widget class cannot be called: {@code getInstance()} returns null and method
 * lookup throws {@code NoSuchMethodException}.
 */
@GuiPlugin(description = "i18n::ExecutionMapContextGuiPlugin.Description")
public class ExecutionMapContextGuiPlugin {

  @GuiContextActionFilter(parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID)
  public boolean filterNodeContextActions(
      String contextActionId, HopGuiExecutionMapNodeContext context) {
    HopGuiExecutionMapGraph graph = graph(context);
    if (graph == null) {
      return true;
    }
    return graph.filterNodeContextActions(contextActionId, context);
  }

  @GuiContextAction(
      id = HopGuiExecutionMapGraph.ACTION_ID_OPEN_ARTIFACT,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.OpenArtifact.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.OpenArtifact.Tooltip",
      image = "ui/images/open.svg",
      category = "Execution map",
      categoryOrder = "1")
  public void openArtifactFromContext(HopGuiExecutionMapNodeContext context) {
    HopGuiExecutionMapGraph graph = graph(context);
    if (graph != null) {
      graph.openArtifactFromContext(context);
    }
  }

  @GuiContextAction(
      id = HopGuiExecutionMapGraph.ACTION_ID_VIEW_SNAPSHOT,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.ViewSnapshot.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.ViewSnapshot.Tooltip",
      image = "ui/images/view.svg",
      category = "Execution map",
      categoryOrder = "2")
  public void viewSnapshotFromContext(HopGuiExecutionMapNodeContext context) {
    HopGuiExecutionMapGraph graph = graph(context);
    if (graph != null) {
      graph.viewSnapshotFromContext(context);
    }
  }

  @GuiContextAction(
      id = HopGuiExecutionMapGraph.ACTION_ID_OPEN_IN_CATALOG,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.OpenInCatalog.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.OpenInCatalog.Tooltip",
      image = "data-catalog.svg",
      category = "Execution map",
      categoryOrder = "3")
  public void openInCatalogFromContext(HopGuiExecutionMapNodeContext context) {
    HopGuiExecutionMapGraph graph = graph(context);
    if (graph != null) {
      graph.openInCatalogFromContext(context);
    }
  }

  @GuiContextAction(
      id = HopGuiExecutionMapGraph.ACTION_ID_PREVIEW_DATASET,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.PreviewDataset.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.PreviewDataset.Tooltip",
      image = "ui/images/preview.svg",
      category = "Execution map",
      categoryOrder = "4")
  public void previewDatasetFromContext(HopGuiExecutionMapNodeContext context) {
    HopGuiExecutionMapGraph graph = graph(context);
    if (graph != null) {
      graph.previewDatasetFromContext(context);
    }
  }

  @GuiContextAction(
      id = HopGuiExecutionMapGraph.ACTION_ID_VIEW_DATASET,
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.Context.ViewDataset.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.Context.ViewDataset.Tooltip",
      image = "ui/images/view.svg",
      category = "Execution map",
      categoryOrder = "5")
  public void viewDatasetFromContext(HopGuiExecutionMapNodeContext context) {
    HopGuiExecutionMapGraph graph = graph(context);
    if (graph != null) {
      graph.viewDatasetFromContext(context);
    }
  }

  @GuiContextAction(
      id = "execution-map-ai-help",
      parentId = HopGuiExecutionMapNodeContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiExecutionMapGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiExecutionMapGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorNodeContext(HopGuiExecutionMapNodeContext context) {
    HopGuiExecutionMapGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorNodeContext(context);
    }
  }

  private static HopGuiExecutionMapGraph graph(HopGuiExecutionMapNodeContext context) {
    return context == null ? null : context.getExecutionMapGraph();
  }
}
