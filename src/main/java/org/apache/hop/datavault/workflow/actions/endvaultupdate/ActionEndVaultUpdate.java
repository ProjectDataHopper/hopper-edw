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
package org.apache.hop.datavault.workflow.actions.endvaultupdate;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metrics.VaultUpdateExecutionSupport;
import org.apache.hop.datavault.metrics.WorkflowLoadOverviewFileWriter;
import org.apache.hop.datavault.metrics.WorkflowLoadOverviewLoader;
import org.apache.hop.datavault.metrics.WorkflowLoadOverviewPublisher;
import org.apache.hop.datavault.metrics.WorkflowLoadOverviewReport;
import org.apache.hop.datavault.metrics.WorkflowLoadOverviewReportFormatter;
import org.apache.hop.datavault.metrics.WorkflowOverviewMetricsResolver;
import org.apache.hop.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.apache.hop.datavault.workflow.ReferencedFilename;
import org.apache.hop.datavault.workflow.WorkflowReferencedObjectVariableSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/** Ends a correlated vault update run and publishes workflow load overview reports. */
@Action(
    id = "END_VAULT_UPDATE",
    name = "i18n::ActionEndVaultUpdate.Name",
    description = "i18n::ActionEndVaultUpdate.Description",
    image = "datavault-model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionEndVaultUpdate.Keywords",
    documentationUrl = "/workflow/actions/endvaultupdate.html")
