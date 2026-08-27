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
package org.hopper.edw.datavault.workflow.actions.datavaultupdate;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.config.WorkflowRunConfiguration;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.datavault.catalog.DvCatalogPublisher;
import org.hopper.edw.datavault.config.DvRunConfigurationSupport;
import org.hopper.edw.datavault.lineage.DdlLineageExplainSupport;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvDdlSupport;
import org.hopper.edw.datavault.metadata.DvGeneratedPipelineSupport;
import org.hopper.edw.datavault.metadata.DvIntegerSettingValidationSupport;
import org.hopper.edw.datavault.metadata.DvIntegrationSupport;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvLoadDateSupport;
import org.hopper.edw.datavault.metadata.DvModelBulkUpdateExecutionSupport;
import org.hopper.edw.datavault.metadata.DvModelCheckOptions;
import org.hopper.edw.datavault.metadata.DvMultiSourceUpdateWorkflowSupport;
import org.hopper.edw.datavault.metadata.DvOrphanHandlingSupport;
import org.hopper.edw.datavault.metadata.DvPipelineOrchestratorSupport;
import org.hopper.edw.datavault.metadata.DvReadOnlyExistingVaultSupport;
import org.hopper.edw.datavault.metadata.DvSpecialRecordSupport;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.DvTargetLoadMode;
import org.hopper.edw.datavault.metadata.DvUpdateExecutionSupport;
import org.hopper.edw.datavault.metadata.DvUpdateWorkflowSupport;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingMeta;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingSupport;
import org.hopper.edw.datavault.metrics.ExecutionMetricsProfileResolver;
import org.hopper.edw.datavault.metrics.ResolvedExecutionMetrics;
import org.hopper.edw.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** This defines a Data Vault Update action. */
@Action(
    id = "DATA_VAULT_UPDATE",
    name = "i18n::ActionDataVaultUpdate.Name",
    description = "i18n::ActionDataVaultUpdate.Description",
    image = "datavault-model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionDataVaultUpdate.Keywords",
    documentationUrl = "/workflow/actions/datavaultupdate.html")
@GuiPlugin(description = "Data Vault Update action")
@Getter
@Setter
public class ActionDataVaultUpdate extends ActionBase implements Cloneable, IAction {
  private static final Class<?> PKG = ActionDataVaultUpdate.class;

  /** Root dialog identifier (action name field lives outside the tab folder). */
  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_UPDATE_ACTION";

  public static final String GUI_PLUGIN_ELEMENT_MODEL_TAB_ID = "DATAVAULT_UPDATE_ACTION_MODEL_TAB";

  public static final String GUI_PLUGIN_ELEMENT_DDL_TAB_ID = "DATAVAULT_UPDATE_ACTION_DDL_TAB";

  public static final String GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID =
      "DATAVAULT_UPDATE_ACTION_SOURCE_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDataVaultUpdate.DataVaultModel.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DataVaultModel.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String dataVaultModelFile;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = PipelineRunConfiguration.class,
      label = "i18n::ActionDataVaultUpdate.PipelineRunConfiguration.Label",
      toolTip = "i18n::ActionDataVaultUpdate.PipelineRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String pipelineRunConfiguration;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.METADATA,
      metadata = WorkflowRunConfiguration.class,
      label = "i18n::ActionDataVaultUpdate.WorkflowRunConfiguration.Label",
      toolTip = "i18n::ActionDataVaultUpdate.WorkflowRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String workflowRunConfiguration;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.LogModelCheckFailures.Label",
      toolTip = "i18n::ActionDataVaultUpdate.LogModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean logModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.AbortOnModelCheckFailures.Label",
      toolTip = "i18n::ActionDataVaultUpdate.AbortOnModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean abortOnModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0450",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.DetailedDataTypeChecking.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DetailedDataTypeChecking.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean detailedDataTypeChecking = true;

