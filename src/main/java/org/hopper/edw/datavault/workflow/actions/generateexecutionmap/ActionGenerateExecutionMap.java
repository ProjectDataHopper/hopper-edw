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
package org.hopper.edw.datavault.workflow.actions.generateexecutionmap;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.hopper.edw.datavault.command.executionmap.ExecutionMapService;
import org.hopper.edw.datavault.command.executionmap.ExecutionMapService.GenerateResult;
import org.hopper.edw.datavault.executionmap.CrawlOptions;
import org.hopper.edw.datavault.executionmap.ExecutionMapPersistence;

/** Crawls a workflow or pipeline and writes a read-only execution map (.hem) file. */
@Action(
    id = "GENERATE_EXECUTION_MAP",
    name = "i18n::ActionGenerateExecutionMap.Name",
    description = "i18n::ActionGenerateExecutionMap.Description",
    image = "execution-map.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionGenerateExecutionMap.Keywords",
    documentationUrl = "/workflow/actions/generateexecutionmap.html")
@GuiPlugin(description = "Generate execution map action")
@Getter
@Setter
public class ActionGenerateExecutionMap extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionGenerateExecutionMap.class;

  public static final String GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID =
      "GENERATE_EXECUTION_MAP_ACTION_SOURCE_TAB";
  public static final String GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID =
      "GENERATE_EXECUTION_MAP_ACTION_CRAWL_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::ActionGenerateExecutionMap.RootArtifact.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.RootArtifact.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private String rootArtifactFilename;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::ActionGenerateExecutionMap.OutputHemFile.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.OutputHemFile.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private String outputHemFile;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateExecutionMap.IncludeGeneratedPipelines.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.IncludeGeneratedPipelines.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID)
  @HopMetadataProperty
  private boolean includeGeneratedPipelines = true;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateExecutionMap.IncludeDatasetNodes.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.IncludeDatasetNodes.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID)
  @HopMetadataProperty
  private boolean includeDatasetNodes = true;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateExecutionMap.CaptureSnapshots.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.CaptureSnapshots.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID)
  @HopMetadataProperty
  private boolean captureSnapshots = true;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateExecutionMap.FollowNestedWorkflows.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.FollowNestedWorkflows.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID)
  @HopMetadataProperty
  private boolean followNestedWorkflows = true;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateExecutionMap.FollowNestedPipelines.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.FollowNestedPipelines.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID)
  @HopMetadataProperty
  private boolean followNestedPipelines = true;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateExecutionMap.IncludeWorkflowActions.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.IncludeWorkflowActions.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID)
  @HopMetadataProperty
  private boolean includeWorkflowActions;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateExecutionMap.IncludePipelineTransforms.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.IncludePipelineTransforms.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID)
  @HopMetadataProperty
  private boolean includePipelineTransforms;

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateExecutionMap.ExportLineage.Label",
      toolTip = "i18n::ActionGenerateExecutionMap.ExportLineage.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CRAWL_TAB_ID)
  @HopMetadataProperty
  private boolean exportLineage;

  public ActionGenerateExecutionMap() {
    super();
  }

  public ActionGenerateExecutionMap(ActionGenerateExecutionMap meta) {
    super(meta);
    this.rootArtifactFilename = meta.rootArtifactFilename;
    this.outputHemFile = meta.outputHemFile;
    this.includeGeneratedPipelines = meta.includeGeneratedPipelines;
    this.includeDatasetNodes = meta.includeDatasetNodes;
    this.captureSnapshots = meta.captureSnapshots;
    this.followNestedWorkflows = meta.followNestedWorkflows;
    this.followNestedPipelines = meta.followNestedPipelines;
    this.includeWorkflowActions = meta.includeWorkflowActions;
    this.includePipelineTransforms = meta.includePipelineTransforms;
    this.exportLineage = meta.exportLineage;
  }

  @Override
  public String getDialogClassName() {
    return ActionGenerateExecutionMapDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    String rootPath = resolveRootArtifactPath();
    CrawlOptions options = toCrawlOptions();
    String outputPath = Utils.isEmpty(outputHemFile) ? null : getVariables().resolve(outputHemFile);

    logBasic(BaseMessages.getString(PKG, "ActionGenerateExecutionMap.Log.Crawling", rootPath));
    GenerateResult generateResult =
        ExecutionMapService.generate(
            rootPath, outputPath, getVariables(), getMetadataProvider(), options);

    logBasic(
        BaseMessages.getString(
            PKG,
            "ActionGenerateExecutionMap.Log.Written",
            generateResult.getOutputPath(),
            generateResult.getDocument().getNodesOrEmpty().size()));

    if (exportLineage) {
      String lineagePath =
          ExecutionMapService.defaultLineageOutputPath(generateResult.getOutputPath());
      ExecutionMapService.exportLineage(generateResult.getDocument(), lineagePath, getVariables());
      logBasic(
          BaseMessages.getString(
              PKG, "ActionGenerateExecutionMap.Log.LineageWritten", lineagePath));
    }

    logWarnings(generateResult.getWarnings());

    result.setResult(true);
    result.setNrErrors(0);
    result.setLogText(
        BaseMessages.getString(
            PKG, "ActionGenerateExecutionMap.Result.Success", generateResult.getOutputPath()));
    return result;
  }

  CrawlOptions toCrawlOptions() {
    return CrawlOptions.builder()
        .includeGeneratedPipelines(includeGeneratedPipelines)
        .includeDatasetNodes(includeDatasetNodes)
        .captureSnapshots(captureSnapshots)
        .followNestedWorkflows(followNestedWorkflows)
        .followNestedPipelines(followNestedPipelines)
        .includeWorkflowActions(includeWorkflowActions)
        .includePipelineTransforms(includePipelineTransforms)
        .build();
  }

  String resolveRootArtifactPath() throws HopException {
    return resolveRootArtifactPath(
        rootArtifactFilename, getParentWorkflow(), getParentWorkflowMeta(), getVariables());
  }

  static String resolveRootArtifactPath(
      String configuredRootArtifactFilename,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      IVariables variables)
      throws HopException {
    return resolveRootArtifactPath(configuredRootArtifactFilename, parentWorkflow, null, variables);
  }

  static String resolveRootArtifactPath(
      String configuredRootArtifactFilename,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      WorkflowMeta parentWorkflowMeta,
      IVariables variables)
      throws HopException {
    if (!Utils.isEmpty(configuredRootArtifactFilename)) {
      return variables != null
          ? variables.resolve(configuredRootArtifactFilename)
          : configuredRootArtifactFilename;
    }
    if (parentWorkflow != null && parentWorkflow.getWorkflowMeta() != null) {
      String filename = parentWorkflow.getWorkflowMeta().getFilename();
      if (!Utils.isEmpty(filename)) {
        return variables != null ? variables.resolve(filename) : filename;
      }
    }
    if (parentWorkflowMeta != null) {
      String filename = parentWorkflowMeta.getFilename();
      if (!Utils.isEmpty(filename)) {
        return variables != null ? variables.resolve(filename) : filename;
      }
    }
    throw new HopException(
        BaseMessages.getString(PKG, "ActionGenerateExecutionMap.Error.MissingRootArtifact"));
  }

  private void logWarnings(List<String> warnings) {
    if (warnings == null || warnings.isEmpty()) {
      return;
    }
    for (String warning : warnings) {
      if (!Utils.isEmpty(warning)) {
        logBasic(BaseMessages.getString(PKG, "ActionGenerateExecutionMap.Log.Warning", warning));
      }
    }
  }

  @Override
  public IAction clone() {
    return new ActionGenerateExecutionMap(this);
  }

  @Override
  public String[] getReferencedObjectDescriptions() {
    return new String[] {
      BaseMessages.getString(PKG, "ActionGenerateExecutionMap.ReferencedObject.Description"),
    };
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    return new boolean[] {canResolveReferencedExecutionMapPath(getVariables())};
  }

  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    return loadReferencedExecutionMap(metadataProvider, variables);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  @Override
  public boolean supportsDrillDown() {
    return true;
  }

  private boolean canResolveReferencedExecutionMapPath(IVariables variables) {
    try {
      resolveReferencedExecutionMapPath(variables);
      return true;
    } catch (HopException e) {
      return false;
    }
  }

  private IHasFilename loadReferencedExecutionMap(
      IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    return ExecutionMapPersistence.load(
        resolveReferencedExecutionMapPath(variables), metadataProvider, variables);
  }

  String resolveReferencedExecutionMapPath(IVariables variables) throws HopException {
    if (!Utils.isEmpty(outputHemFile)) {
      return variables.resolve(outputHemFile);
    }
    String rootPath =
        resolveRootArtifactPath(
            rootArtifactFilename, getParentWorkflow(), getParentWorkflowMeta(), variables);
    return ExecutionMapService.resolveOutputPath(rootPath, null, variables);
  }
}
