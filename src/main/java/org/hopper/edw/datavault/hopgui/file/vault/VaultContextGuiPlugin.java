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
package org.hopper.edw.datavault.hopgui.file.vault;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.action.GuiContextActionFilter;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;

/**
 * Context actions for a Data Vault model.
 *
 * <p>These live on a plain {@code @GuiPlugin} (not the graph {@code Composite}) so {@code
 * GuiActionLambdaBuilder} can construct a plugin instance with a no-arg constructor. On Hop Web,
 * methods on the RAP widget class cannot be called: {@code getInstance()} returns null and method
 * lookup throws {@code NoSuchMethodException}.
 */
@GuiPlugin(description = "i18n::VaultContextGuiPlugin.Description")
public class VaultContextGuiPlugin {

  @GuiContextAction(
      id = "vault-graph-add-hub",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Hub",
      tooltip = "Add a new Hub table at the click location",
      image = "datavault-hub.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void addHub(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addHub(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-satellite",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Satellite",
      tooltip = "Add a new Satellite table at the click location",
      image = "datavault-satellite.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void addSatellite(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addSatellite(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-link",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Link",
      tooltip = "Add a new Link table at the click location",
      image = "datavault-link.svg",
      category = "Data Vault",
      categoryOrder = "3")
  public void addLink(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addLink(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-reference",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "Add Reference table",
      tooltip = "Add a natural-key reference/code table (no hub hash key) at the click location",
      image = "datavault-reference.svg",
      keywords = {"reference", "ref", "code", "lookup", "catalog"},
      category = "Data Vault",
      categoryOrder = "4")
  public void addReferenceTable(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addReferenceTable(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-hub-reference",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.AddHubReference.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.AddHubReference.Tooltip",
      image = "datavault-hub.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void addHubReference(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addHubReference(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-hub-alias",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.AddHubAlias.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.AddHubAlias.Tooltip",
      image = "datavault-hub.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void addHubAlias(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addHubAlias(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-link-reference",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.AddLinkReference.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.AddLinkReference.Tooltip",
      image = "datavault-link.svg",
      category = "Data Vault",
      categoryOrder = "5")
  public void addLinkReference(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addLinkReference(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-satellite-reference",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.AddSatelliteReference.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.AddSatelliteReference.Tooltip",
      image = "datavault-satellite.svg",
      category = "Data Vault",
      categoryOrder = "6")
  public void addSatelliteReference(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addSatelliteReference(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-paste-clipboard",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiVaultGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Data Vault",
      categoryOrder = "4")
  public void pasteFromClipboard(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.pasteFromClipboard(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-copy-update-action",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.Context.CopyUpdateAction.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.CopyUpdateAction.Tooltip",
      image = "ui/images/copy.svg",
      category = "Workflow",
      categoryOrder = "1")
  public void copyUpdateActionToClipboard(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.copyUpdateActionToClipboard(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-compose-multi-table-source",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.ComposeMultiTableSource.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.ComposeMultiTableSource.Tooltip",
      image = "source-model.svg",
      category = "Data Vault",
      categoryOrder = "6")
  public void composeMultiTableSource(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.composeMultiTableSource(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-generate-from-source",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.Context.GenerateFromSource.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.GenerateFromSource.Tooltip",
      image = "source-model.svg",
      category = "Data Vault",
      categoryOrder = "6")
  public void generateFromSourceModel(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.generateFromSourceModel(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-add-note",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.AddNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.AddNote.Tooltip",
      image = "ui/images/note.svg",
      category = "Data Vault",
      categoryOrder = "5")
  public void addNote(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addNote(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-edit-model",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "Edit model properties",
      tooltip = "Edit the properties of this Data Vault model",
      image = "datavault-model.svg",
      category = "Data Vault",
      categoryOrder = "10")
  public void editModelProperties(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.editModelProperties(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-publish-dimensional-draft",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.PublishDimensional.Name",
      tooltip = "i18n::HopGuiVaultGraph.PublishDimensional.Tooltip",
      image = "dimensional-model.svg",
      category = "Export",
      categoryOrder = "2")
  public void publishDimensionalDraft(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.publishDimensionalDraft(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-import-record-definitions",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiVaultGraph.ImportSources.Name",
      tooltip = "i18n::HopGuiVaultGraph.ImportSources.Tooltip",
      image = "data-catalog.svg",
      category = "Import",
      categoryOrder = "1")
  public void importDatabaseSourceTables(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.importDatabaseSourceTables(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-ai-help",
      parentId = HopGuiVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiVaultGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorContext(HopGuiVaultContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorContext(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-table-ai-help",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiVaultGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorTableContext(HopGuiVaultTableContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorTableContext(context);
    }
  }

  @GuiContextAction(
      id = HopGuiVaultGraph.ACTION_ID_GO_TO_REFERENCED_TABLE,
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.Context.GoToReferencedTable.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.GoToReferencedTable.Tooltip",
      image = "ui/images/location.svg",
      category = "Data Vault",
      categoryOrder = "0")
  public void goToReferencedTableAction(HopGuiVaultTableContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.goToReferencedTableAction(context);
    }
  }

  @GuiContextActionFilter(parentId = HopGuiVaultTableContext.CONTEXT_ID)
  public boolean filterTableContextActions(
      String contextActionId, HopGuiVaultTableContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph == null) {
      return true;
    }
    return graph.filterTableContextActions(contextActionId, context);
  }

  @GuiContextAction(
      id = "vault-graph-edit-table",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "Edit",
      tooltip = "Edit the properties of this table",
      image = "ui/images/edit.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void editTableAction(HopGuiVaultTableContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.editTableAction(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-delete-table",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "Delete",
      tooltip = "Delete this table from the model",
      image = "ui/images/delete.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void deleteTable(HopGuiVaultTableContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.deleteTable(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-show-table-pipeline",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "Show update pipeline",
      tooltip = "Generate and show the pipeline used to update this table",
      image = "ui/images/debug.svg",
      category = "Data Vault",
      categoryOrder = "3")
  public void debugTablePipeline(HopGuiVaultTableContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.debugTablePipeline(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-preview-target-table",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiVaultGraph.Context.PreviewTargetTable.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.PreviewTargetTable.Tooltip",
      image = "ui/images/preview.svg",
      category = "Data Vault",
      categoryOrder = "5")
  public void previewTargetTableAction(HopGuiVaultTableContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.previewTargetTableAction(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-preview-target-layout",
      parentId = HopGuiVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiVaultGraph.Context.PreviewTargetLayout.Name",
      tooltip = "i18n::HopGuiVaultGraph.Context.PreviewTargetLayout.Tooltip",
      image = "ui/images/preview.svg",
      category = "Data Vault",
      categoryOrder = "6")
  public void previewTargetLayoutAction(HopGuiVaultTableContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.previewTargetLayoutAction(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-edit-note",
      parentId = HopGuiVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiVaultGraph.EditNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.EditNote.Tooltip",
      image = "ui/images/edit.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void editNoteAction(HopGuiVaultNoteContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.editNoteAction(context);
    }
  }

  @GuiContextAction(
      id = "vault-graph-delete-note",
      parentId = HopGuiVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiVaultGraph.DeleteNote.Name",
      tooltip = "i18n::HopGuiVaultGraph.DeleteNote.Tooltip",
      image = "ui/images/delete.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiVaultNoteContext context) {
    HopGuiVaultGraph graph = graph(context);
    if (graph != null) {
      graph.deleteNoteAction(context);
    }
  }

  private static HopGuiVaultGraph graph(HopGuiVaultContext context) {
    return context == null ? null : context.getVaultGraph();
  }

  private static HopGuiVaultGraph graph(HopGuiVaultTableContext context) {
    return context == null ? null : context.getVaultGraph();
  }

  private static HopGuiVaultGraph graph(HopGuiVaultNoteContext context) {
    return context == null ? null : context.getVaultGraph();
  }
}