  /**
   * Prefer DISCOVERED field layouts from a schema harvest for detailed type checking (skips live
   * JDBC when harvest data is available).
   */
  @GuiWidgetElement(
      order = "0455",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.PreferHarvestForTypeChecks.Label",
      toolTip = "i18n::ActionDataVaultUpdate.PreferHarvestForTypeChecks.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean preferHarvestForTypeChecks = true;

  @GuiWidgetElement(
      order = "0456",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.HarvestRunId.Label",
      toolTip = "i18n::ActionDataVaultUpdate.HarvestRunId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String harvestRunId =
      "${"
          + org.hopper.edw.catalog.harvest.history.SchemaHarvestHistoryPublisher
              .VAR_SCHEMA_HARVEST_RUN_ID
          + "}";

  @GuiWidgetElement(
      order = "0457",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::ActionDataVaultUpdate.HarvestHistoryDatabase.Label",
      toolTip = "i18n::ActionDataVaultUpdate.HarvestHistoryDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String harvestHistoryDatabase;

  @GuiWidgetElement(
      order = "0350",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.ParallelPipelineCopies.Label",
      toolTip = "i18n::ActionDataVaultUpdate.ParallelPipelineCopies.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String parallelPipelineCopies = "1";

  @GuiWidgetElement(
      order = "0360",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.PipelineStagingFolder.Label",
      toolTip = "i18n::ActionDataVaultUpdate.PipelineStagingFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String pipelineStagingFolder;

  @GuiWidgetElement(
      order = "0365",
      type = GuiElementType.METADATA,
      metadata = ExecutionMetricsProfileMeta.class,
      label = "i18n::ActionDataVaultUpdate.ExecutionMetricsProfile.Label",
      toolTip = "i18n::ActionDataVaultUpdate.ExecutionMetricsProfile.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String executionMetricsProfile;

  @HopMetadataProperty private String metricsOutputFolder;

  @GuiWidgetElement(
      order = "0380",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.PublishToCatalog.Label",
      toolTip = "i18n::ActionDataVaultUpdate.PublishToCatalog.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean publishToCatalog;

  @GuiWidgetElement(
      order = "0385",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::ActionDataVaultUpdate.DataCatalogConnection.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DataCatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String dataCatalogConnection;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.UpdateTargetStructure.Label",
      toolTip = "i18n::ActionDataVaultUpdate.UpdateTargetStructure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private boolean updateTargetDatabaseStructure = true;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.METADATA,
      metadata = TargetTypeMappingMeta.class,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.TargetTypeMapping.Label",
      toolTip = "i18n::ActionDataVaultUpdate.TargetTypeMapping.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private String targetTypeMapping;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDataVaultUpdate.DdlSqlFilename.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DdlSqlFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private String ddlSqlFilename;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.FailIfDdlNeeded.Label",
      toolTip = "i18n::ActionDataVaultUpdate.FailIfDdlNeeded.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private boolean failIfDdlNeeded;

  @GuiWidgetElement(
      order = "0050",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.LoadDate.Label",
      toolTip = "i18n::ActionDataVaultUpdate.LoadDate.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private String loadDate;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionDataVaultUpdate.RecordSourceGroup.Label",
      toolTip = "i18n::ActionDataVaultUpdate.RecordSourceGroup.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private String recordSourceGroup;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.EnsureSpecialRecords.Label",
      toolTip = "i18n::ActionDataVaultUpdate.EnsureSpecialRecords.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private boolean ensureSpecialRecords = true;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDataVaultUpdate.DoNotUpdateTargetDatabase.Label",
      toolTip = "i18n::ActionDataVaultUpdate.DoNotUpdateTargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_SOURCE_TAB_ID)
  @HopMetadataProperty
  private boolean doNotUpdateTargetDatabase;

  public ActionDataVaultUpdate(String name) {
    super(name, "");
    dataVaultModelFile = null;
    pipelineRunConfiguration = null;
    workflowRunConfiguration = null;
  }

  public ActionDataVaultUpdate() {
    this("");
  }

  public ActionDataVaultUpdate(ActionDataVaultUpdate meta) {
    super(meta);
    this.dataVaultModelFile = meta.dataVaultModelFile;
    this.pipelineRunConfiguration = meta.pipelineRunConfiguration;
    this.workflowRunConfiguration = meta.workflowRunConfiguration;
    this.logModelCheckFailures = meta.logModelCheckFailures;
    this.abortOnModelCheckFailures = meta.abortOnModelCheckFailures;
    this.detailedDataTypeChecking = meta.detailedDataTypeChecking;
    this.preferHarvestForTypeChecks = meta.preferHarvestForTypeChecks;
    this.harvestRunId = meta.harvestRunId;
    this.harvestHistoryDatabase = meta.harvestHistoryDatabase;
    this.updateTargetDatabaseStructure = meta.updateTargetDatabaseStructure;
    this.targetTypeMapping = meta.targetTypeMapping;
    this.ddlSqlFilename = meta.ddlSqlFilename;
    this.failIfDdlNeeded = meta.failIfDdlNeeded;
    this.ensureSpecialRecords = meta.ensureSpecialRecords;
    this.doNotUpdateTargetDatabase = meta.doNotUpdateTargetDatabase;
    this.recordSourceGroup = meta.recordSourceGroup;
    this.loadDate = meta.loadDate;
    this.parallelPipelineCopies = meta.parallelPipelineCopies;
    this.pipelineStagingFolder = meta.pipelineStagingFolder;
    this.executionMetricsProfile = meta.executionMetricsProfile;
    this.metricsOutputFolder = meta.metricsOutputFolder;
    this.publishToCatalog = meta.publishToCatalog;
    this.dataCatalogConnection = meta.dataCatalogConnection;
  }

  @Override
  public ActionDataVaultUpdate clone() {
    return new ActionDataVaultUpdate(this);
  }

  @Override
  public String getDialogClassName() {
    return ActionDataVaultUpdateDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    try {

      String realRunConfig =
          DvRunConfigurationSupport.resolvePipelineRunConfiguration(
              pipelineRunConfiguration, getVariables());
      String realWorkflowRunConfig =
          DvRunConfigurationSupport.resolveWorkflowRunConfiguration(
              workflowRunConfiguration, getVariables());
      if (Utils.isEmpty(realRunConfig)) {
        logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.NoRunConfig"));
        return result;
      }

      DataVaultModel model = loadReferencedDataModel(getMetadataProvider(), this);
      if (DvReadOnlyExistingVaultSupport.isReadOnly(model)) {
        logError(DvReadOnlyExistingVaultSupport.refuseUpdateMessage());
        return result;
      }

      // Perform model check if requested
      if (logModelCheckFailures || abortOnModelCheckFailures) {
        DvModelCheckOptions checkOptions = DvModelCheckOptions.forCheckRun();
        checkOptions.setDetailedDataTypeChecking(detailedDataTypeChecking);
        checkOptions.setPreferHarvestForLiveFields(preferHarvestForTypeChecks);
        checkOptions.setHarvestRunId(harvestRunId);
        checkOptions.setHarvestHistoryDatabase(harvestHistoryDatabase);
        checkOptions.setHarvestCatalogConnection(dataCatalogConnection);
        org.hopper.edw.catalog.harvest.SchemaHarvestModelCheckSupport.warmCacheIfPreferred(
            checkOptions, this, getMetadataProvider(), getLogChannel());
        // model.check closes the options session (including cache) when not shared.
        List<ICheckResult> remarks = model.check(getMetadataProvider(), this, checkOptions);
        for (ICheckResult remark : remarks) {
          String message =
              BaseMessages.getString(
                  PKG,
                  "ActionDataVaultUpdate.Log.ModelCheckResult",
                  remark.getTypeDesc(),
                  remark.getText());
          if (remark.getType() == ICheckResult.TYPE_RESULT_ERROR) {
            logError(message);
          } else if (remark.getType() == ICheckResult.TYPE_RESULT_WARNING) {
            logBasic(message);
          }
        }

        if (abortOnModelCheckFailures) {
          boolean hasError =
              remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
          if (hasError) {
            logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.AbortingOnModelCheck"));
            result.setResult(false);
            result.setNrErrors(1);
            return result;
          }
        }
      }

      List<IDvTable> tables = model.getTables();
      if (tables == null || tables.isEmpty()) {
        logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.NoTables"));
        return finishExecution(result, true, 0, model);
      }

      Date batchLoadDate = DvLoadDateSupport.resolveLoadDate(loadDate, getVariables());
      if (DvLoadDateSupport.isConfigured(loadDate, getVariables())) {
        ValueMetaDate loadDateMeta = new ValueMetaDate("loadDate");
        loadDateMeta.setConversionMask(DvLoadDateSupport.LOAD_DATE_FORMAT_MASK);
        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionDataVaultUpdate.Log.UsingLoadDate",
                loadDateMeta.getString(batchLoadDate)));
      } else {
        logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.UsingCurrentLoadDate"));
      }

      DataVaultConfiguration configuration = model.getConfigurationOrDefault();
      if (configuration.isStoreLoadCycleId()) {
        DatabaseMeta loadCycleDatabase =
            DvSpecialRecordSupport.loadTargetDatabase(getMetadataProvider(), configuration);
        if (loadCycleDatabase == null) {
          throw new HopException(
              BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.LoadCycleTargetMissing"));
        }
        ILoggingObject loadCycleLog =
            new SimpleLoggingObject("ActionDataVaultUpdate", LoggingObjectType.GENERAL, null);
        long cycleId =
            DvLoadCycleSupport.allocateNext(
                loadCycleDatabase,
                getVariables(),
                loadCycleLog,
                configuration.getLoadCycleControlTable());
        if (getParentWorkflow() != null) {
          DvLoadCycleSupport.setCycleIdVariable(getParentWorkflow(), cycleId);
        }
        logBasic(
            BaseMessages.getString(
                PKG, "ActionDataVaultUpdate.Log.UsingLoadCycleId", Long.toString(cycleId)));
      }

      boolean success = true;
      int totalErrors = 0;
      String realRecordSourceGroup = resolve(recordSourceGroup);

      if (!Utils.isEmpty(realRecordSourceGroup)) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionDataVaultUpdate.Log.UsingRecordSourceGroup", realRecordSourceGroup));
      }

      String realDdlSqlFilename = resolve(ddlSqlFilename);
      TargetTypeMappingSupport.applyExplicitName(getVariables(), targetTypeMapping);
      boolean processDdl =
          updateTargetDatabaseStructure || failIfDdlNeeded || !Utils.isEmpty(realDdlSqlFilename);

      if (processDdl) {
        boolean ddlPhaseFailed = false;
        List<String> ddlStatements = new ArrayList<>();
        for (IDvTable table : tables) {
          try {
            for (String ddl :
                table.generateUpdateDdl(getMetadataProvider(), getVariables(), model)) {
              if (!Utils.isEmpty(ddl)) {
                ddlStatements.add(ddl);
              }
            }
          } catch (Exception ex) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionDataVaultUpdate.Error.DdlGenerateFailed", table.getName()),
                ex);
            totalErrors++;
            success = false;
            ddlPhaseFailed = true;
          }
        }

        ddlStatements = DvDdlSupport.deduplicateCreateTableDdl(ddlStatements);

        if (hasDdlStatements(ddlStatements)) {
          // Always explain structural changes with source-to-target lineage (VaultSpeed-style
          // opacity guard).
          try {
            String explanation =
                DdlLineageExplainSupport.explain(
                    ddlStatements, model, getVariables(), getMetadataProvider());
            if (!Utils.isEmpty(explanation)) {
              if (failIfDdlNeeded) {
                logError(explanation);
              } else {
                logBasic(explanation);
              }
            }
          } catch (Exception lineageEx) {
            logError(
                BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.LineageExplainFailed"),
                lineageEx);
          }

          if (failIfDdlNeeded) {
            logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.AbortingOnDdlNeeded"));
            result.setResult(false);
            result.setNrErrors(1);
            return result;
          }

          DatabaseMeta targetDatabase =
              DvSpecialRecordSupport.loadTargetDatabase(
                  getMetadataProvider(), model.getConfigurationOrDefault());

          if (!Utils.isEmpty(realDdlSqlFilename)) {
            try {
              writeDdlToFile(ddlStatements, targetDatabase, realDdlSqlFilename);
              logBasic(
                  BaseMessages.getString(
                      PKG, "ActionDataVaultUpdate.Log.DdlSavedToFile", realDdlSqlFilename));
            } catch (Exception e) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionDataVaultUpdate.Error.DdlSaveFailed", realDdlSqlFilename),
                  e);
              totalErrors++;
              success = false;
              ddlPhaseFailed = true;
            }
          }

          if (updateTargetDatabaseStructure) {
            if (targetDatabase == null) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionDataVaultUpdate.Error.DdlExecutionFailed", ""));
              totalErrors++;
              success = false;
              ddlPhaseFailed = true;
            } else {
              ILoggingObject loggingObject =
                  new SimpleLoggingObject("ActionDataVaultUpdate", LoggingObjectType.GENERAL, null);
              try (Database db = new Database(loggingObject, getVariables(), targetDatabase)) {
                db.connect();
                Set<String> createdInBatch = new HashSet<>();
                for (String ddl : ddlStatements) {
                  if (Utils.isEmpty(ddl)) {
                    continue;
                  }
                  if (DvDdlSupport.shouldSkipCreateTable(
                      db, getVariables(), targetDatabase, ddl, createdInBatch)) {
                    String tableName = DvDdlSupport.extractCreateTableName(ddl);
                    logBasic(
                        BaseMessages.getString(
                            PKG,
                            "ActionDataVaultUpdate.Log.SkippingCreateTable",
                            tableName != null ? tableName : ddl.trim()));
                    if (!Utils.isEmpty(tableName)) {
                      createdInBatch.add(tableName.toLowerCase(Locale.ROOT));
                    }
                    continue;
                  }
                  logBasic(
                      BaseMessages.getString(
                          PKG, "ActionDataVaultUpdate.Log.ExecutingDdl", targetDatabase.getName()));
                  db.execStatements(ddl);
                  String tableName = DvDdlSupport.extractCreateTableName(ddl);
                  if (!Utils.isEmpty(tableName)) {
                    createdInBatch.add(tableName.toLowerCase(Locale.ROOT));
                  }
                }
              } catch (Exception e) {
                logError(
                    BaseMessages.getString(
                        PKG,
                        "ActionDataVaultUpdate.Error.DdlExecutionFailed",
                        targetDatabase.getName()),
                    e);
                totalErrors++;
                success = false;
                ddlPhaseFailed = true;
              }
            }
          } else {
            logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.SkippingDdlExecution"));
          }
        } else {
          logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.NoDdlNeeded"));
        }

        if (updateTargetDatabaseStructure && ddlPhaseFailed) {
          logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.AbortingOnDdlFailure"));
          return finishExecution(result, false, Math.max(totalErrors, 1), model);
        }
      }

      if (doNotUpdateTargetDatabase) {
        logBasic(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.SkippingDataUpdate"));
        return finishExecution(result, success, totalErrors, model);
      }

      if (DvOrphanHandlingSupport.modelUsesQuarantine(model)) {
        try {
          DatabaseMeta targetDatabase =
              DvSpecialRecordSupport.loadTargetDatabase(
                  getMetadataProvider(), model.getConfigurationOrDefault());
          DvOrphanHandlingSupport.ensureQuarantineTable(
              targetDatabase,
              getVariables(),
              this,
              model.getConfigurationOrDefault().resolveQuarantineTableName(getVariables()));
        } catch (Exception e) {
          logError("Unable to ensure orphan quarantine table", e);
          totalErrors++;
          success = false;
        }
      }

      if (ensureSpecialRecords) {
        ILoggingObject specialRecordsLoggingObject =
            new SimpleLoggingObject(
                "ActionDataVaultUpdate.ensureSpecialRecords", LoggingObjectType.GENERAL, null);
        int totalInserted = 0;
        for (DvTableType tableType : new DvTableType[] {DvTableType.HUB, DvTableType.LINK}) {
          for (IDvTable table : tables) {
            if (table.getTableType() != tableType) {
              continue;
            }
            if (DvIntegrationSupport.shouldSkipSentinelRows(table)) {
              logBasic(
                  BaseMessages.getString(
                      PKG,
                      "ActionDataVaultUpdate.Log.SkippingSpecialRecordsExternal",
                      table.getName()));
              continue;
            }
            try {
              int inserted =
                  table.ensureSpecialRecords(
                      getMetadataProvider(),
                      getVariables(),
                      model,
                      batchLoadDate,
                      specialRecordsLoggingObject);
              totalInserted += inserted;
              if (inserted > 0) {
                logBasic(
                    BaseMessages.getString(
                        PKG,
                        "ActionDataVaultUpdate.Log.SpecialRecordsInserted",
                        table.getName(),
                        inserted));
              }
            } catch (Exception ex) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionDataVaultUpdate.Error.SpecialRecordsFailed", table.getName()),
                  ex);
              totalErrors++;
              success = false;
            }
          }
        }
        if (totalInserted > 0) {
          logBasic(
              BaseMessages.getString(
                  PKG, "ActionDataVaultUpdate.Log.SpecialRecordsTotal", totalInserted));
        }
      }

      DataVaultConfiguration pipelineConfig = model.getConfigurationOrDefault();
      try {
        validatePipelineIntegerSettings(pipelineConfig);
      } catch (HopException e) {
        logError(e.getMessage());
        result.setResult(false);
        result.setNrErrors(1);
        return result;
      }

      LogLevel pipelineLogLevel = pipelineConfig.resolveExecutionLogLevel();
      DvUpdateExecutionSupport.FreePipelineBuckets freePipelines =
          new DvUpdateExecutionSupport.FreePipelineBuckets();
      List<MultiSourceUpdateUnit> multiSourceUnits = new ArrayList<>();

      for (IDvTable table : DvUpdateExecutionSupport.orderTablesForPipelineExecution(tables)) {
        if (DvIntegrationSupport.shouldSkipUpdatePipeline(table)) {
          logBasic(
              BaseMessages.getString(
                  PKG,
                  DvIntegrationSupport.isLinkedTable(table)
                      ? "ActionDataVaultUpdate.Log.SkippingLinkedTable"
                      : "ActionDataVaultUpdate.Log.SkippingExternalTable",
                  table.getName()));
          continue;
        }

        if (DvIntegrationSupport.isCustomPipelines(table)) {
          logBasic(
              BaseMessages.getString(
                  PKG, "ActionDataVaultUpdate.Log.LoadingCustomPipelines", table.getName()));
        } else {
          logBasic(
              BaseMessages.getString(
                  PKG, "ActionDataVaultUpdate.Log.GeneratingForTable", table.getName()));
        }

        List<PipelineMeta> pipelineMetas;
        List<WorkflowMeta> workflowMetas;
        try {
          pipelineMetas =
              table.generateUpdatePipelines(
                  getMetadataProvider(),
                  getVariables(),
                  model,
                  batchLoadDate,
                  realRecordSourceGroup);
          workflowMetas =
              table.generateUpdateWorkflows(
                  getMetadataProvider(),
                  getVariables(),
                  model,
                  batchLoadDate,
                  realRecordSourceGroup);
        } catch (HopException e) {
          logError(
              BaseMessages.getString(
                  PKG, "ActionDataVaultUpdate.Error.GenerateFailed", table.getName()),
              e);
          totalErrors++;
          success = false;
          continue;
        }

        if (pipelineMetas == null) {
          pipelineMetas = List.of();
        }
        if (workflowMetas == null) {
          workflowMetas = List.of();
        }

        if (pipelineMetas.isEmpty() && workflowMetas.isEmpty()) {
          if (!Utils.isEmpty(realRecordSourceGroup)) {
            logBasic(
                BaseMessages.getString(
                    PKG,
                    "ActionDataVaultUpdate.Log.SkippingTableForGroup",
                    table.getName(),
                    realRecordSourceGroup));
            continue;
          }
          if (DvIntegrationSupport.isCustomPipelines(table)) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionDataVaultUpdate.Error.CustomPipelinesMissing", table.getName()));
          } else {
            logError(
                BaseMessages.getString(
                    PKG, "ActionDataVaultUpdate.Error.GenerateFailed", table.getName()),
                new HopException(
                    BaseMessages.getString(
                        PKG, "ActionDataVaultUpdate.Error.GenerateFailedEmpty", table.getName())));
          }
          totalErrors++;
          success = false;
          continue;
        }

        for (PipelineMeta pipelineMeta : pipelineMetas) {
          if (pipelineMeta != null) {
            pipelineMeta.lookupReferencesAfterLoading();
            String savedPipelineFile =
                DvGeneratedPipelineSupport.saveBeforeExecution(
                    pipelineConfig, getVariables(), pipelineMeta);
            if (!Utils.isEmpty(savedPipelineFile)) {
              logBasic(
                  BaseMessages.getString(
                      PKG,
                      "ActionDataVaultUpdate.Log.SavedGeneratedPipeline",
                      pipelineMeta.getName(),
                      savedPipelineFile));
            }
          }
        }

        DvMultiSourceUpdateWorkflowSupport.UpdateArtifacts artifacts =
            DvMultiSourceUpdateWorkflowSupport.UpdateArtifacts.of(pipelineMetas, workflowMetas);
        freePipelines.add(table.getTableType(), artifacts.freePipelines());

        if (!artifacts.multiSourceWorkflows().isEmpty()) {
          logBasic(
              BaseMessages.getString(
                  PKG,
                  "ActionDataVaultUpdate.Log.MultiSourceSerialWorkflow",
                  table.getName(),
                  artifacts.nestedPipelines().size()));
          for (WorkflowMeta workflowMeta : artifacts.multiSourceWorkflows()) {
            if (workflowMeta == null) {
              continue;
            }
            multiSourceUnits.add(
                new MultiSourceUpdateUnit(
                    table.getName(), workflowMeta, artifacts.nestedPipelines()));
            String savedWorkflowFile =
                DvGeneratedPipelineSupport.saveWorkflowBeforeExecution(
                    pipelineConfig, getVariables(), workflowMeta);
            if (!Utils.isEmpty(savedWorkflowFile)) {
              logBasic(
                  BaseMessages.getString(
                      PKG,
                      "ActionDataVaultUpdate.Log.SavedGeneratedWorkflow",
                      workflowMeta.getName(),
                      savedWorkflowFile));
            }
          }
        }
      }

      if (!multiSourceUnits.isEmpty() || !freePipelines.isEmpty()) {
        DvTargetLoadMode targetLoadMode = pipelineConfig.resolveTargetLoadMode();
        UpdateExecutionOutcome outcome;
        if (targetLoadMode == DvTargetLoadMode.STAGING_FILE) {
          outcome =
              executeWithMultiSourceAndStaging(
                  result,
                  model,
                  pipelineConfig,
                  multiSourceUnits,
                  freePipelines,
                  realRunConfig,
                  realWorkflowRunConfig,
                  pipelineLogLevel,
                  success,
                  totalErrors);
        } else {
          outcome =
              executeWithMultiSourceAndOrchestrator(
                  result,
                  model,
                  multiSourceUnits,
                  freePipelines,
                  realRunConfig,
                  realWorkflowRunConfig,
                  pipelineLogLevel,
                  success,
                  totalErrors);
        }
        success = outcome.success();
        totalErrors = outcome.totalErrors();
      }

      return finishExecution(result, success, totalErrors, model);

    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.General"), e);
      result.setResult(false);
      result.setNrErrors(1);
      return result;
    }
  }