@GuiPlugin(description = "End Vault Update action")
@Getter
@Setter
public class ActionEndVaultUpdate extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionEndVaultUpdate.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "END_VAULT_UPDATE_ACTION";

  public static final int REFERENCED_OBJECT_MARKDOWN = 0;
  public static final int REFERENCED_OBJECT_HTML = 1;

  @GuiWidgetElement(
      order = "0050",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionEndVaultUpdate.ExecutionIdVariable.Label",
      toolTip = "i18n::ActionEndVaultUpdate.ExecutionIdVariable.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String workflowExecutionIdVariable;

  @GuiWidgetElement(
      order = "0060",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.UseWorkflowLogChannelId.Label",
      toolTip = "i18n::ActionEndVaultUpdate.UseWorkflowLogChannelId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean useWorkflowLogChannelId;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = ExecutionMetricsProfileMeta.class,
      label = "i18n::ActionEndVaultUpdate.ExecutionMetricsProfile.Label",
      toolTip = "i18n::ActionEndVaultUpdate.ExecutionMetricsProfile.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String executionMetricsProfile;

  @GuiWidgetElement(
      order = "0110",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::ActionEndVaultUpdate.DataCatalogConnection.Label",
      toolTip = "i18n::ActionEndVaultUpdate.DataCatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dataCatalogConnection;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.PublishToDatabase.Label",
      toolTip = "i18n::ActionEndVaultUpdate.PublishToDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean publishToDatabase = true;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.PublishToCatalog.Label",
      toolTip = "i18n::ActionEndVaultUpdate.PublishToCatalog.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean publishToCatalog = true;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.LogToWorkflow.Label",
      toolTip = "i18n::ActionEndVaultUpdate.LogToWorkflow.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean logToWorkflow = true;

  @GuiWidgetElement(
      order = "0310",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.WriteMarkdownReport.Label",
      toolTip = "i18n::ActionEndVaultUpdate.WriteMarkdownReport.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean writeMarkdownReport;

  @GuiWidgetElement(
      order = "0320",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.WriteHtmlReport.Label",
      toolTip = "i18n::ActionEndVaultUpdate.WriteHtmlReport.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean writeHtmlReport;

  @GuiWidgetElement(
      order = "0330",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionEndVaultUpdate.ReportOutputFolder.Label",
      toolTip = "i18n::ActionEndVaultUpdate.ReportOutputFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportOutputFolder = "${PROJECT_HOME}/reports";

  @GuiWidgetElement(
      order = "0340",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionEndVaultUpdate.ReportFileBaseName.Label",
      toolTip = "i18n::ActionEndVaultUpdate.ReportFileBaseName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportFileBaseName;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.FailIfNoMetricsFound.Label",
      toolTip = "i18n::ActionEndVaultUpdate.FailIfNoMetricsFound.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failIfNoMetricsFound;

  @GuiWidgetElement(
      order = "0410",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.IncludePipelineDetail.Label",
      toolTip = "i18n::ActionEndVaultUpdate.IncludePipelineDetail.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includePipelineDetail = true;

  @GuiWidgetElement(
      order = "0420",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEndVaultUpdate.IncludeInsights.Label",
      toolTip = "i18n::ActionEndVaultUpdate.IncludeInsights.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeInsights = true;

  public ActionEndVaultUpdate() {
    super();
  }

  public ActionEndVaultUpdate(ActionEndVaultUpdate meta) {
    super(meta);
    this.workflowExecutionIdVariable = meta.workflowExecutionIdVariable;
    this.useWorkflowLogChannelId = meta.useWorkflowLogChannelId;
    this.executionMetricsProfile = meta.executionMetricsProfile;
    this.dataCatalogConnection = meta.dataCatalogConnection;
    this.publishToDatabase = meta.publishToDatabase;
    this.publishToCatalog = meta.publishToCatalog;
    this.logToWorkflow = meta.logToWorkflow;
    this.writeMarkdownReport = meta.writeMarkdownReport;
    this.writeHtmlReport = meta.writeHtmlReport;
    this.reportOutputFolder = meta.reportOutputFolder;
    this.reportFileBaseName = meta.reportFileBaseName;
    this.failIfNoMetricsFound = meta.failIfNoMetricsFound;
    this.includePipelineDetail = meta.includePipelineDetail;
    this.includeInsights = meta.includeInsights;
  }

  @Override
  public String getDialogClassName() {
    return ActionEndVaultUpdateDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(true);
    result.setNrErrors(0);

    String executionIdVariable = resolveExecutionIdVariable();
    String executionId =
        VaultUpdateExecutionSupport.resolveExecutionId(
            getVariables(),
            executionIdVariable,
            useWorkflowLogChannelId,
            VaultUpdateExecutionSupport.resolveWorkflowLogChannelId(getParentWorkflow()));
    if (Utils.isEmpty(executionId)) {
      String errorKey =
          useWorkflowLogChannelId
              ? "ActionEndVaultUpdate.Error.MissingExecutionIdOrLogChannel"
              : "ActionEndVaultUpdate.Error.MissingExecutionId";
      throw new HopException(BaseMessages.getString(PKG, errorKey, executionIdVariable));
    }

    WorkflowOverviewMetricsResolver.ResolvedOverviewMetrics settings =
        WorkflowOverviewMetricsResolver.resolve(
            executionMetricsProfile, dataCatalogConnection, getVariables(), getMetadataProvider());
    DatabaseMeta databaseMeta =
        getMetadataProvider().getSerializer(DatabaseMeta.class).load(settings.targetDatabaseName());
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "ActionEndVaultUpdate.Error.MissingDatabase", settings.targetDatabaseName()));
    }

    Date startedAt = VaultUpdateExecutionSupport.resolveStartedAt(getVariables(), null);
    WorkflowLoadOverviewReport report =
        WorkflowLoadOverviewLoader.load(
            databaseMeta,
            settings.operationsSchema(),
            executionId,
            resolveRootWorkflowName(),
            startedAt,
            getVariables(),
            includePipelineDetail,
            includeInsights,
            WorkflowLoadOverviewReport.DEFAULT_MAX_PIPELINES_PER_MODEL);

    if (report == null) {
      String message =
          BaseMessages.getString(PKG, "ActionEndVaultUpdate.Error.NoMetricsFound", executionId);
      if (failIfNoMetricsFound) {
        throw new HopException(message);
      }
      logBasic(message);
      result.setLogText(message);
      return result;
    }

    if (publishToDatabase || publishToCatalog) {
      WorkflowLoadOverviewPublisher.publish(
          null,
          settings,
          report,
          publishToCatalog,
          publishToDatabase,
          getVariables(),
          getMetadataProvider());
    }

    if (logToWorkflow) {
      String formatted =
          WorkflowLoadOverviewReportFormatter.formatLog(
              report, includePipelineDetail, includeInsights);
      if (!Utils.isEmpty(formatted)) {
        logBasic(formatted);
      }
    }

    if (writeMarkdownReport) {
      String markdownPath =
          WorkflowLoadOverviewFileWriter.writeMarkdown(
              reportOutputFolder,
              reportFileBaseName,
              report,
              includePipelineDetail,
              includeInsights,
              getVariables());
      if (!Utils.isEmpty(markdownPath)) {
        logBasic(
            BaseMessages.getString(PKG, "ActionEndVaultUpdate.Log.MarkdownWritten", markdownPath));
      }
    }

    if (writeHtmlReport) {
      String htmlPath =
          WorkflowLoadOverviewFileWriter.writeHtml(
              reportOutputFolder,
              reportFileBaseName,
              report,
              includePipelineDetail,
              includeInsights,
              getVariables());
      if (!Utils.isEmpty(htmlPath)) {
        logBasic(BaseMessages.getString(PKG, "ActionEndVaultUpdate.Log.HtmlWritten", htmlPath));
      }
    }

    result.setNrLinesInput(report.getTotalSourceRowsRead());
    result.setNrLinesOutput(report.getTotalTargetRowsInserted());
    if (report.getTotalErrors() > 0) {
      result.setNrErrors(report.getTotalErrors());
      result.setResult(false);
    }
    result.setLogText(
        BaseMessages.getString(
            PKG,
            "ActionEndVaultUpdate.Result.Summary",
            report.getOverviewId(),
            Integer.toString(report.getModelCount()),
            Long.toString(report.getTotalTargetRowsInserted())));
    return result;
  }

  private String resolveExecutionIdVariable() {
    return Utils.isEmpty(workflowExecutionIdVariable)
        ? VaultUpdateExecutionSupport.defaultExecutionIdVariableName()
        : workflowExecutionIdVariable;
  }

  private String resolveRootWorkflowName() {
    IWorkflowEngine<WorkflowMeta> parentWorkflow = getParentWorkflow();
    if (parentWorkflow != null && parentWorkflow.getWorkflowMeta() != null) {
      return parentWorkflow.getWorkflowMeta().getName();
    }
    WorkflowMeta parentWorkflowMeta = getParentWorkflowMeta();
    if (parentWorkflowMeta != null) {
      return parentWorkflowMeta.getName();
    }
    return null;
  }

  @Override
  public String[] getReferencedObjectDescriptions() {
    return new String[] {
      BaseMessages.getString(PKG, "ActionEndVaultUpdate.ReferencedObject.MarkdownDescription"),
      BaseMessages.getString(PKG, "ActionEndVaultUpdate.ReferencedObject.HtmlDescription"),
    };
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    IVariables variables =
        WorkflowReferencedObjectVariableSupport.effectiveVariables(
            getVariables(), getParentWorkflowMeta());
    return new boolean[] {
      writeMarkdownReport && canOpenReferencedReport(REFERENCED_OBJECT_MARKDOWN, variables),
      writeHtmlReport && canOpenReferencedReport(REFERENCED_OBJECT_HTML, variables),
    };
  }

  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    String path = resolveReferencedReportPath(index, variables);
    if (Utils.isEmpty(path)) {
      return null;
    }
    return new ReferencedFilename(path);
  }

  @Override
  public boolean supportsDrillDown() {
    return true;
  }

  private boolean canOpenReferencedReport(int index, IVariables variables) {
    try {
      return !Utils.isEmpty(resolveReferencedReportPath(index, variables));
    } catch (HopException e) {
      return false;
    }
  }

  private String resolveReferencedReportPath(int index, IVariables variables) throws HopException {
    IVariables effectiveVariables =
        WorkflowReferencedObjectVariableSupport.effectiveVariables(
            getVariables(), getParentWorkflowMeta(), variables);
    String extension =
        index == REFERENCED_OBJECT_HTML
            ? WorkflowLoadOverviewFileWriter.HTML_EXTENSION
            : WorkflowLoadOverviewFileWriter.MARKDOWN_EXTENSION;
    return WorkflowLoadOverviewFileWriter.resolveExistingReportPath(
        reportOutputFolder,
        reportFileBaseName,
        extension,
        resolveRootWorkflowName(),
        effectiveVariables);
  }

  @Override
  public IAction clone() {
    return new ActionEndVaultUpdate(this);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }
}
