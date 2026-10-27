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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;

/**
 * Context actions for a source model.
 *
 * <p>These live on a plain {@code @GuiPlugin} (not the graph {@code Composite}) so {@code
 * GuiActionLambdaBuilder} can construct a plugin instance with a no-arg constructor. On Hop Web,
 * methods on the RAP widget class cannot be called: {@code getInstance()} returns null and method
 * lookup throws {@code NoSuchMethodException}.
 */
@GuiPlugin(description = "i18n::SourceModelContextGuiPlugin.Description")
public class SourceModelContextGuiPlugin {

  @GuiContextAction(
      id = "source-model-graph-add-table",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddTable.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddTable.Tooltip",
      image = "source-model.svg",
      category = "Basic",
      categoryOrder = "1")
  public void addTable(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.addTable(context);
    }
  }

  @GuiContextAction(
      id = "source-model-graph-import-schema",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.ImportSchema.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.ImportSchema.Tooltip",
      image = "ui/images/schema.svg",
      category = "Basic",
      categoryOrder = "2")
  public void importSchemaFromContext(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.importSchemaFromContext(context);
    }
  }

  @GuiContextAction(
      id = "source-model-graph-ai-help",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorContext(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorContext(context);
    }
  }

  @GuiContextAction(
      id = "source-model-table-ai-help",
      parentId = HopGuiSourceTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorTableContext(HopGuiSourceTableContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorTableContext(context);
    }
  }

  @GuiContextAction(
      id = "source-model-table-browse",
      parentId = HopGuiSourceTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Browse.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Browse.Tooltip",
      image = "ui/images/view.svg",
      category = "Basic",
      categoryOrder = "0")
  public void browseFromTable(HopGuiSourceTableContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.browseFromTable(context);
    }
  }

  @GuiContextAction(
      id = "source-model-query-browse",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Browse.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Browse.Tooltip",
      image = "ui/images/view.svg",
      category = "Basic",
      categoryOrder = "0")
  public void browseFromQuery(HopGuiSourceQueryContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.browseFromQuery(context);
    }
  }

  @GuiContextAction(
      id = "source-model-json-browse",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Browse.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Browse.Tooltip",
      image = "ui/images/view.svg",
      category = "Basic",
      categoryOrder = "0")
  public void browseFromJson(HopGuiSourceJsonContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.browseFromJson(context);
    }
  }

  @GuiContextAction(
      id = "source-model-pipeline-browse",
      parentId = HopGuiSourcePipelineContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Browse.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Browse.Tooltip",
      image = "ui/images/view.svg",
      category = "Basic",
      categoryOrder = "0")
  public void browseFromPipeline(HopGuiSourcePipelineContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.browseFromPipeline(context);
    }
  }

  @GuiContextAction(
      id = "source-model-masking-browse",
      parentId = HopGuiSourceMaskingContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Browse.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Browse.Tooltip",
      image = "ui/images/view.svg",
      category = "Basic",
      categoryOrder = "0")
  public void browseFromMasking(HopGuiSourceMaskingContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.browseFromMasking(context);
    }
  }

  @GuiContextAction(
      id = "source-model-query-ai-help",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorQueryContext(HopGuiSourceQueryContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorQueryContext(context);
    }
  }

  @GuiContextAction(
      id = "source-model-json-ai-help",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorJsonContext(HopGuiSourceJsonContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorJsonContext(context);
    }
  }

  @GuiContextAction(
      id = "source-model-pipeline-ai-help",
      parentId = HopGuiSourcePipelineContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorPipelineContext(HopGuiSourcePipelineContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorPipelineContext(context);
    }
  }

  @GuiContextAction(
      id = "source-model-relationship-ai-help",
      parentId = HopGuiSourceRelationshipContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.AiHelp.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.AiHelp.Tooltip",
      image = "ai-provider.svg",
      category = "Help",
      categoryOrder = "1")
  public void openAiAdvisorRelationshipContext(HopGuiSourceRelationshipContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.openAiAdvisorRelationshipContext(context);
    }
  }

  @GuiContextAction(
      id = "source-model-graph-generate-vault",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.GenerateVault.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.GenerateVault.Tooltip",
      image = "datavault-model.svg",
      category = "Basic",
      categoryOrder = "3")
  public void generateDataVaultFromContext(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.generateDataVaultFromContext(context);
    }
  }

  @GuiContextAction(
      id = "source-model-graph-add-query",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddQuery.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddQuery.Tooltip",
      image = "source-model.svg",
      category = "Basic",
      categoryOrder = "4")
  public void addQuery(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.addQuery(context);
    }
  }

  @GuiContextAction(
      id = "source-model-graph-add-json",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddJson.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddJson.Tooltip",
      image = "source-model.svg",
      category = "Basic",
      categoryOrder = "5")
  public void addJson(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.addJson(context);
    }
  }

  @GuiContextAction(
      id = "source-model-graph-add-masking",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddMasking.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddMasking.Tooltip",
      image = "source-model.svg",
      category = "Basic",
      categoryOrder = "5")
  public void addMasking(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.addMasking(context);
    }
  }

  @GuiContextAction(
      id = "source-model-graph-add-pipeline",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddPipeline.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddPipeline.Tooltip",
      image = "source-model.svg",
      category = "Basic",
      categoryOrder = "6")
  public void addPipeline(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.addPipeline(context);
    }
  }

  @GuiContextAction(
      id = "source-model-pipeline-edit",
      parentId = HopGuiSourcePipelineContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Pipeline.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Pipeline.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editPipelineAction(HopGuiSourcePipelineContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.editPipelineAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-pipeline-open",
      parentId = HopGuiSourcePipelineContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiSourceModelGraph.Context.Pipeline.Open.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Pipeline.Open.Tooltip",
      image = "ui/images/open.svg",
      category = "Basic",
      categoryOrder = "2")
  public void openPipelineAction(HopGuiSourcePipelineContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.openPipelineAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-pipeline-publish",
      parentId = HopGuiSourcePipelineContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.Pipeline.Publish.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Pipeline.Publish.Tooltip",
      image = "ui/images/publish.svg",
      category = "Basic",
      categoryOrder = "3")
  public void publishPipelineAction(HopGuiSourcePipelineContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.publishPipelineAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-json-edit",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Json.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Json.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editJsonAction(HopGuiSourceJsonContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.editJsonAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-masking-edit",
      parentId = HopGuiSourceMaskingContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Masking.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Masking.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editMaskingAction(HopGuiSourceMaskingContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.editMaskingAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-masking-preview",
      parentId = HopGuiSourceMaskingContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiSourceModelGraph.Context.Masking.Preview.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Masking.Preview.Tooltip",
      image = "ui/images/preview.svg",
      category = "Basic",
      categoryOrder = "2")
  public void previewMaskingAction(HopGuiSourceMaskingContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.previewMaskingAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-masking-publish",
      parentId = HopGuiSourceMaskingContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.Masking.Publish.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Masking.Publish.Tooltip",
      image = "ui/images/publish.svg",
      category = "Basic",
      categoryOrder = "3")
  public void publishMaskingAction(HopGuiSourceMaskingContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.publishMaskingAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-masking-delete",
      parentId = HopGuiSourceMaskingContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Masking.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Masking.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "4")
  public void deleteMaskingAction(HopGuiSourceMaskingContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.deleteMaskingAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-json-preview",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiSourceModelGraph.Context.Json.Preview.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Json.Preview.Tooltip",
      image = "ui/images/preview.svg",
      category = "Basic",
      categoryOrder = "2")
  public void previewJsonAction(HopGuiSourceJsonContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.previewJsonAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-json-publish",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.Json.Publish.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Json.Publish.Tooltip",
      image = "ui/images/publish.svg",
      category = "Basic",
      categoryOrder = "3")
  public void publishJsonAction(HopGuiSourceJsonContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.publishJsonAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-json-delete",
      parentId = HopGuiSourceJsonContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Json.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Json.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "4")
  public void deleteJsonAction(HopGuiSourceJsonContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.deleteJsonAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-query-edit",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Query.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Query.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editQueryAction(HopGuiSourceQueryContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.editQueryAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-query-preview",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiSourceModelGraph.Context.Query.Preview.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Query.Preview.Tooltip",
      image = "ui/images/preview.svg",
      category = "Basic",
      categoryOrder = "2")
  public void previewQueryAction(HopGuiSourceQueryContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.previewQueryAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-query-publish",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.Query.Publish.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Query.Publish.Tooltip",
      image = "ui/images/publish.svg",
      category = "Basic",
      categoryOrder = "3")
  public void publishQueryAction(HopGuiSourceQueryContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.publishQueryAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-query-delete",
      parentId = HopGuiSourceQueryContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Query.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Query.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "4")
  public void deleteQueryAction(HopGuiSourceQueryContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.deleteQueryAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-graph-add-note",
      parentId = HopGuiSourceModelContext.CONTEXT_ID,
      type = GuiActionType.Create,
      name = "i18n::HopGuiSourceModelGraph.Context.AddNote.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.AddNote.Tooltip",
      image = "ui/images/note-add.svg",
      category = "Basic",
      categoryOrder = "3")
  public void addNote(HopGuiSourceModelContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.addNote(context);
    }
  }

  @GuiContextAction(
      id = "source-model-table-edit",
      parentId = HopGuiSourceTableContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Table.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Table.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editTableAction(HopGuiSourceTableContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.editTableAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-table-preview",
      parentId = HopGuiSourceTableContext.CONTEXT_ID,
      type = GuiActionType.Info,
      name = "i18n::HopGuiSourceModelGraph.Context.Table.Preview.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Table.Preview.Tooltip",
      image = "ui/images/preview.svg",
      category = "Basic",
      categoryOrder = "2")
  public void previewTableAction(HopGuiSourceTableContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.previewTableAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-table-delete",
      parentId = HopGuiSourceTableContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Table.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Table.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "3")
  public void deleteTableAction(HopGuiSourceTableContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.deleteTableAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-pipeline-delete",
      parentId = HopGuiSourcePipelineContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Pipeline.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Pipeline.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "4")
  public void deletePipelineAction(HopGuiSourcePipelineContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.deletePipelineAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-relationship-edit",
      parentId = HopGuiSourceRelationshipContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Relationship.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Relationship.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editRelationshipAction(HopGuiSourceRelationshipContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.editRelationshipAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-relationship-delete",
      parentId = HopGuiSourceRelationshipContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Relationship.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Relationship.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "2")
  public void deleteRelationshipAction(HopGuiSourceRelationshipContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.deleteRelationshipAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-note-edit",
      parentId = HopGuiSourceNoteContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::HopGuiSourceModelGraph.Context.Note.Edit.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Note.Edit.Tooltip",
      image = "ui/images/edit.svg",
      category = "Basic",
      categoryOrder = "1")
  public void editNoteAction(HopGuiSourceNoteContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.editNoteAction(context);
    }
  }

  @GuiContextAction(
      id = "source-model-note-delete",
      parentId = HopGuiSourceNoteContext.CONTEXT_ID,
      type = GuiActionType.Delete,
      name = "i18n::HopGuiSourceModelGraph.Context.Note.Delete.Name",
      tooltip = "i18n::HopGuiSourceModelGraph.Context.Note.Delete.Tooltip",
      image = "ui/images/delete.svg",
      category = "Basic",
      categoryOrder = "2")
  public void deleteNoteAction(HopGuiSourceNoteContext context) {
    HopGuiSourceModelGraph graph = graph(context);
    if (graph != null) {
      graph.deleteNoteAction(context);
    }
  }

  private static HopGuiSourceModelGraph graph(HopGuiSourceModelContext context) {
    return context == null ? null : context.getSourceModelGraph();
  }

  private static HopGuiSourceModelGraph graph(HopGuiSourceTableContext context) {
    return context == null ? null : context.getSourceModelGraph();
  }

  private static HopGuiSourceModelGraph graph(HopGuiSourceQueryContext context) {
    return context == null ? null : context.getSourceModelGraph();
  }

  private static HopGuiSourceModelGraph graph(HopGuiSourceJsonContext context) {
    return context == null ? null : context.getSourceModelGraph();
  }

  private static HopGuiSourceModelGraph graph(HopGuiSourcePipelineContext context) {
    return context == null ? null : context.getSourceModelGraph();
  }

  private static HopGuiSourceModelGraph graph(HopGuiSourceMaskingContext context) {
    return context == null ? null : context.getSourceModelGraph();
  }

  private static HopGuiSourceModelGraph graph(HopGuiSourceRelationshipContext context) {
    return context == null ? null : context.getSourceModelGraph();
  }

  private static HopGuiSourceModelGraph graph(HopGuiSourceNoteContext context) {
    return context == null ? null : context.getSourceModelGraph();
  }
}
