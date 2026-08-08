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
package org.apache.hop.datavault.workflow.actions.updateresourcegroup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.LogChannel;
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
import org.apache.hop.datavault.resourcedefinition.ParallelValidationSupport;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.workflow.WorkflowReferencedObjectVariableSupport;
import org.apache.hop.datavault.workflow.actions.businessvaultupdate.ActionBusinessVaultUpdate;
import org.apache.hop.datavault.workflow.actions.datavaultupdate.ActionDataVaultUpdate;
import org.apache.hop.datavault.workflow.actions.dimensionalupdate.ActionDimensionalUpdate;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelUpdatePlanner.ModelUpdateJob;
import org.apache.hop.datavault.workflow.actions.updateresourcegroup.ResourceGroupModelValidationSupport.ModelCheckOutcome;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.config.WorkflowRunConfiguration;

/**
 * Updates every DV / BV / DM model listed on a resource definition group, in layer order (DV → BV →
 * DM) and group list order within each layer. Optionally brackets the wave with vault-update
 * metrics (Begin/End behaviour) so separate Begin/End actions are not required.
 */
@Action(
    id = "UPDATE_RESOURCE_DEFINITION_GROUP",
    name = "i18n::ActionUpdateResourceDefinitionGroup.Name",
    description = "i18n::ActionUpdateResourceDefinitionGroup.Description",
    image = "resource-definition-group.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionUpdateResourceDefinitionGroup.Keywords",
    documentationUrl = "/workflow/actions/updateresourcedefinitiongroup.html")