  private record UpdateExecutionOutcome(boolean success, int totalErrors) {}

  /** Multi-source hub/link: serial workflow over nested per-source pipelines. */
  private record MultiSourceUpdateUnit(
      String tableName, WorkflowMeta workflowMeta, List<PipelineMeta> nestedPipelines) {}

  private UpdateExecutionOutcome executeWithMultiSourceAndOrchestrator(
      Result result,
      DataVaultModel model,
      List<MultiSourceUpdateUnit> multiSourceUnits,
      DvUpdateExecutionSupport.FreePipelineBuckets freePipelines,
      String realRunConfig,
      String realWorkflowRunConfig,
      LogLevel pipelineLogLevel,
      boolean success,
      int totalErrors)
      throws HopException {
    String stagingFolder =
        resolve(
            DvPipelineOrchestratorSupport.resolveStagingFolder(
                pipelineStagingFolder, getVariables(), model.getName()));

    try {
      DvPipelineOrchestratorSupport.prepareStagingFolder(stagingFolder, getVariables());

      // Multi-source hubs/links first (serial sources), then free pipelines by phase:
      // hubs → links → satellites (parallel only within a phase so FKs stay valid).
      UpdateExecutionOutcome multiOutcome =
          runMultiSourceWorkflowUnits(
              multiSourceUnits,
              stagingFolder,
              realRunConfig,
              realWorkflowRunConfig,
              pipelineLogLevel,
              success,
              totalErrors);
      success = multiOutcome.success();
      totalErrors = multiOutcome.totalErrors();
      if (!success) {
        return multiOutcome;
      }

      for (List<PipelineMeta> phase : freePipelines.phases()) {
        UpdateExecutionOutcome phaseOutcome =
            executeOrchestratorUpdate(
                result, model, phase, realRunConfig, pipelineLogLevel, success, totalErrors);
        success = phaseOutcome.success();
        totalErrors = phaseOutcome.totalErrors();
        if (!success) {
          return phaseOutcome;
        }
      }
      return new UpdateExecutionOutcome(success, totalErrors);
    } finally {
      try {
        DvPipelineOrchestratorSupport.cleanupStagingFolder(stagingFolder, getVariables());
      } catch (HopException e) {
        logError(
            BaseMessages.getString(
                PKG, "ActionDataVaultUpdate.Error.StagingCleanupFailed", stagingFolder),
            e);
      }
    }
  }

