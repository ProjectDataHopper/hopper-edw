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
package org.hopper.edw.datavault.hopgui.file.businessvault;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;

/**
 * Context actions for a Business Vault model.
 *
 * <p>These live on a plain {@code @GuiPlugin} (not the graph {@code Composite}) so {@code
 * GuiActionLambdaBuilder} can construct a plugin instance with a no-arg constructor. On Hop Web,
 * methods on the RAP widget class cannot be called: {@code getInstance()} returns null and method
 * lookup throws {@code NoSuchMethodException}.
 */
@GuiPlugin(description = "i18n::BusinessVaultContextGuiPlugin.Description")
public class BusinessVaultContextGuiPlugin {

  @GuiContextAction(
      id = "bv-graph-add-hub-reference",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddHubReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddHubReference.Tooltip",
      image = "datavault-hub.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void addHubReference(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addHubReference(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-satellite-reference",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddSatelliteReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddSatelliteReference.Tooltip",
      image = "datavault-satellite.svg",
      category = "Data Vault",
      categoryOrder = "2")
  public void addSatelliteReference(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addSatelliteReference(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-link-reference",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddLinkReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddLinkReference.Tooltip",
      image = "datavault-link.svg",
      category = "Data Vault",
      categoryOrder = "3")
  public void addLinkReference(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addLinkReference(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-delete-dv-reference",
      parentId = HopGuiBusinessVaultDvReferenceContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiBusinessVaultGraph.Context.DeleteDvReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.DeleteDvReference.Tooltip",
      image = "ui/images/delete.svg",
      category = "Data Vault",
      categoryOrder = "1")
  public void deleteDvReference(HopGuiBusinessVaultDvReferenceContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.deleteDvReference(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-bv-reference",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddBvReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddBvReference.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "0")
  public void addBvTableReference(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addBvTableReference(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-delete-bv-reference",
      parentId = HopGuiBusinessVaultBvReferenceContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiBusinessVaultGraph.Context.DeleteBvReference.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.DeleteBvReference.Tooltip",
      image = "ui/images/delete.svg",
      category = "Business Vault",
      categoryOrder = "1")
  public void deleteBvReference(HopGuiBusinessVaultBvReferenceContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.deleteBvReference(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-scd2",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddScd2.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddScd2.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "1")
  public void addScd2Table(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addScd2Table(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-pit",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddPit.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddPit.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "2")
  public void addPitTable(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addPitTable(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-business-table",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddBusinessTable.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddBusinessTable.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void addBusinessTable(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addBusinessTable(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-source-query",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddSourceQuery.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddSourceQuery.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void addSourceQuery(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addSourceQuery(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-identity-map",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddIdentityMap.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddIdentityMap.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void addIdentityMap(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addIdentityMap(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-source-calendar",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddSourceCalendar.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddSourceCalendar.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void addSourceCalendar(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addSourceCalendar(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-bridge",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddBridge.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddBridge.Tooltip",
      image = "business-vault-model.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void addBridgeTable(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addBridgeTable(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-import-dbt",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.ImportDbt.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.ImportDbt.Tooltip",
      image = "jinja-macro-library.svg",
      category = "Business Vault",
      categoryOrder = "5")
  public void importDbtModelsContext(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.importDbtModelsContext(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-add-note",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.AddNote.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.AddNote.Tooltip",
      image = "ui/images/note.svg",
      category = "Business Vault",
      categoryOrder = "4")
  public void addNote(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.addNote(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-edit-note",
      parentId = HopGuiBusinessVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.EditNote.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.EditNote.Tooltip",
      image = "ui/images/edit.svg",
      category = "Business Vault",
      categoryOrder = "1")
  public void editNoteAction(HopGuiBusinessVaultNoteContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.editNoteAction(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-delete-note",
      parentId = HopGuiBusinessVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiBusinessVaultGraph.DeleteNote.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.DeleteNote.Tooltip",
      image = "ui/images/delete.svg",
      category = "Business Vault",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiBusinessVaultNoteContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.deleteNoteAction(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-ai-help",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "10")
  public void openAiAdvisorContext(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorContext(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-table-ai-help",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "10")
  public void openAiAdvisorTableContext(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorTableContext(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-paste-clipboard",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Business Vault",
      categoryOrder = "5")
  public void pasteFromClipboard(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.pasteFromClipboard(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-paste-clipboard-table",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Business Vault",
      categoryOrder = "5")
  public void pasteFromClipboardOnTable(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.pasteFromClipboardOnTable(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-paste-clipboard-note",
      parentId = HopGuiBusinessVaultNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.PasteFromClipboard.Tooltip",
      image = "ui/images/paste.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void pasteFromClipboardOnNote(HopGuiBusinessVaultNoteContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.pasteFromClipboardOnNote(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-copy-update-action",
      parentId = HopGuiBusinessVaultContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.Context.CopyUpdateAction.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.CopyUpdateAction.Tooltip",
      image = "ui/images/copy.svg",
      category = "Workflow",
      categoryOrder = "9")
  public void copyUpdateActionToClipboard(HopGuiBusinessVaultContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.copyUpdateActionToClipboard(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-edit-table",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiBusinessVaultGraph.Context.EditTable.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.EditTable.Tooltip",
      image = "ui/images/edit.svg",
      category = "Business Vault",
      categoryOrder = "1")
  public void editBvTableAction(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.editBvTableAction(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-delete-table",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiBusinessVaultGraph.Context.DeleteTable.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.DeleteTable.Tooltip",
      image = "ui/images/delete.svg",
      category = "Business Vault",
      categoryOrder = "2")
  public void deleteBvTable(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.deleteBvTable(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-show-build-pipeline",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiBusinessVaultGraph.Context.ShowBuildPipeline.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.ShowBuildPipeline.Tooltip",
      image = "ui/images/debug.svg",
      category = "Business Vault",
      categoryOrder = "3")
  public void showBuildPipelineAction(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.showBuildPipelineAction(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-generate-scd2-calc-unit-test",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiBusinessVaultGraph.Context.GenerateCalcUnitTest.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.GenerateCalcUnitTest.Tooltip",
      image = "ui/images/debug.svg",
      category = "Business Vault",
      categoryOrder = "4")
  public void generateScd2CalculationUnitTestAction(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.generateScd2CalculationUnitTestAction(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-preview-target-table",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiBusinessVaultGraph.Context.PreviewTargetTable.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.PreviewTargetTable.Tooltip",
      image = "ui/images/preview.svg",
      category = "Business Vault",
      categoryOrder = "5")
  public void previewTargetTableAction(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.previewTargetTableAction(context);
    }
  }

  @GuiContextAction(
      id = "bv-graph-preview-target-layout",
      parentId = HopGuiBusinessVaultTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiBusinessVaultGraph.Context.PreviewTargetLayout.Name",
      tooltip = "i18n::HopGuiBusinessVaultGraph.Context.PreviewTargetLayout.Tooltip",
      image = "ui/images/preview.svg",
      category = "Business Vault",
      categoryOrder = "6")
  public void previewTargetLayoutAction(HopGuiBusinessVaultTableContext context) {
    HopGuiBusinessVaultGraph graph = graph(context);
    if (graph != null) {
      graph.previewTargetLayoutAction(context);
    }
  }

  private static HopGuiBusinessVaultGraph graph(HopGuiBusinessVaultContext context) {
    return context == null ? null : context.getBusinessVaultGraph();
  }

  private static HopGuiBusinessVaultGraph graph(HopGuiBusinessVaultDvReferenceContext context) {
    return context == null ? null : context.getBusinessVaultGraph();
  }

  private static HopGuiBusinessVaultGraph graph(HopGuiBusinessVaultBvReferenceContext context) {
    return context == null ? null : context.getBusinessVaultGraph();
  }

  private static HopGuiBusinessVaultGraph graph(HopGuiBusinessVaultNoteContext context) {
    return context == null ? null : context.getBusinessVaultGraph();
  }

  private static HopGuiBusinessVaultGraph graph(HopGuiBusinessVaultTableContext context) {
    return context == null ? null : context.getBusinessVaultGraph();
  }
}