@GuiPlugin(description = "Update Resource Definition Group action")
@Getter
@Setter
public class ActionUpdateResourceDefinitionGroup extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionUpdateResourceDefinitionGroup.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID =
      "UPDATE_RESOURCE_DEFINITION_GROUP_ACTION";

  public static final String GUI_PLUGIN_ELEMENT_SELECTION_TAB_ID =
      "UPDATE_RESOURCE_DEFINITION_GROUP_ACTION_SELECTION_TAB";
  public static final String GUI_PLUGIN_ELEMENT_RUN_TAB_ID =
      "UPDATE_RESOURCE_DEFINITION_GROUP_ACTION_RUN_TAB";
  public static final String GUI_PLUGIN_ELEMENT_OPERATIONS_TAB_ID =
      "UPDATE_RESOURCE_DEFINITION_GROUP_ACTION_OPERATIONS_TAB";
  public static final String GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID =
      "UPDATE_RESOURCE_DEFINITION_GROUP_ACTION_VALIDATION_TAB";
  public static final String GUI_PLUGIN_ELEMENT_CATALOG_TAB_ID =
      "UPDATE_RESOURCE_DEFINITION_GROUP_ACTION_CATALOG_TAB";
  public static final String GUI_PLUGIN_ELEMENT_METRICS_TAB_ID =
      "UPDATE_RESOURCE_DEFINITION_GROUP_ACTION_METRICS_TAB";
  public static final String GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID =
      "UPDATE_RESOURCE_DEFINITION_GROUP_ACTION_REPORTS_TAB";

  /** Tab parent ids for dialog create/get/set of GuiCompositeWidgets. */
  public static final String[] GUI_PLUGIN_ELEMENT_TAB_IDS = {
    GUI_PLUGIN_ELEMENT_SELECTION_TAB_ID,
    GUI_PLUGIN_ELEMENT_RUN_TAB_ID,
    GUI_PLUGIN_ELEMENT_OPERATIONS_TAB_ID,
    GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID,
    GUI_PLUGIN_ELEMENT_CATALOG_TAB_ID,
    GUI_PLUGIN_ELEMENT_METRICS_TAB_ID,
    GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID,
  };

  /** Extension-data key for the last model-validation report text. */
  public static final String RESULT_ATTR_MODEL_VALIDATION_REPORT = "modelValidationReportText";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::ActionUpdateResourceDefinitionGroup.Group.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SELECTION_TAB_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.IncludeDataVault.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.IncludeDataVault.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SELECTION_TAB_ID)
  @HopMetadataProperty
  private boolean includeDataVault = true;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.IncludeBusinessVault.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.IncludeBusinessVault.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SELECTION_TAB_ID)
  @HopMetadataProperty
  private boolean includeBusinessVault = true;

  @GuiWidgetElement(
      order = "0220",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.IncludeDimensional.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.IncludeDimensional.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SELECTION_TAB_ID)
  @HopMetadataProperty
  private boolean includeDimensional = true;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.LoadDate.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.LoadDate.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_RUN_TAB_ID)
  @HopMetadataProperty
  private String loadDate;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.METADATA,
      metadata = PipelineRunConfiguration.class,
      label = "i18n::ActionUpdateResourceDefinitionGroup.PipelineRunConfiguration.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.PipelineRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_RUN_TAB_ID)
  @HopMetadataProperty
  private String pipelineRunConfiguration = "local";

  @GuiWidgetElement(
      order = "0410",
      type = GuiElementType.METADATA,
      metadata = WorkflowRunConfiguration.class,
      label = "i18n::ActionUpdateResourceDefinitionGroup.WorkflowRunConfiguration.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.WorkflowRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_RUN_TAB_ID)
  @HopMetadataProperty
  private String workflowRunConfiguration = "local";

  @GuiWidgetElement(
      order = "0420",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.ParallelPipelineCopies.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ParallelPipelineCopies.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_RUN_TAB_ID)
  @HopMetadataProperty
  private String parallelPipelineCopies = "1";

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.LogModelCheckFailures.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.LogModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private boolean logModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0510",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.AbortOnModelCheckFailures.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.AbortOnModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private boolean abortOnModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0520",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.DetailedDataTypeChecking.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.DetailedDataTypeChecking.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private boolean detailedDataTypeChecking = true;

  /**
   * When detailed type checking is on, reuse DISCOVERED layouts from the latest schema harvest
   * (e.g. {@code DV_SCHEMA_HARVEST_RUN_ID}) instead of re-querying live source JDBC metadata.
   */
  @GuiWidgetElement(
      order = "0525",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.PreferHarvestForTypeChecks.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.PreferHarvestForTypeChecks.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private boolean preferHarvestForTypeChecks = true;

  @GuiWidgetElement(
      order = "0526",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.HarvestRunId.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.HarvestRunId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private String harvestRunId =
      "${"
          + org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryPublisher
              .VAR_SCHEMA_HARVEST_RUN_ID
          + "}";

  @GuiWidgetElement(
      order = "0527",
      type = GuiElementType.METADATA,
      metadata = org.apache.hop.core.database.DatabaseMeta.class,
      label = "i18n::ActionUpdateResourceDefinitionGroup.HarvestHistoryDatabase.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.HarvestHistoryDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private String harvestHistoryDatabase;

  /**
   * Max concurrent model checks before the update wave (and for dry-run validation). Literal or Hop
   * variable; default {@code 8}.
   */
  @GuiWidgetElement(
      order = "0530",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.ModelCheckParallelism.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ModelCheckParallelism.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private String modelCheckParallelism = "8";

  /**
   * When true, model-check warnings are omitted from the workflow log (still stored in the
   * validation report file). Errors still abort when abort-on-check is enabled.
   */
  @GuiWidgetElement(
      order = "0540",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.IgnoreModelCheckWarnings.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.IgnoreModelCheckWarnings.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private boolean ignoreModelCheckWarnings;

  @GuiWidgetElement(
      order = "0550",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.WriteValidationReport.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.WriteValidationReport.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private boolean writeValidationReport;

  @GuiWidgetElement(
      order = "0560",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.ValidationReportFolder.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ValidationReportFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private String validationReportFolder = "${PROJECT_HOME}/work/reports";

  @GuiWidgetElement(
      order = "0570",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.ValidationReportBaseName.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ValidationReportBaseName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private String validationReportBaseName;

  @GuiWidgetElement(
      order = "0580",
      type = GuiElementType.COMBO,
      variables = false,
      comboValuesMethod = "getValidationReportFormatOptions",
      label = "i18n::ActionUpdateResourceDefinitionGroup.ValidationReportFormat.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ValidationReportFormat.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_VALIDATION_TAB_ID)
  @HopMetadataProperty
  private String validationReportFormat =
      GroupModelValidationReportFileWriter.ReportFormat.MARKDOWN.name();

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.UpdateTargetDatabaseStructure.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.UpdateTargetDatabaseStructure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_OPERATIONS_TAB_ID)
  @HopMetadataProperty
  private boolean updateTargetDatabaseStructure = true;

  @GuiWidgetElement(
      order = "0610",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.FailIfDdlNeeded.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.FailIfDdlNeeded.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_OPERATIONS_TAB_ID)
  @HopMetadataProperty
  private boolean failIfDdlNeeded;

  /**
   * Dry run: skip data load pipelines on each child DV/BV/DM update (same semantics as Data Vault
   * Update). Model checks still run; DDL still runs when structure update / fail-if-DDL is on.
   */
  @GuiWidgetElement(
      order = "0615",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.DoNotUpdateTargetDatabase.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.DoNotUpdateTargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_OPERATIONS_TAB_ID)
  @HopMetadataProperty
  private boolean doNotUpdateTargetDatabase;

  @GuiWidgetElement(
      order = "0620",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.PublishToCatalog.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.PublishToCatalog.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CATALOG_TAB_ID)
  @HopMetadataProperty
  private boolean publishToCatalog = true;

  @GuiWidgetElement(
      order = "0630",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::ActionUpdateResourceDefinitionGroup.DataCatalogConnection.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.DataCatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CATALOG_TAB_ID)
  @HopMetadataProperty
  private String dataCatalogConnection;

  @GuiWidgetElement(
      order = "0640",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.EnsureSpecialRecords.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.EnsureSpecialRecords.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_OPERATIONS_TAB_ID)
  @HopMetadataProperty
  private boolean ensureSpecialRecords = true;

  @GuiWidgetElement(
      order = "0650",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.RecordSourceGroup.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.RecordSourceGroup.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_RUN_TAB_ID)
  @HopMetadataProperty
  private String recordSourceGroup;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.ManageMetrics.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ManageMetrics.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_METRICS_TAB_ID)
  @HopMetadataProperty
  private boolean manageVaultUpdateMetrics = true;

  @GuiWidgetElement(
      order = "0710",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.UseWorkflowLogChannelId.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.UseWorkflowLogChannelId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_METRICS_TAB_ID)
  @HopMetadataProperty
  private boolean useWorkflowLogChannelId = true;

  @GuiWidgetElement(
      order = "0720",
      type = GuiElementType.METADATA,
      metadata = ExecutionMetricsProfileMeta.class,
      label = "i18n::ActionUpdateResourceDefinitionGroup.ExecutionMetricsProfile.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ExecutionMetricsProfile.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_METRICS_TAB_ID)
  @HopMetadataProperty
  private String executionMetricsProfile;

  @GuiWidgetElement(
      order = "0730",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.PublishMetricsToDatabase.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.PublishMetricsToDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_METRICS_TAB_ID)
  @HopMetadataProperty
  private boolean publishMetricsToDatabase = true;

  @GuiWidgetElement(
      order = "0740",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.PublishMetricsToCatalog.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.PublishMetricsToCatalog.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_METRICS_TAB_ID)
  @HopMetadataProperty
  private boolean publishMetricsToCatalog = true;

  @GuiWidgetElement(
      order = "0750",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.LogMetricsToWorkflow.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.LogMetricsToWorkflow.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_METRICS_TAB_ID)
  @HopMetadataProperty
  private boolean logMetricsToWorkflow = true;

  @GuiWidgetElement(
      order = "0760",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.WriteMarkdownReport.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.WriteMarkdownReport.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID)
  @HopMetadataProperty
  private boolean writeMarkdownReport;

  @GuiWidgetElement(
      order = "0770",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.WriteHtmlReport.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.WriteHtmlReport.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID)
  @HopMetadataProperty
  private boolean writeHtmlReport;

  @GuiWidgetElement(
      order = "0780",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.ReportOutputFolder.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ReportOutputFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID)
  @HopMetadataProperty
  private String reportOutputFolder = "${PROJECT_HOME}/work/reports";

  @GuiWidgetElement(
      order = "0790",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionUpdateResourceDefinitionGroup.ReportFileBaseName.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.ReportFileBaseName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID)
  @HopMetadataProperty
  private String reportFileBaseName;

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.FailIfNoMetricsFound.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.FailIfNoMetricsFound.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_METRICS_TAB_ID)
  @HopMetadataProperty
  private boolean failIfNoMetricsFound;

  @GuiWidgetElement(
      order = "0810",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.IncludePipelineDetail.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.IncludePipelineDetail.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID)
  @HopMetadataProperty
  private boolean includePipelineDetail = true;

  @GuiWidgetElement(
      order = "0820",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionUpdateResourceDefinitionGroup.IncludeInsights.Label",
      toolTip = "i18n::ActionUpdateResourceDefinitionGroup.IncludeInsights.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_REPORTS_TAB_ID)
  @HopMetadataProperty
  private boolean includeInsights = true;

  public ActionUpdateResourceDefinitionGroup() {
    super();
    this.modelCheckParallelism = "8";
    this.validationReportFolder = "${PROJECT_HOME}/work/reports";
    this.validationReportFormat = GroupModelValidationReportFileWriter.ReportFormat.MARKDOWN.name();
  }

  public ActionUpdateResourceDefinitionGroup(ActionUpdateResourceDefinitionGroup meta) {
    super(meta);
    this.resourceDefinitionGroup = meta.resourceDefinitionGroup;
    this.includeDataVault = meta.includeDataVault;
    this.includeBusinessVault = meta.includeBusinessVault;
    this.includeDimensional = meta.includeDimensional;
    this.loadDate = meta.loadDate;
    this.pipelineRunConfiguration = meta.pipelineRunConfiguration;
    this.workflowRunConfiguration = meta.workflowRunConfiguration;
    this.parallelPipelineCopies = meta.parallelPipelineCopies;
    this.logModelCheckFailures = meta.logModelCheckFailures;
    this.abortOnModelCheckFailures = meta.abortOnModelCheckFailures;
    this.detailedDataTypeChecking = meta.detailedDataTypeChecking;
    this.preferHarvestForTypeChecks = meta.preferHarvestForTypeChecks;
    this.harvestRunId = meta.harvestRunId;
    this.harvestHistoryDatabase = meta.harvestHistoryDatabase;
    this.modelCheckParallelism =
        meta.modelCheckParallelism != null ? meta.modelCheckParallelism : "8";
    this.ignoreModelCheckWarnings = meta.ignoreModelCheckWarnings;
    this.writeValidationReport = meta.writeValidationReport;
    this.validationReportFolder = meta.validationReportFolder;
    this.validationReportBaseName = meta.validationReportBaseName;
    this.validationReportFormat =
        meta.validationReportFormat != null
            ? meta.validationReportFormat
            : GroupModelValidationReportFileWriter.ReportFormat.MARKDOWN.name();
    this.updateTargetDatabaseStructure = meta.updateTargetDatabaseStructure;
    this.failIfDdlNeeded = meta.failIfDdlNeeded;
    this.doNotUpdateTargetDatabase = meta.doNotUpdateTargetDatabase;
    this.publishToCatalog = meta.publishToCatalog;
    this.dataCatalogConnection = meta.dataCatalogConnection;
    this.ensureSpecialRecords = meta.ensureSpecialRecords;
    this.recordSourceGroup = meta.recordSourceGroup;
    this.manageVaultUpdateMetrics = meta.manageVaultUpdateMetrics;
    this.useWorkflowLogChannelId = meta.useWorkflowLogChannelId;
    this.executionMetricsProfile = meta.executionMetricsProfile;
    this.publishMetricsToDatabase = meta.publishMetricsToDatabase;
    this.publishMetricsToCatalog = meta.publishMetricsToCatalog;
    this.logMetricsToWorkflow = meta.logMetricsToWorkflow;
    this.writeMarkdownReport = meta.writeMarkdownReport;
    this.writeHtmlReport = meta.writeHtmlReport;
    this.reportOutputFolder = meta.reportOutputFolder;
    this.reportFileBaseName = meta.reportFileBaseName;
    this.failIfNoMetricsFound = meta.failIfNoMetricsFound;
    this.includePipelineDetail = meta.includePipelineDetail;
    this.includeInsights = meta.includeInsights;
  }

  /** Hop GUI comboValuesMethod contract: {@code (ILogChannel, IHopMetadataProvider) -> List}. */
  public List<String> getValidationReportFormatOptions(
      org.apache.hop.core.logging.ILogChannel log, IHopMetadataProvider metadataProvider) {
    return List.of(
        GroupModelValidationReportFileWriter.ReportFormat.MARKDOWN.name(),
        GroupModelValidationReportFileWriter.ReportFormat.HTML.name(),
        GroupModelValidationReportFileWriter.ReportFormat.BOTH.name());
  }

  @Override
  public String getDialogClassName() {
    return ActionUpdateResourceDefinitionGroupDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    if (Utils.isEmpty(resourceDefinitionGroup)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionUpdateResourceDefinitionGroup.Error.MissingGroup"));
    }

    String groupName = resolve(resourceDefinitionGroup);
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(groupName, getMetadataProvider());

    List<ModelUpdateJob> jobs =
        ResourceGroupModelUpdatePlanner.plan(
            group, includeDataVault, includeBusinessVault, includeDimensional, this);
    if (jobs.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "ActionUpdateResourceDefinitionGroup.Error.NoModels", groupName));
    }

    String catalogConnection = resolveCatalogConnection(group);
    String metricsProfile = Const.NVL(resolve(executionMetricsProfile), "");
    int checkParallelism = resolveModelCheckParallelism();

    if (doNotUpdateTargetDatabase) {
      logBasic(
          BaseMessages.getString(PKG, "ActionUpdateResourceDefinitionGroup.Log.DryRunEnabled"));
    }

    // Parallel model validation for the whole group (before any load / DDL wave).
    boolean preValidated = false;
    if (logModelCheckFailures || abortOnModelCheckFailures || writeValidationReport) {
      preValidated = logModelCheckFailures || abortOnModelCheckFailures;
      boolean validationFailed = runParallelModelChecks(jobs, checkParallelism, groupName, result);
      if (validationFailed && abortOnModelCheckFailures) {
        result.setResult(false);
        result.setNrErrors(Math.max(1, result.getNrErrors()));
        logError(
            BaseMessages.getString(
                PKG, "ActionUpdateResourceDefinitionGroup.Log.AbortingOnModelCheck"));
        return result;
      }
      // When only writing a report (no log/abort), still skip child re-checks if we ran checks.
      if (writeValidationReport) {
        preValidated = true;
      }
    }

    // Pure dry-run validation: no data load, and no structure/DDL work requested → done.
    if (doNotUpdateTargetDatabase && !updateTargetDatabaseStructure && !failIfDdlNeeded) {
      result.setResult(true);
      result.setNrErrors(0);
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionUpdateResourceDefinitionGroup.Log.DryRunValidationOnly",
              groupName,
              Integer.toString(jobs.size()),
              Integer.toString(checkParallelism)));
      return result;
    }

    String executionId = null;
    if (manageVaultUpdateMetrics && !doNotUpdateTargetDatabase) {
      executionId =
          VaultUpdateExecutionSupport.beginExecution(
              getParentWorkflow(),
              VaultUpdateExecutionSupport.defaultExecutionIdVariableName(),
              false,
              useWorkflowLogChannelId);
      if (Utils.isEmpty(executionId) && useWorkflowLogChannelId) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "ActionUpdateResourceDefinitionGroup.Error.MissingWorkflowLogChannelId"));
      }
      // beginExecution writes onto the parent workflow only. This action (and the programmatic
      // child DV/BV/DM updates it spawns) already has its own variable map from initializeFrom —
      // propagate so load_run.workflow_execution_id is set and overview reports can be written.
      if (getParentWorkflow() != null) {
        VaultUpdateExecutionSupport.propagateExecutionVariables(getParentWorkflow(), this);
      } else if (!Utils.isEmpty(executionId)) {
        setVariable(VaultUpdateExecutionSupport.defaultExecutionIdVariableName(), executionId);
      }
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionUpdateResourceDefinitionGroup.Log.MetricsStarted",
              Const.NVL(executionId, "")));
    }

    int index = 0;
    for (ModelUpdateJob job : jobs) {
      index++;
      logBasic(
          BaseMessages.getString(
              PKG,
              doNotUpdateTargetDatabase
                  ? "ActionUpdateResourceDefinitionGroup.Log.DryRunModel"
                  : "ActionUpdateResourceDefinitionGroup.Log.UpdatingModel",
              job.layer().name(),
              job.modelFile(),
              Integer.toString(index),
              Integer.toString(jobs.size())));
      Result modelResult =
          runModelUpdate(job, catalogConnection, metricsProfile, result, nr, preValidated);
      mergeResult(result, modelResult);
      if (modelResult.getNrErrors() > 0 || !modelResult.isResult()) {
        result.setResult(false);
        result.setNrErrors(Math.max(1, result.getNrErrors()));
        logError(
            BaseMessages.getString(
                PKG,
                "ActionUpdateResourceDefinitionGroup.Error.ModelFailed",
                job.layer().name(),
                job.modelFile()));
        // Surface the child action's last error lines here — full detail is earlier in the log
        // and easy to miss when many tables update in one model run.
        String detail = summarizeResultErrors(modelResult);
        if (!Utils.isEmpty(detail)) {
          logError(
              BaseMessages.getString(
                  PKG, "ActionUpdateResourceDefinitionGroup.Error.ModelFailedDetail", detail));
        }
        if (manageVaultUpdateMetrics && !doNotUpdateTargetDatabase) {
          publishOverview(executionId, catalogConnection, metricsProfile, false);
        }
        return result;
      }
    }

    if (manageVaultUpdateMetrics && !doNotUpdateTargetDatabase) {
      publishOverview(executionId, catalogConnection, metricsProfile, true);
    }

    result.setResult(true);
    result.setNrErrors(0);
    logBasic(
        BaseMessages.getString(
            PKG,
            doNotUpdateTargetDatabase
                ? "ActionUpdateResourceDefinitionGroup.Log.DryRunCompleted"
                : "ActionUpdateResourceDefinitionGroup.Log.Completed",
            groupName,
            Integer.toString(jobs.size())));
    return result;
  }

  private int resolveModelCheckParallelism() {
    String raw = modelCheckParallelism;
    if (!Utils.isEmpty(raw)) {
      raw = resolve(raw);
    }
    return ParallelValidationSupport.resolveParallelism(
        raw, ParallelValidationSupport.DEFAULT_PARALLELISM);
  }

  /**
   * @return true when any model had check errors (or failed to load/check)
   */
  private boolean runParallelModelChecks(
      List<ModelUpdateJob> jobs, int parallelism, String groupName, Result result)
      throws HopException {
    java.time.Instant startedAt = java.time.Instant.now();
    logBasic(
        BaseMessages.getString(
            PKG,
            "ActionUpdateResourceDefinitionGroup.Log.ModelCheckStart",
            Integer.toString(jobs.size()),
            Integer.toString(parallelism)));

    ResourceGroupModelValidationSupport.HarvestReuseSettings harvestReuse =
        new ResourceGroupModelValidationSupport.HarvestReuseSettings(
            preferHarvestForTypeChecks,
            harvestRunId,
            harvestHistoryDatabase,
            null,
            dataCatalogConnection,
            resourceDefinitionGroup);
    List<ModelCheckOutcome> outcomes =
        ResourceGroupModelValidationSupport.checkModels(
            jobs,
            detailedDataTypeChecking,
            parallelism,
            harvestReuse,
            this,
            getMetadataProvider(),
            getLogChannel());
    java.time.Instant finishedAt = java.time.Instant.now();

    GroupModelValidationReport report =
        GroupModelValidationAggregator.aggregate(
            groupName, outcomes, parallelism, startedAt, finishedAt);

    boolean includeWarningsInLog = logModelCheckFailures && !ignoreModelCheckWarnings;
    String logText = GroupModelValidationReportFormatter.formatLog(report, includeWarningsInLog);
    if (logModelCheckFailures || abortOnModelCheckFailures) {
      if (!Utils.isEmpty(logText)) {
        for (String line : logText.split("\n")) {
          if (Utils.isEmpty(line)) {
            continue;
          }
          if (line.startsWith("ERROR") || line.startsWith("FAILED")) {
            logError(line);
          } else {
            logBasic(line);
          }
        }
      }
    } else {
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionUpdateResourceDefinitionGroup.Log.ModelCheckFinished",
              Integer.toString(report.modelsChecked()),
              Integer.toString(report.modelsWithErrors()),
              Long.toString(Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli())),
              Integer.toString(parallelism)));
    }

    stashModelValidationReport(logText);

    if (writeValidationReport) {
      writeModelValidationReportFiles(report);
    }

    if (report.hasErrors() && result != null) {
      result.setNrErrors(result.getNrErrors() + Math.max(1, report.totalErrors()));
    }
    return report.hasErrors();
  }

  private void writeModelValidationReportFiles(GroupModelValidationReport report)
      throws HopException {
    String folder = Const.NVL(validationReportFolder, "${PROJECT_HOME}/work/reports");
    GroupModelValidationReportFileWriter.ReportFormat format =
        parseValidationReportFormat(validationReportFormat);
    List<String> written =
        GroupModelValidationReportFileWriter.write(
            folder, validationReportBaseName, report, format, this);
    for (String path : written) {
      logBasic(
          BaseMessages.getString(
              PKG, "ActionUpdateResourceDefinitionGroup.Log.ValidationReportWritten", path));
    }
  }

  private void stashModelValidationReport(String formatted) {
    if (Utils.isEmpty(formatted)) {
      return;
    }
    getExtensionDataMap().put(RESULT_ATTR_MODEL_VALIDATION_REPORT, formatted);
    if (getParentWorkflow() != null) {
      getParentWorkflow().getExtensionDataMap().put(RESULT_ATTR_MODEL_VALIDATION_REPORT, formatted);
    }
  }

  private static GroupModelValidationReportFileWriter.ReportFormat parseValidationReportFormat(
      String raw) {
    if (Utils.isEmpty(raw)) {
      return GroupModelValidationReportFileWriter.ReportFormat.MARKDOWN;
    }
    try {
      return GroupModelValidationReportFileWriter.ReportFormat.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return GroupModelValidationReportFileWriter.ReportFormat.MARKDOWN;
    }
  }

  private Result runModelUpdate(
      ModelUpdateJob job,
      String catalogConnection,
      String metricsProfile,
      Result parentResult,
      int nr,
      boolean skipChildModelCheck)
      throws HopException {
    IAction child =
        switch (job.layer()) {
          case DATA_VAULT ->
              configureDataVaultUpdate(
                  job.modelFile(), catalogConnection, metricsProfile, skipChildModelCheck);
          case BUSINESS_VAULT ->
              configureBusinessVaultUpdate(
                  job.modelFile(), catalogConnection, metricsProfile, skipChildModelCheck);
          case DIMENSIONAL ->
              configureDimensionalUpdate(
                  job.modelFile(), catalogConnection, metricsProfile, skipChildModelCheck);
        };
    prepareChildAction(child);
    Result modelResult = new Result();
    modelResult.setResult(true);
    modelResult.setNrErrors(0);
    // Carry forward rows / state where Hop expects it.
    if (parentResult != null && parentResult.getRows() != null) {
      modelResult.setRows(parentResult.getRows());
    }
    return child.execute(modelResult, nr);
  }

  private void prepareChildAction(IAction child) {
    if (child instanceof ActionBase base) {
      // Prefer the parent workflow variable map (same as LocalWorkflowEngine before each action):
      // it holds DV_WORKFLOW_EXECUTION_ID after beginExecution. Fall back to this action's map.
      if (getParentWorkflow() != null) {
        base.copyFrom(getParentWorkflow());
      } else {
        base.copyFrom(this);
      }
      // Ensure correlation vars are present even if copy order or timing was awkward.
      VaultUpdateExecutionSupport.propagateExecutionVariables(this, base);
      if (getParentWorkflow() != null) {
        VaultUpdateExecutionSupport.propagateExecutionVariables(getParentWorkflow(), base);
      }
      base.setParentWorkflow(getParentWorkflow());
      base.setParentWorkflowMeta(getParentWorkflowMeta());
      base.setMetadataProvider(getMetadataProvider());
    }
  }

  private ActionDataVaultUpdate configureDataVaultUpdate(
      String modelFile,
      String catalogConnection,
      String metricsProfile,
      boolean skipChildModelCheck) {
    ActionDataVaultUpdate action = new ActionDataVaultUpdate();
    action.setName("DV " + modelFile);
    action.setDataVaultModelFile(modelFile);
    action.setPipelineRunConfiguration(pipelineRunConfiguration);
    action.setWorkflowRunConfiguration(workflowRunConfiguration);
    action.setParallelPipelineCopies(parallelPipelineCopies);
    action.setLogModelCheckFailures(!skipChildModelCheck && logModelCheckFailures);
    action.setAbortOnModelCheckFailures(!skipChildModelCheck && abortOnModelCheckFailures);
    action.setDetailedDataTypeChecking(detailedDataTypeChecking);
    action.setPreferHarvestForTypeChecks(preferHarvestForTypeChecks);
    action.setHarvestRunId(harvestRunId);
    action.setHarvestHistoryDatabase(harvestHistoryDatabase);
    action.setUpdateTargetDatabaseStructure(updateTargetDatabaseStructure);
    action.setFailIfDdlNeeded(failIfDdlNeeded);
    action.setPublishToCatalog(publishToCatalog && !doNotUpdateTargetDatabase);
    action.setDataCatalogConnection(catalogConnection);
    action.setEnsureSpecialRecords(ensureSpecialRecords && !doNotUpdateTargetDatabase);
    action.setLoadDate(loadDate);
    action.setRecordSourceGroup(recordSourceGroup);
    action.setExecutionMetricsProfile(metricsProfile);
    action.setDoNotUpdateTargetDatabase(doNotUpdateTargetDatabase);
    return action;
  }

  private ActionBusinessVaultUpdate configureBusinessVaultUpdate(
      String modelFile,
      String catalogConnection,
      String metricsProfile,
      boolean skipChildModelCheck) {
    ActionBusinessVaultUpdate action = new ActionBusinessVaultUpdate();
    action.setName("BV " + modelFile);
    action.setBusinessVaultModelFile(modelFile);
    action.setPipelineRunConfiguration(pipelineRunConfiguration);
    action.setWorkflowRunConfiguration(workflowRunConfiguration);
    action.setParallelPipelineCopies(parallelPipelineCopies);
    action.setLogModelCheckFailures(!skipChildModelCheck && logModelCheckFailures);
    action.setAbortOnModelCheckFailures(!skipChildModelCheck && abortOnModelCheckFailures);
    action.setUpdateTargetDatabaseStructure(updateTargetDatabaseStructure);
    action.setFailIfDdlNeeded(failIfDdlNeeded);
    action.setPublishToCatalog(publishToCatalog && !doNotUpdateTargetDatabase);
    action.setDataCatalogConnection(catalogConnection);
    action.setExecutionMetricsProfile(metricsProfile);
    action.setDoNotUpdateTargetDatabase(doNotUpdateTargetDatabase);
    return action;
  }

  private ActionDimensionalUpdate configureDimensionalUpdate(
      String modelFile,
      String catalogConnection,
      String metricsProfile,
      boolean skipChildModelCheck) {
    ActionDimensionalUpdate action = new ActionDimensionalUpdate();
    action.setName("DM " + modelFile);
    action.setDimensionalModelFile(modelFile);
    action.setPipelineRunConfiguration(pipelineRunConfiguration);
    action.setWorkflowRunConfiguration(workflowRunConfiguration);
    action.setParallelPipelineCopies(parallelPipelineCopies);
    action.setLogModelCheckFailures(!skipChildModelCheck && logModelCheckFailures);
    action.setAbortOnModelCheckFailures(!skipChildModelCheck && abortOnModelCheckFailures);
    action.setUpdateTargetDatabaseStructure(updateTargetDatabaseStructure);
    action.setFailIfDdlNeeded(failIfDdlNeeded);
    action.setPublishToCatalog(publishToCatalog && !doNotUpdateTargetDatabase);
    action.setDataCatalogConnection(catalogConnection);
    action.setExecutionMetricsProfile(metricsProfile);
    action.setDoNotUpdateTargetDatabase(doNotUpdateTargetDatabase);
    return action;
  }

  private void publishOverview(
      String executionId, String catalogConnection, String metricsProfile, boolean waveSucceeded)
      throws HopException {
    if (Utils.isEmpty(metricsProfile)) {
      logBasic(
          BaseMessages.getString(PKG, "ActionUpdateResourceDefinitionGroup.Log.NoMetricsProfile"));
      return;
    }
    String resolvedId =
        VaultUpdateExecutionSupport.resolveExecutionId(
            this,
            VaultUpdateExecutionSupport.defaultExecutionIdVariableName(),
            useWorkflowLogChannelId,
            VaultUpdateExecutionSupport.resolveWorkflowLogChannelId(getParentWorkflow()));
    if (Utils.isEmpty(resolvedId)) {
      resolvedId = executionId;
    }
    if (Utils.isEmpty(resolvedId)) {
      if (failIfNoMetricsFound) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "ActionUpdateResourceDefinitionGroup.Error.MissingExecutionId"));
      }
      logBasic(
          BaseMessages.getString(
              PKG, "ActionUpdateResourceDefinitionGroup.Log.NoExecutionIdForOverview"));
      return;
    }

    WorkflowOverviewMetricsResolver.ResolvedOverviewMetrics settings =
        WorkflowOverviewMetricsResolver.resolve(
            metricsProfile, catalogConnection, this, getMetadataProvider());
    DatabaseMeta databaseMeta =
        getMetadataProvider().getSerializer(DatabaseMeta.class).load(settings.targetDatabaseName());
    if (databaseMeta == null) {
      String message =
          BaseMessages.getString(
              PKG,
              "ActionUpdateResourceDefinitionGroup.Error.MissingMetricsDatabase",
              settings.targetDatabaseName());
      if (failIfNoMetricsFound) {
        throw new HopException(message);
      }
      logBasic(message);
      return;
    }

    Date startedAt = VaultUpdateExecutionSupport.resolveStartedAt(this, null);
    WorkflowLoadOverviewReport report =
        WorkflowLoadOverviewLoader.load(
            databaseMeta,
            settings.operationsSchema(),
            resolvedId,
            resolveRootWorkflowName(),
            startedAt,
            this,
            includePipelineDetail,
            includeInsights,
            WorkflowLoadOverviewReport.DEFAULT_MAX_PIPELINES_PER_MODEL);

    if (report == null) {
      String message =
          BaseMessages.getString(
              PKG, "ActionUpdateResourceDefinitionGroup.Error.NoMetricsFound", resolvedId);
      if (failIfNoMetricsFound) {
        throw new HopException(message);
      }
      logBasic(message);
      return;
    }

    if (publishMetricsToDatabase || publishMetricsToCatalog) {
      WorkflowLoadOverviewPublisher.publish(
          null,
          settings,
          report,
          publishMetricsToCatalog,
          publishMetricsToDatabase,
          this,
          getMetadataProvider());
    }

    if (logMetricsToWorkflow) {
      String formatted =
          WorkflowLoadOverviewReportFormatter.formatLog(
              report, includePipelineDetail, includeInsights);
      if (!Utils.isEmpty(formatted)) {
        logBasic(formatted);
      }
    }

    if (writeMarkdownReport) {
      String path =
          WorkflowLoadOverviewFileWriter.writeMarkdown(
              reportOutputFolder,
              reportFileBaseName,
              report,
              includePipelineDetail,
              includeInsights,
              this);
      if (!Utils.isEmpty(path)) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionUpdateResourceDefinitionGroup.Log.MarkdownWritten", path));
      }
    }
    if (writeHtmlReport) {
      String path =
          WorkflowLoadOverviewFileWriter.writeHtml(
              reportOutputFolder,
              reportFileBaseName,
              report,
              includePipelineDetail,
              includeInsights,
              this);
      if (!Utils.isEmpty(path)) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionUpdateResourceDefinitionGroup.Log.HtmlWritten", path));
      }
    }

    if (!waveSucceeded) {
      logBasic(
          BaseMessages.getString(
              PKG, "ActionUpdateResourceDefinitionGroup.Log.OverviewAfterFailure", resolvedId));
    }
  }

  private String resolveCatalogConnection(ResourceDefinitionGroupMeta group) {
    String fromAction = resolve(Const.NVL(dataCatalogConnection, ""));
    if (!Utils.isEmpty(fromAction)) {
      return fromAction;
    }
    if (group != null && !Utils.isEmpty(group.getDataCatalogConnection())) {
      return resolve(group.getDataCatalogConnection());
    }
    return null;
  }

  private String resolveRootWorkflowName() {
    if (getParentWorkflow() != null && getParentWorkflow().getWorkflowMeta() != null) {
      return getParentWorkflow().getWorkflowMeta().getName();
    }
    if (getParentWorkflowMeta() != null) {
      return getParentWorkflowMeta().getName();
    }
    return getName();
  }

  private static void mergeResult(Result into, Result from) {
    if (into == null || from == null) {
      return;
    }
    into.setNrErrors(into.getNrErrors() + from.getNrErrors());
    into.setNrLinesRead(into.getNrLinesRead() + from.getNrLinesRead());
    into.setNrLinesWritten(into.getNrLinesWritten() + from.getNrLinesWritten());
    into.setNrLinesInput(into.getNrLinesInput() + from.getNrLinesInput());
    into.setNrLinesOutput(into.getNrLinesOutput() + from.getNrLinesOutput());
    into.setNrLinesUpdated(into.getNrLinesUpdated() + from.getNrLinesUpdated());
    into.setNrLinesRejected(into.getNrLinesRejected() + from.getNrLinesRejected());
    if (!Utils.isEmpty(from.getLogText())) {
      into.setLogText(Const.NVL(into.getLogText(), "") + from.getLogText());
    }
  }

  /**
   * Extract a short, high-signal summary of failure lines from a child Result log so group-level
   * "ModelFailed" is not a one-liner with the real cause buried earlier.
   */
  static String summarizeResultErrors(Result result) {
    if (result == null || Utils.isEmpty(result.getLogText())) {
      return "";
    }
    String[] lines = result.getLogText().split("\\R");
    List<String> interesting = new ArrayList<>();
    for (String line : lines) {
      if (Utils.isEmpty(line)) {
        continue;
      }
      String trimmed = line.trim();
      String lower = trimmed.toLowerCase();
      if (lower.contains("error")
          || lower.contains("exception")
          || lower.contains("failed")
          || lower.contains("please specify")
          || lower.startsWith("org.apache.hop")) {
        interesting.add(trimmed);
      }
    }
    if (interesting.isEmpty()) {
      // Fall back to the last non-empty lines of the child log.
      for (int i = lines.length - 1; i >= 0 && interesting.size() < 5; i--) {
        if (!Utils.isEmpty(lines[i])) {
          interesting.add(0, lines[i].trim());
        }
      }
    }
    int maxLines = 8;
    if (interesting.size() > maxLines) {
      interesting = interesting.subList(interesting.size() - maxLines, interesting.size());
    }
    return String.join(" | ", interesting);
  }

  @Override
  public IAction clone() {
    return new ActionUpdateResourceDefinitionGroup(this);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  @Override
  public boolean isUnconditional() {
    return false;
  }

  @Override
  public boolean supportsDrillDown() {
    return true;
  }

  /**
   * GUI labels for models listed on the resource definition group. Paths are shown project-relative
   * without a {@code ${PROJECT_HOME}/} prefix (e.g. {@code models/retail-360.hdv}).
   */
  @Override
  public String[] getReferencedObjectDescriptions() {
    try {
      IVariables vars = effectiveReferencedObjectVariables(null);
      List<ReferencedModel> models = listReferencedModels(metadataProviderForGui(), vars);
      String[] descriptions = new String[models.size()];
      for (int i = 0; i < models.size(); i++) {
        ReferencedModel model = models.get(i);
        descriptions[i] =
            BaseMessages.getString(
                PKG, model.descriptionKey(), toDisplayRelativePath(model.storedPath(), vars));
      }
      return descriptions;
    } catch (Exception e) {
      LogChannel.GENERAL.logError("Error getting reference descriptions", e);
      return super.getReferencedObjectDescriptions();
    }
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    try {
      IVariables vars = effectiveReferencedObjectVariables(null);
      List<ReferencedModel> models = listReferencedModels(metadataProviderForGui(), vars);
      boolean[] enabled = new boolean[models.size()];
      for (int i = 0; i < models.size(); i++) {
        enabled[i] = StringUtils.isNotEmpty(models.get(i).storedPath());
      }
      return enabled;
    } catch (Exception e) {
      LogChannel.GENERAL.logError("Error checking referenced objects", e);
      return super.isReferencedObjectEnabled();
    }
  }

  /**
   * Opens the model file at {@code index} (same order as {@link
   * #getReferencedObjectDescriptions()}). Returns the stored path (often with {@code
   * ${PROJECT_HOME}}); Hop resolves variables when opening.
   */
  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    try {
      IVariables vars = effectiveReferencedObjectVariables(variables);
      IHopMetadataProvider provider =
          metadataProvider != null ? metadataProvider : metadataProviderForGui();
      List<ReferencedModel> models = listReferencedModels(provider, vars);
      if (index < 0 || index >= models.size()) {
        return super.loadReferencedObject(index, metadataProvider, variables);
      }
      String storedPath = models.get(index).storedPath();
      return () -> storedPath;
    } catch (Exception e) {
      LogChannel.GENERAL.logError("Error loading referenced object", e);
      return super.loadReferencedObject(index, metadataProvider, variables);
    }
  }

  private IVariables effectiveReferencedObjectVariables(IVariables preferred) {
    return WorkflowReferencedObjectVariableSupport.effectiveVariables(
        this, getParentWorkflowMeta(), preferred != null ? preferred : this);
  }

  private static IHopMetadataProvider metadataProviderForGui() {
    try {
      return HopGui.getInstance().getMetadataProvider();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Ordered model files from the configured resource definition group (DV, then BV, then DM), using
   * the same list order as group metadata. Empty paths are skipped.
   */
  private List<ReferencedModel> listReferencedModels(
      IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    String groupName =
        variables != null
            ? variables.resolve(Const.NVL(resourceDefinitionGroup, ""))
            : Const.NVL(resourceDefinitionGroup, "");
    if (Utils.isEmpty(groupName) || metadataProvider == null) {
      return List.of();
    }
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(groupName, metadataProvider);
    List<ReferencedModel> models = new ArrayList<>();
    appendReferenced(
        models,
        group.getDataVaultModelFiles(),
        "ActionUpdateResourceDefinitionGroup.ReferencedObject.DataVault");
    appendReferenced(
        models,
        group.getBusinessVaultModelFiles(),
        "ActionUpdateResourceDefinitionGroup.ReferencedObject.BusinessVault");
    appendReferenced(
        models,
        group.getDimensionalModelFiles(),
        "ActionUpdateResourceDefinitionGroup.ReferencedObject.Dimensional");
    return models;
  }

  private static void appendReferenced(
      List<ReferencedModel> models, List<String> files, String descriptionKey) {
    if (files == null) {
      return;
    }
    for (String file : files) {
      if (StringUtils.isNotEmpty(file)) {
        models.add(new ReferencedModel(file.trim(), descriptionKey));
      }
    }
  }

  /**
   * Human-readable project-relative path for GUI menus: strips a leading {@code ${PROJECT_HOME}/}
   * (or resolved absolute project home). Does not rewrite paths for loading.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code ${PROJECT_HOME}/models/retail-360.hdv} → {@code models/retail-360.hdv}
   *   <li>{@code /proj/models/x.hdv} with {@code PROJECT_HOME=/proj} → {@code models/x.hdv}
   *   <li>paths outside the project are left unchanged (or basename if absolute under another root)
   * </ul>
   */
  static String toDisplayRelativePath(String storedOrRawPath, IVariables variables) {
    if (Utils.isEmpty(storedOrRawPath)) {
      return "";
    }
    String path = storedOrRawPath.trim().replace('\\', '/');
    final String projectHomeToken =
        "${" + WorkflowReferencedObjectVariableSupport.VARIABLE_PROJECT_HOME + "}";
    final String projectHomePrefix = projectHomeToken + "/";

    if (path.startsWith(projectHomePrefix)) {
      return path.substring(projectHomePrefix.length());
    }
    if (path.equals(projectHomeToken) || path.equals(projectHomeToken + "/")) {
      return ".";
    }

    // Already project-relative without variable (models/foo.hdv)
    if (!path.startsWith("/") && !path.matches("^[A-Za-z]:/.*") && !path.contains("${")) {
      return path;
    }

    if (variables == null) {
      return stripResolvedProjectHome(path, null);
    }
    String resolvedPath = variables.resolve(path).replace('\\', '/');
    String projectHome = variables.resolve(projectHomeToken);
    if (Utils.isEmpty(projectHome) || projectHome.contains("${")) {
      return resolvedPath.contains("/")
          ? resolvedPath.substring(resolvedPath.lastIndexOf('/') + 1)
          : resolvedPath;
    }
    return stripResolvedProjectHome(resolvedPath, projectHome.replace('\\', '/'));
  }

  private static String stripResolvedProjectHome(String resolvedPath, String projectHome) {
    if (Utils.isEmpty(resolvedPath)) {
      return "";
    }
    if (Utils.isEmpty(projectHome)) {
      return resolvedPath;
    }
    try {
      Path home = Path.of(projectHome).toAbsolutePath().normalize();
      Path file = Path.of(resolvedPath).toAbsolutePath().normalize();
      if (file.startsWith(home)) {
        String relative = home.relativize(file).toString().replace('\\', '/');
        return Utils.isEmpty(relative) ? "." : relative;
      }
    } catch (Exception ignored) {
      // fall through
    }
    String home = projectHome.endsWith("/") ? projectHome : projectHome + "/";
    if (resolvedPath.startsWith(home)) {
      return resolvedPath.substring(home.length());
    }
    return resolvedPath;
  }

  /** One model file listed on the resource definition group (open-referenced-object entry). */
  record ReferencedModel(String storedPath, String descriptionKey) {}
}