  private UpdateExecutionOutcome executeWithMultiSourceAndStaging(
      Result result,
      DataVaultModel model,
      DataVaultConfiguration pipelineConfig,
      List<MultiSourceUpdateUnit> multiSourceUnits,
      DvUpdateExecutionSupport.FreePipelineBuckets freePipelines,
      String realRunConfig,
      String realWorkflowRunConfig,
      LogLevel pipelineLogLevel,
      boolean success,
      int totalErrors)
      throws HopException {
    ResolvedExecutionMetrics executionMetrics =
        ExecutionMetricsProfileResolver.resolve(
            resolve(executionMetricsProfile),
            resolve(metricsOutputFolder),
            resolve(dataCatalogConnection),
            pipelineConfig.getTargetDatabase(),
            GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
            getParentWorkflow(),
            getVariables(),
            getMetadataProvider());
    DatabaseMeta targetDatabase =
        DvSpecialRecordSupport.loadTargetDatabase(getMetadataProvider(), pipelineConfig);

    // Serial multi-source: pipeline+bulk per source in order (buildMasterWorkflow is sequential).
    for (MultiSourceUpdateUnit unit : multiSourceUnits) {
      if (unit == null || unit.nestedPipelines() == null || unit.nestedPipelines().isEmpty()) {
        continue;
      }
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionDataVaultUpdate.Log.RunningMultiSourceStaging",
              unit.tableName(),
              unit.nestedPipelines().size()));
      DvModelBulkUpdateExecutionSupport.ExecutionOutcome bulkOutcome =
          DvModelBulkUpdateExecutionSupport.executeStagingFileUpdate(
              result,
              model.getName() + "-" + unit.tableName(),
              pipelineConfig,
              unit.nestedPipelines(),
              realRunConfig,
              realWorkflowRunConfig,
              pipelineLogLevel,
              pipelineStagingFolder,
              targetDatabase,
              pipelineConfig.getTargetDatabase(),
              executionMetrics.enabled() ? executionMetrics.metricsOutputFolder() : null,
              executionMetrics.enabled() ? executionMetrics.publishContext() : null,
              resolve(dataVaultModelFile),
              getParentWorkflow(),
              success,
              totalErrors,
              getVariables(),
              this,
              getMetadataProvider());
      success = bulkOutcome.success();
      totalErrors = bulkOutcome.totalErrors();
      if (!success) {
        return new UpdateExecutionOutcome(success, totalErrors);
      }
    }

    // Sequential master workflow: free pipelines already flattened hub → link → satellite.
    List<PipelineMeta> freePipelineMetas = freePipelines.flattenedInDependencyOrder();
    if (!freePipelineMetas.isEmpty()) {
      DvModelBulkUpdateExecutionSupport.ExecutionOutcome bulkOutcome =
          DvModelBulkUpdateExecutionSupport.executeStagingFileUpdate(
              result,
              model.getName(),
              pipelineConfig,
              freePipelineMetas,
              realRunConfig,
              realWorkflowRunConfig,
              pipelineLogLevel,
              pipelineStagingFolder,
              targetDatabase,
              pipelineConfig.getTargetDatabase(),
              executionMetrics.enabled() ? executionMetrics.metricsOutputFolder() : null,
              executionMetrics.enabled() ? executionMetrics.publishContext() : null,
              resolve(dataVaultModelFile),
              getParentWorkflow(),
              success,
              totalErrors,
              getVariables(),
              this,
              getMetadataProvider());
      return new UpdateExecutionOutcome(bulkOutcome.success(), bulkOutcome.totalErrors());
    }
    return new UpdateExecutionOutcome(success, totalErrors);
  }

  private UpdateExecutionOutcome runMultiSourceWorkflowUnits(
      List<MultiSourceUpdateUnit> multiSourceUnits,
      String stagingFolder,
      String realRunConfig,
      String realWorkflowRunConfig,
      LogLevel pipelineLogLevel,
      boolean success,
      int totalErrors)
      throws HopException {
    if (multiSourceUnits == null || multiSourceUnits.isEmpty()) {
      return new UpdateExecutionOutcome(success, totalErrors);
    }

    String workflowRunConfig =
        !Utils.isEmpty(realWorkflowRunConfig) ? realWorkflowRunConfig : realRunConfig;

    for (MultiSourceUpdateUnit unit : multiSourceUnits) {
      if (unit == null || unit.workflowMeta() == null) {
        continue;
      }
      List<PipelineMeta> nested =
          unit.nestedPipelines() != null ? unit.nestedPipelines() : List.of();
      DvPipelineOrchestratorSupport.stageNamedPipelines(stagingFolder, getVariables(), nested);
      java.util.Map<String, String> stagedPaths =
          DvMultiSourceUpdateWorkflowSupport.mapStagedPipelinePaths(nested);
      DvMultiSourceUpdateWorkflowSupport.applyStagedPipelineFilenames(
          unit.workflowMeta(), stagedPaths);
      DvMultiSourceUpdateWorkflowSupport.applyPipelineRunConfiguration(
          unit.workflowMeta(), realRunConfig);
      DvPipelineOrchestratorSupport.stageWorkflow(
          stagingFolder, getVariables(), unit.workflowMeta());

      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionDataVaultUpdate.Log.RunningMultiSourceWorkflow",
              unit.tableName(),
              unit.workflowMeta().getName(),
              nested.size()));

      Result workflowResult =
          DvUpdateWorkflowSupport.runMasterWorkflow(
              unit.workflowMeta(),
              workflowRunConfig,
              pipelineLogLevel != null ? pipelineLogLevel : getLogLevel(),
              this,
              getVariables(),
              getMetadataProvider());
      if (workflowResult == null) {
        workflowResult = new Result();
      }
      if (workflowResult.getNrErrors() > 0 || !workflowResult.getResult()) {
        logError(
            BaseMessages.getString(
                PKG,
                "ActionDataVaultUpdate.Error.MultiSourceWorkflowFailed",
                unit.workflowMeta().getName(),
                unit.tableName()));
        success = false;
        totalErrors += Math.max(1, (int) workflowResult.getNrErrors());
        return new UpdateExecutionOutcome(success, totalErrors);
      }
    }
    return new UpdateExecutionOutcome(success, totalErrors);
  }

  private UpdateExecutionOutcome executeOrchestratorUpdate(
      Result result,
      DataVaultModel model,
      List<PipelineMeta> allPipelineMetas,
      String realRunConfig,
      LogLevel pipelineLogLevel,
      boolean success,
      int totalErrors)
      throws HopException {
    String stagingFolder =
        resolve(
            DvPipelineOrchestratorSupport.resolveStagingFolder(
                pipelineStagingFolder, getVariables(), model.getName()));
    int parallelCopies =
        DvPipelineOrchestratorSupport.resolveParallelCopies(parallelPipelineCopies, getVariables());

    logBasic(
        BaseMessages.getString(
            PKG,
            "ActionDataVaultUpdate.Log.StagingPipelines",
            stagingFolder,
            allPipelineMetas.size()));
    logBasic(
        BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.ParallelCopies", parallelCopies));

    try {
      // Staging folder may already be prepared by multi-source path; recreate is safe.
      DvPipelineOrchestratorSupport.prepareStagingFolder(stagingFolder, getVariables());
      // Free pipelines only — use a free-only subfolder so Get File Names does not pick
      // multi-source
      // nested .hpl files left from earlier staging.
      String freeFolder = stagingFolder;
      if (!freeFolder.endsWith("/") && !freeFolder.endsWith("\\")) {
        freeFolder = freeFolder + "/";
      }
      freeFolder = freeFolder + "free/";
      DvPipelineOrchestratorSupport.prepareStagingFolder(freeFolder, getVariables());
      DvPipelineOrchestratorSupport.stagePipelines(
          freeFolder, getVariables(), allPipelineMetas, true);

      PipelineMeta orchestrator =
          DvPipelineOrchestratorSupport.buildOrchestratorPipeline(
              freeFolder, realRunConfig, parallelCopies, model.getName());

      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionDataVaultUpdate.Log.RunningOrchestrator",
              orchestrator.getName(),
              realRunConfig));

      DataVaultConfiguration pipelineConfig = model.getConfigurationOrDefault();
      ResolvedExecutionMetrics executionMetrics =
          ExecutionMetricsProfileResolver.resolve(
              resolve(executionMetricsProfile),
              resolve(metricsOutputFolder),
              resolve(dataCatalogConnection),
              pipelineConfig != null ? pipelineConfig.getTargetDatabase() : null,
              GeneratedPipelineMetadataConstants.MODEL_TYPE_DV,
              getParentWorkflow(),
              getVariables(),
              getMetadataProvider());

      Result orchestratorResult =
          DvPipelineOrchestratorSupport.runOrchestrator(
              orchestrator,
              realRunConfig,
              pipelineLogLevel != null ? pipelineLogLevel : getLogLevel(),
              executionMetrics.enabled() ? executionMetrics.metricsOutputFolder() : null,
              executionMetrics.enabled() ? executionMetrics.publishContext() : null,
              stagingFolder,
              resolve(dataVaultModelFile),
              this,
              getParentWorkflow(),
              getVariables(),
              getMetadataProvider());

      DvPipelineOrchestratorSupport.mergeResult(result, orchestratorResult);

      if (orchestratorResult.getNrErrors() > 0 || !orchestratorResult.getResult()) {
        logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.OrchestratorFailed"));
        success = false;
        totalErrors += orchestratorResult.getNrErrors();
      }
    } finally {
      try {
        DvPipelineOrchestratorSupport.cleanupStagingFolder(stagingFolder, getVariables());
        logBasic(
            BaseMessages.getString(PKG, "ActionDataVaultUpdate.Log.StagingCleanup", stagingFolder));
      } catch (HopException e) {
        logError(
            BaseMessages.getString(
                PKG, "ActionDataVaultUpdate.Error.StagingCleanupFailed", stagingFolder),
            e);
      }
    }
    return new UpdateExecutionOutcome(success, totalErrors);
  }

  private Result finishExecution(
      Result result, boolean success, int totalErrors, DataVaultModel model) {
    if (publishToCatalog) {
      if (!publishModelToCatalog(model)) {
        success = false;
        totalErrors++;
      }
    }
    result.setResult(success);
    result.setNrErrors(success ? 0 : (totalErrors > 0 ? totalErrors : 1));
    return result;
  }

  private boolean publishModelToCatalog(DataVaultModel model) {
    String catalogConnection = resolve(dataCatalogConnection);
    if (Utils.isEmpty(catalogConnection)) {
      logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.NoDataCatalogConnection"));
      return false;
    }
    try {
      DvCatalogPublisher.PublishResult publishResult =
          DvCatalogPublisher.publish(
              catalogConnection,
              model,
              getVariables(),
              getMetadataProvider(),
              getName(),
              new DvCatalogPublisher.CatalogPublishLog() {
                @Override
                public void logBasic(String message) {
                  ActionDataVaultUpdate.this.logBasic(message);
                }

                @Override
                public void logError(String message, Throwable throwable) {
                  ActionDataVaultUpdate.this.logError(message, throwable);
                }
              });
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionDataVaultUpdate.Log.CatalogPublished",
              publishResult.getTableCount(),
              catalogConnection,
              publishResult.getErrorCount()));
      return publishResult.isSuccess();
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.CatalogPublishFailed"), e);
      return false;
    }
  }

  private void validatePipelineIntegerSettings(DataVaultConfiguration pipelineConfig)
      throws HopException {
    DvIntegerSettingValidationSupport.requireModelPipelineIntegerSettings(
        pipelineConfig, getVariables());
    DvIntegerSettingValidationSupport.requirePositiveInteger(
        parallelPipelineCopies,
        getVariables(),
        "1",
        BaseMessages.getString(
            DvIntegerSettingValidationSupport.class,
            "DvIntegerSettingValidation.Label.ParallelPipelineCopies"));
  }

  private boolean hasDdlStatements(List<String> ddlStatements) {
    if (ddlStatements == null || ddlStatements.isEmpty()) {
      return false;
    }
    for (String ddl : ddlStatements) {
      if (!Utils.isEmpty(ddl)) {
        return true;
      }
    }
    return false;
  }

  private void writeDdlToFile(
      List<String> ddlStatements, DatabaseMeta targetDatabase, String filename)
      throws IOException, HopException {
    FileObject file = HopVfs.getFileObject(filename, getVariables());
    try (OutputStreamWriter writer =
        new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
      if (targetDatabase != null) {
        writer.write("-- Target database: ");
        writer.write(targetDatabase.getName());
        writer.write(Const.CR);
      }
      for (String ddl : ddlStatements) {
        if (!Utils.isEmpty(ddl)) {
          writer.write(ddl);
          writer.write(Const.CR);
        }
      }
      writer.write(Const.CR);
    }
  }

  private DataVaultModel loadReferencedDataModel(
      IHopMetadataProvider provider, IVariables variables) throws HopException {
    String realModelFile = variables.resolve(dataVaultModelFile);
    if (Utils.isEmpty(realModelFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionDataVaultUpdate.Error.NoModelFile"));
    }
    // Load the DataVaultModel from .hdv file (XML)
    FileObject file = HopVfs.getFileObject(realModelFile, getVariables());
    Document document = XmlHandler.loadXmlFile(file);
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    if (rootNode == null) {
      rootNode = document.getDocumentElement();
    }

    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    model.setFilename(realModelFile);

    return model;
  }

  /**
   * @return The objects referenced in the transform, like a a pipeline, a workflow, a mapper, a
   *     reducer, a combiner, ...
   */
  @Override
  public String[] getReferencedObjectDescriptions() {
    return new String[] {
      BaseMessages.getString(PKG, "ActionDataVaultUpdate.ReferencedObject.Description"),
    };
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    return new boolean[] {
      StringUtils.isNotEmpty(dataVaultModelFile),
    };
  }

  /**
   * Load the referenced object
   *
   * @param index the referenced object index to load (in case there are multiple references)
   * @param metadataProvider metadataProvider
   * @param variables the variable variables to use
   * @return the referenced object once loaded
   * @throws HopException
   */
  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    return loadReferencedDataModel(metadataProvider, variables);
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
}
