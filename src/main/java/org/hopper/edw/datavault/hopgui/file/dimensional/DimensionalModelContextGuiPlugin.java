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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.action.GuiContextActionFilter;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;

/**
 * Context actions for a dimensional model.
 *
 * <p>These live on a plain {@code @GuiPlugin} (not the graph {@code Composite}) so {@code
 * GuiActionLambdaBuilder} can construct a plugin instance with a no-arg constructor. On Hop Web,
 * methods on the RAP widget class cannot be called: {@code getInstance()} returns null and method
 * lookup throws {@code NoSuchMethodException}.
 */
@GuiPlugin(description = "i18n::DimensionalModelContextGuiPlugin.Description")
public class DimensionalModelContextGuiPlugin {

  @GuiContextAction(
      id = "dm-graph-ai-help",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "2")
  public void openAiAdvisorContext(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorContext(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-table-ai-help",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "2")
  public void openAiAdvisorTableContext(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorTableContext(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-import-database-tables",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.ImportDatabaseTables.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.ImportDatabaseTables.Tooltip",
      image = "ui/images/database.svg",
      category = "Import",
      categoryOrder = "1")
  public void importDatabaseTables(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.importDatabaseTables(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddDimension.Tooltip",
      image = "dimension.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void addDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addDimension(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-dimension-alias",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddDimensionAlias.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddDimensionAlias.Tooltip",
      image = "dimension-alias.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void addDimensionAlias(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addDimensionAlias(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-date-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddDateDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddDateDimension.Tooltip",
      image = "dimension.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void addDateDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addDateDimension(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-fact",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddFact.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddFact.Tooltip",
      image = "fact.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void addFact(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addFact(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-junk-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddJunkDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddJunkDimension.Tooltip",
      image = "dimension-junk.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void addJunkDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addJunkDimension(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-range-dimension",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddRangeDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddRangeDimension.Tooltip",
      image = "dimension-range.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void addRangeDimension(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addRangeDimension(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-factless-fact",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddFactlessFact.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddFactlessFact.Tooltip",
      image = "fact-factless.svg",
      category = "Dimensional",
      categoryOrder = "4")
  public void addFactlessFact(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addFactlessFact(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-bridge",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddBridge.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddBridge.Tooltip",
      image = "bridge.svg",
      category = "Dimensional",
      categoryOrder = "5")
  public void addBridge(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addBridge(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-add-note",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiDimensionalModelGraph.Context.AddNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.AddNote.Tooltip",
      image = "ui/images/note.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void addNote(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.addNote(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-paste-clipboard",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Dimensional",
      categoryOrder = "6")
  public void pasteFromClipboard(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.pasteFromClipboard(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-paste-clipboard-table",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Dimensional",
      categoryOrder = "6")
  public void pasteFromClipboardOnTable(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.pasteFromClipboardOnTable(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-paste-clipboard-note",
      parentId = HopGuiDimensionalNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void pasteFromClipboardOnNote(HopGuiDimensionalNoteContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.pasteFromClipboardOnNote(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-copy-update-action",
      parentId = HopGuiDimensionalContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.Context.CopyUpdateAction.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.CopyUpdateAction.Tooltip",
      image = "ui/images/copy.svg",
      category = "Workflow",
      categoryOrder = "1")
  public void copyUpdateActionToClipboard(HopGuiDimensionalContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.copyUpdateActionToClipboard(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-edit-note",
      parentId = HopGuiDimensionalNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.EditNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.EditNote.Tooltip",
      image = "ui/images/edit.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void editNoteAction(HopGuiDimensionalNoteContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.editNoteAction(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-delete-note",
      parentId = HopGuiDimensionalNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiDimensionalModelGraph.DeleteNote.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.DeleteNote.Tooltip",
      image = "ui/images/delete.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiDimensionalNoteContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.deleteNoteAction(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-edit-table",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.Context.EditTable.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.EditTable.Tooltip",
      image = "ui/images/edit.svg",
      category = "Dimensional",
      categoryOrder = "1")
  public void editTableAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.editTableAction(context);
    }
  }

  @GuiContextAction(
      id = HopGuiDimensionalModelGraph.ACTION_ID_CREATE_PRESENTATION,
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiDimensionalModelGraph.Context.CreatePresentation.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.CreatePresentation.Tooltip",
      image = "fact.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void createFactPresentationAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.createFactPresentationAction(context);
    }
  }

  @GuiContextAction(
      id = HopGuiDimensionalModelGraph.ACTION_ID_OPEN_SOURCE_PIPELINE,
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.Context.OpenSourcePipeline.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.OpenSourcePipeline.Tooltip",
      image = "ui/images/pipeline.svg",
      category = "Dimensional",
      categoryOrder = "2")
  public void openSourcePipelineAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.openSourcePipelineAction(context);
    }
  }

  @GuiContextAction(
      id = HopGuiDimensionalModelGraph.ACTION_ID_GO_TO_ALIASED_DIMENSION,
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiDimensionalModelGraph.Context.GoToAliasedDimension.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.GoToAliasedDimension.Tooltip",
      image = "ui/images/location.svg",
      category = "Dimensional",
      categoryOrder = "3")
  public void goToAliasedDimensionAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.goToAliasedDimensionAction(context);
    }
  }

  @GuiContextActionFilter(parentId = HopGuiDimensionalTableContext.CONTEXT_ID)
  public boolean filterTableContextActions(
      String contextActionId, HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph == null) {
      return true;
    }
    return graph.filterTableContextActions(contextActionId, context);
  }

  @GuiContextAction(
      id = "dm-graph-delete-table",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiDimensionalModelGraph.Context.DeleteTable.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.DeleteTable.Tooltip",
      image = "ui/images/delete.svg",
      category = "Dimensional",
      categoryOrder = "4")
  public void deleteTable(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.deleteTable(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-show-update-pipeline",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiDimensionalModelGraph.Context.ShowUpdatePipeline.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.ShowUpdatePipeline.Tooltip",
      image = "ui/images/debug.svg",
      category = "Dimensional",
      categoryOrder = "5")
  public void showUpdatePipelineAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.showUpdatePipelineAction(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-preview-masked-rows",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiDimensionalModelGraph.Context.PreviewMaskedRows.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.PreviewMaskedRows.Tooltip",
      image = "ui/images/preview.svg",
      category = "Dimensional",
      categoryOrder = "7")
  public void previewMaskedRowsAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.previewMaskedRowsAction(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-preview-target-table",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiDimensionalModelGraph.Context.PreviewTargetTable.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.PreviewTargetTable.Tooltip",
      image = "ui/images/preview.svg",
      category = "Dimensional",
      categoryOrder = "7")
  public void previewTargetTableAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.previewTargetTableAction(context);
    }
  }

  @GuiContextAction(
      id = "dm-graph-preview-target-layout",
      parentId = HopGuiDimensionalTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiDimensionalModelGraph.Context.PreviewTargetLayout.Name",
      tooltip = "i18n::HopGuiDimensionalModelGraph.Context.PreviewTargetLayout.Tooltip",
      image = "ui/images/preview.svg",
      category = "Dimensional",
      categoryOrder = "8")
  public void previewTargetLayoutAction(HopGuiDimensionalTableContext context) {
    HopGuiDimensionalModelGraph graph = graph(context);
    if (graph != null) {
      graph.previewTargetLayoutAction(context);
    }
  }

  private static HopGuiDimensionalModelGraph graph(HopGuiDimensionalContext context) {
    return context == null ? null : context.getDimensionalGraph();
  }

  private static HopGuiDimensionalModelGraph graph(HopGuiDimensionalTableContext context) {
    return context == null ? null : context.getDimensionalGraph();
  }

  private static HopGuiDimensionalModelGraph graph(HopGuiDimensionalNoteContext context) {
    return context == null ? null : context.getDimensionalGraph();
  }
}
