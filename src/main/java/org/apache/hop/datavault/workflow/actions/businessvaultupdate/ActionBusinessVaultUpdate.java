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
package org.apache.hop.datavault.workflow.actions.businessvaultupdate;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
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
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.catalog.BvCatalogPublisher;
import org.apache.hop.datavault.config.DvRunConfigurationSupport;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.lineage.DdlLineageExplainSupport;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvDdlSupport;
import org.apache.hop.datavault.metadata.DvIntegerSettingValidationSupport;
import org.apache.hop.datavault.metadata.DvLoadCycleSupport;
import org.apache.hop.datavault.metadata.DvModelBulkUpdateExecutionSupport;
import org.apache.hop.datavault.metadata.DvTargetLoadMode;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.apache.hop.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultUpdateExecutionSupport;
import org.apache.hop.datavault.metadata.businessvault.BvGeneratedPipelineSupport;
import org.apache.hop.datavault.metadata.businessvault.BvTargetDatabaseSupport;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metrics.DvUpdateMetricsCollector;
import org.apache.hop.datavault.metrics.ExecutionMetricsProfileResolver;
import org.apache.hop.datavault.metrics.ResolvedExecutionMetrics;
import org.apache.hop.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.config.WorkflowRunConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Generates and executes Business Vault update pipelines from a .hbv model. */
@Action(
    id = "BUSINESS_VAULT_UPDATE",
    name = "i18n::ActionBusinessVaultUpdate.Name",
    description = "i18n::ActionBusinessVaultUpdate.Description",
    image = "business-vault-model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionBusinessVaultUpdate.Keywords",
    documentationUrl = "/workflow/actions/businessvaultupdate.html")
@GuiPlugin(description = "Business Vault Update action")
@Getter
@Setter
public class ActionBusinessVaultUpdate extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionBusinessVaultUpdate.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "BUSINESS_VAULT_UPDATE_ACTION";
  public static final String GUI_PLUGIN_ELEMENT_MODEL_TAB_ID =
      "BUSINESS_VAULT_UPDATE_ACTION_MODEL_TAB";
  public static final String GUI_PLUGIN_ELEMENT_DDL_TAB_ID = "BUSINESS_VAULT_UPDATE_ACTION_DDL_TAB";
  public static final String DEFAULT_STAGING_PREFIX = "${java.io.tmpdir}/bv/";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionBusinessVaultUpdate.BusinessVaultModel.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.BusinessVaultModel.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String businessVaultModelFile;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = PipelineRunConfiguration.class,
      label = "i18n::ActionBusinessVaultUpdate.PipelineRunConfiguration.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.PipelineRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String pipelineRunConfiguration;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.METADATA,
      metadata = WorkflowRunConfiguration.class,
      label = "i18n::ActionBusinessVaultUpdate.WorkflowRunConfiguration.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.WorkflowRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String workflowRunConfiguration;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBusinessVaultUpdate.LogModelCheckFailures.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.LogModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean logModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBusinessVaultUpdate.AbortOnModelCheckFailures.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.AbortOnModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean abortOnModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionBusinessVaultUpdate.ParallelPipelineCopies.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.ParallelPipelineCopies.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String parallelPipelineCopies = "1";

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionBusinessVaultUpdate.PipelineStagingFolder.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.PipelineStagingFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String pipelineStagingFolder;

  @GuiWidgetElement(
      order = "0695",
      type = GuiElementType.METADATA,
      metadata = ExecutionMetricsProfileMeta.class,
      label = "i18n::ActionBusinessVaultUpdate.ExecutionMetricsProfile.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.ExecutionMetricsProfile.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String executionMetricsProfile;

  @HopMetadataProperty private String metricsOutputFolder;

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBusinessVaultUpdate.PublishToCatalog.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.PublishToCatalog.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean publishToCatalog;

  @GuiWidgetElement(
      order = "0850",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::ActionBusinessVaultUpdate.DataCatalogConnection.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.DataCatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String dataCatalogConnection;

  @GuiWidgetElement(
      order = "0900",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionBusinessVaultUpdate.SelectedTableNames.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.SelectedTableNames.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String selectedTableNames;

  @GuiWidgetElement(
      order = "1000",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBusinessVaultUpdate.DoNotUpdateTargetDatabase.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.DoNotUpdateTargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean doNotUpdateTargetDatabase;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBusinessVaultUpdate.UpdateTargetStructure.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.UpdateTargetStructure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private boolean updateTargetDatabaseStructure = true;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionBusinessVaultUpdate.DdlSqlFilename.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.DdlSqlFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private String ddlSqlFilename;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionBusinessVaultUpdate.FailIfDdlNeeded.Label",
      toolTip = "i18n::ActionBusinessVaultUpdate.FailIfDdlNeeded.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private boolean failIfDdlNeeded;

  public ActionBusinessVaultUpdate(String name) {
    super(name, "");
  }

  public ActionBusinessVaultUpdate() {
    this("");
  }

  public ActionBusinessVaultUpdate(ActionBusinessVaultUpdate meta) {
    super(meta);
    this.businessVaultModelFile = meta.businessVaultModelFile;
    this.pipelineRunConfiguration = meta.pipelineRunConfiguration;
    this.workflowRunConfiguration = meta.workflowRunConfiguration;
    this.logModelCheckFailures = meta.logModelCheckFailures;
    this.abortOnModelCheckFailures = meta.abortOnModelCheckFailures;
    this.parallelPipelineCopies = meta.parallelPipelineCopies;
    this.pipelineStagingFolder = meta.pipelineStagingFolder;
    this.executionMetricsProfile = meta.executionMetricsProfile;
    this.metricsOutputFolder = meta.metricsOutputFolder;
    this.publishToCatalog = meta.publishToCatalog;
    this.dataCatalogConnection = meta.dataCatalogConnection;
    this.selectedTableNames = meta.selectedTableNames;
    this.doNotUpdateTargetDatabase = meta.doNotUpdateTargetDatabase;
    this.updateTargetDatabaseStructure = meta.updateTargetDatabaseStructure;
    this.ddlSqlFilename = meta.ddlSqlFilename;
    this.failIfDdlNeeded = meta.failIfDdlNeeded;
  }

  @Override
  public ActionBusinessVaultUpdate clone() {
    return new ActionBusinessVaultUpdate(this);
  }

  @Override
  public String getDialogClassName() {
    return ActionBusinessVaultUpdateDialog.class.getName();
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
        logError(BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Error.NoRunConfig"));
        return result;
      }

      BusinessVaultModel bvModel = loadBusinessVaultModel(getMetadataProvider(), getVariables());
      DataVaultModel dvModel =
          BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
              bvModel, getVariables(), getMetadataProvider());

      BusinessVaultConfiguration bvConfiguration = bvModel.getConfigurationOrDefault();
      if (bvConfiguration.isStoreLoadCycleId()) {
        DatabaseMeta loadCycleDatabase =
            BvTargetDatabaseSupport.loadTargetDatabase(getMetadataProvider(), bvConfiguration);
        if (loadCycleDatabase == null) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "ActionBusinessVaultUpdate.Error.LoadCycleTargetMissing"));
        }
        ILoggingObject loadCycleLog =
            new SimpleLoggingObject("ActionBusinessVaultUpdate", LoggingObjectType.GENERAL, null);
        long cycleId =
            DvLoadCycleSupport.allocateNext(
                loadCycleDatabase,
                getVariables(),
                loadCycleLog,
                bvConfiguration.getLoadCycleControlTable());
        if (getParentWorkflow() != null) {
          DvLoadCycleSupport.setCycleIdVariable(getParentWorkflow(), cycleId);
        }
        logBasic(
            BaseMessages.getString(
                PKG, "ActionBusinessVaultUpdate.Log.UsingLoadCycleId", Long.toString(cycleId)));
      }

      if (logModelCheckFailures || abortOnModelCheckFailures) {
        List<ICheckResult> remarks = bvModel.check(getMetadataProvider(), getVariables());
        for (ICheckResult remark : remarks) {
          String message =
              BaseMessages.getString(
                  PKG,
                  "ActionBusinessVaultUpdate.Log.ModelCheckResult",
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
            logError(
                BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Log.AbortingOnModelCheck"));
            return result;
          }
        }
      }

      List<IBvTable> tables = filterTables(bvModel.getTables());
      if (tables.isEmpty()) {
        logBasic(BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Log.NoTables"));
        return finishExecution(result, true, 0, bvModel, dvModel);
      }

      boolean success = true;
      int totalErrors = 0;
      String realDdlSqlFilename = resolve(ddlSqlFilename);
      boolean processDdl =
          updateTargetDatabaseStructure || failIfDdlNeeded || !Utils.isEmpty(realDdlSqlFilename);

      if (processDdl) {
        boolean ddlPhaseFailed = false;
        List<String> ddlStatements = new ArrayList<>();
        for (IBvTable table : tables) {
          try {
            for (String ddl :
                table.generateBuildDdl(getMetadataProvider(), getVariables(), bvModel, dvModel)) {
              if (!Utils.isEmpty(ddl)) {
                ddlStatements.add(ddl);
              }
            }
          } catch (Exception ex) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionBusinessVaultUpdate.Error.DdlGenerateFailed", table.getName()),
                ex);
            totalErrors++;
            success = false;
            ddlPhaseFailed = true;
          }
        }

        ddlStatements = DvDdlSupport.deduplicateCreateTableDdl(ddlStatements);

        if (hasDdlStatements(ddlStatements)) {
          try {
            String explanation =
                DdlLineageExplainSupport.explain(ddlStatements, bvModel, getVariables());
            if (!Utils.isEmpty(explanation)) {
              if (failIfDdlNeeded) {
                logError(explanation);
              } else {
                logBasic(explanation);
              }
            }
          } catch (Exception lineageEx) {
            logError(
                BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Log.LineageExplainFailed"),
                lineageEx);
          }

          if (failIfDdlNeeded) {
            logError(
                BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Log.AbortingOnDdlNeeded"));
            result.setResult(false);
            result.setNrErrors(1);
            return result;
          }

          DatabaseMeta targetDatabase =
              BvTargetDatabaseSupport.loadTargetDatabase(
                  getMetadataProvider(), bvModel.getConfigurationOrDefault());

          if (!Utils.isEmpty(realDdlSqlFilename)) {
            try {
              writeDdlToFile(ddlStatements, targetDatabase, realDdlSqlFilename);
              logBasic(
                  BaseMessages.getString(
                      PKG, "ActionBusinessVaultUpdate.Log.DdlSavedToFile", realDdlSqlFilename));
            } catch (Exception e) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionBusinessVaultUpdate.Error.DdlSaveFailed", realDdlSqlFilename),
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
                      PKG, "ActionBusinessVaultUpdate.Error.DdlExecutionFailed", ""));
              totalErrors++;
              success = false;
              ddlPhaseFailed = true;
            } else {
              ILoggingObject loggingObject =
                  new SimpleLoggingObject(
                      "ActionBusinessVaultUpdate", LoggingObjectType.GENERAL, null);
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
                            "ActionBusinessVaultUpdate.Log.SkippingCreateTable",
                            tableName != null ? tableName : ddl.trim()));
                    if (!Utils.isEmpty(tableName)) {
                      createdInBatch.add(tableName.toLowerCase(Locale.ROOT));
                    }
                    continue;
                  }
                  logBasic(
                      BaseMessages.getString(
                          PKG,
                          "ActionBusinessVaultUpdate.Log.ExecutingDdl",
                          targetDatabase.getName()));
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
                        "ActionBusinessVaultUpdate.Error.DdlExecutionFailed",
                        targetDatabase.getName()),
                    e);
                totalErrors++;
                success = false;
                ddlPhaseFailed = true;
              }
            }
          } else {
            logBasic(
                BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Log.SkippingDdlExecution"));
          }
        } else {
          logBasic(BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Log.NoDdlNeeded"));
        }

        if (updateTargetDatabaseStructure && ddlPhaseFailed) {
          logError(
              BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Log.AbortingOnDdlFailure"));
          return finishExecution(result, false, Math.max(totalErrors, 1), bvModel, dvModel);
        }
      }

      if (doNotUpdateTargetDatabase) {
        logBasic(BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Log.SkippingDataUpdate"));
        return finishExecution(result, success, totalErrors, bvModel, dvModel);
      }

      BusinessVaultConfiguration pipelineConfig = bvModel.getConfigurationOrDefault();
      validatePipelineIntegerSettings(pipelineConfig);

      List<PipelineMeta> allPipelineMetas = new ArrayList<>();

      for (IBvTable table : tables) {
        if (table == null
            || BusinessVaultUpdateExecutionSupport.isPipelineExecutableTableType(
                table.getTableType())) {
          continue;
        }
        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionBusinessVaultUpdate.Log.SkippingUnsupportedTableType",
                table.getName(),
                table.getTableType()));
      }

      for (IBvTable table :
          BusinessVaultUpdateExecutionSupport.orderTablesForPipelineExecution(
              tables, bvModel, dvModel)) {
        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionBusinessVaultUpdate.Log.GeneratingForTable",
                table.getName(),
                table.getTableType()));

        List<PipelineMeta> pipelineMetas =
            table.generateBuildPipelines(getMetadataProvider(), getVariables(), bvModel, dvModel);

        if (pipelineMetas == null || pipelineMetas.isEmpty()) {
          logError(
              BaseMessages.getString(
                  PKG, "ActionBusinessVaultUpdate.Error.GenerateFailed", table.getName()));
          totalErrors++;
          success = false;
          continue;
        }

        for (PipelineMeta pipelineMeta : pipelineMetas) {
          if (pipelineMeta == null) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionBusinessVaultUpdate.Error.GenerateFailed", table.getName()));
            totalErrors++;
            success = false;
            continue;
          }

          pipelineMeta.lookupReferencesAfterLoading();
          allPipelineMetas.add(pipelineMeta);

          String savedPipelineFile =
              BvGeneratedPipelineSupport.saveBeforeExecution(
                  pipelineConfig, getVariables(), pipelineMeta);
          if (!Utils.isEmpty(savedPipelineFile)) {
            logBasic(
                BaseMessages.getString(
                    PKG,
                    "ActionBusinessVaultUpdate.Log.SavedGeneratedPipeline",
                    pipelineMeta.getName(),
                    savedPipelineFile));
          }
        }
      }

      if (!allPipelineMetas.isEmpty()) {
        ResolvedExecutionMetrics executionMetrics =
            ExecutionMetricsProfileResolver.resolve(
                resolve(executionMetricsProfile),
                resolve(metricsOutputFolder),
                resolveCatalogConnection(bvModel),
                pipelineConfig.getTargetDatabase(),
                GeneratedPipelineMetadataConstants.MODEL_TYPE_BV,
                getParentWorkflow(),
                getVariables(),
                getMetadataProvider());
        String resolvedMetricsOutputFolder =
            executionMetrics.enabled() ? executionMetrics.metricsOutputFolder() : null;
        DvUpdateMetricsCollector.LoadRunPublishContext metricsPublishContext =
            executionMetrics.enabled() ? executionMetrics.publishContext() : null;

        DvModelBulkUpdateExecutionSupport.ExecutionOutcome outcome;
        if (pipelineConfig.resolveTargetLoadMode() == DvTargetLoadMode.STAGING_FILE) {
          DatabaseMeta targetDatabase =
              BvTargetDatabaseSupport.loadTargetDatabase(getMetadataProvider(), pipelineConfig);
          outcome =
              DvModelBulkUpdateExecutionSupport.executeStagingFileUpdate(
                  result,
                  bvModel.getName(),
                  pipelineConfig,
                  allPipelineMetas,
                  realRunConfig,
                  realWorkflowRunConfig,
                  getLogLevel(),
                  pipelineStagingFolder,
                  targetDatabase,
                  pipelineConfig.getTargetDatabase(),
                  resolvedMetricsOutputFolder,
                  metricsPublishContext,
                  resolve(businessVaultModelFile),
                  getParentWorkflow(),
                  success,
                  totalErrors,
                  getVariables(),
                  this,
                  getMetadataProvider());
        } else {
          outcome =
              DvModelBulkUpdateExecutionSupport.executeOrchestratorUpdate(
                  result,
                  bvModel.getName(),
                  allPipelineMetas,
                  realRunConfig,
                  getLogLevel(),
                  pipelineStagingFolder,
                  parallelPipelineCopies,
                  resolvedMetricsOutputFolder,
                  metricsPublishContext,
                  success,
                  totalErrors,
                  getVariables(),
                  this,
                  getParentWorkflow(),
                  getMetadataProvider());
        }
        success = outcome.success();
        totalErrors = outcome.totalErrors();
      }

      return finishExecution(result, success, totalErrors, bvModel, dvModel);

    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Error.General"), e);
      result.setResult(false);
      result.setNrErrors(1);
      return result;
    }
  }

  private Result finishExecution(
      Result result,
      boolean success,
      int totalErrors,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel) {
    if (publishToCatalog) {
      if (!publishModelToCatalog(bvModel, dvModel)) {
        success = false;
        totalErrors++;
      }
    }
    result.setResult(success);
    result.setNrErrors(success ? 0 : (totalErrors > 0 ? totalErrors : 1));
    return result;
  }

  private boolean publishModelToCatalog(BusinessVaultModel bvModel, DataVaultModel dvModel) {
    String catalogConnection = resolveCatalogConnection(bvModel);
    if (Utils.isEmpty(catalogConnection)) {
      logError(
          BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Error.NoDataCatalogConnection"));
      return false;
    }
    try {
      BvCatalogPublisher.PublishResult publishResult =
          BvCatalogPublisher.publish(
              catalogConnection,
              bvModel,
              dvModel,
              getVariables(),
              getMetadataProvider(),
              getName(),
              new BvCatalogPublisher.CatalogPublishLog() {
                @Override
                public void logBasic(String message) {
                  ActionBusinessVaultUpdate.this.logBasic(message);
                }

                @Override
                public void logError(String message, Throwable throwable) {
                  ActionBusinessVaultUpdate.this.logError(message, throwable);
                }
              });
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionBusinessVaultUpdate.Log.CatalogPublished",
              publishResult.getTableCount(),
              catalogConnection,
              publishResult.getErrorCount()));
      return publishResult.isSuccess();
    } catch (Exception e) {
      logError(
          BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Error.CatalogPublishFailed"), e);
      return false;
    }
  }

  /**
   * Action catalog connection wins; otherwise the Business Vault model configuration default
   * ({@link BusinessVaultConfiguration#getDataCatalogConnection()}).
   */
  private String resolveCatalogConnection(BusinessVaultModel bvModel) {
    String actionConnection = resolve(dataCatalogConnection);
    if (!Utils.isEmpty(actionConnection)) {
      return actionConnection;
    }
    if (bvModel == null) {
      return null;
    }
    BusinessVaultConfiguration config = bvModel.getConfigurationOrDefault();
    if (config == null || Utils.isEmpty(config.getDataCatalogConnection())) {
      return null;
    }
    return resolve(config.getDataCatalogConnection());
  }

  private List<IBvTable> filterTables(List<IBvTable> tables) {
    List<IBvTable> result = new ArrayList<>();
    if (tables == null) {
      return result;
    }
    Set<String> selected = parseSelectedTableNames(resolve(selectedTableNames));
    for (IBvTable table : tables) {
      if (table == null) {
        continue;
      }
      if (selected.isEmpty() || selected.contains(table.getName())) {
        result.add(table);
      }
    }
    return result;
  }

  private static Set<String> parseSelectedTableNames(String value) {
    Set<String> selected = new HashSet<>();
    if (Utils.isEmpty(value)) {
      return selected;
    }
    for (String part : value.split(",")) {
      if (!Utils.isEmpty(part)) {
        selected.add(part.trim());
      }
    }
    return selected;
  }

  private static String resolveStagingFolder(
      String configuredFolder, IVariables variables, String modelName) {
    String folder = configuredFolder;
    if (variables != null) {
      folder = variables.resolve(folder);
    }
    if (Utils.isEmpty(folder)) {
      folder = DEFAULT_STAGING_PREFIX + sanitizeModelName(modelName) + "/";
    }
    if (!folder.endsWith("/") && !folder.endsWith("\\")) {
      folder = folder + "/";
    }
    return folder;
  }

  private static String sanitizeModelName(String modelName) {
    if (Utils.isEmpty(modelName)) {
      return "business-vault";
    }
    return modelName.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private void validatePipelineIntegerSettings(BusinessVaultConfiguration pipelineConfig)
      throws HopException {
    DvIntegerSettingValidationSupport.requirePositiveInteger(
        pipelineConfig.getTargetTableBatchSize(),
        getVariables(),
        BusinessVaultConfiguration.DEFAULT_TARGET_TABLE_BATCH_SIZE,
        BaseMessages.getString(
            DvIntegerSettingValidationSupport.class,
            "DvIntegerSettingValidation.Label.TargetTableBatchSize"));
    DvIntegerSettingValidationSupport.requirePositiveInteger(
        pipelineConfig.getTargetTableParallelCopies(),
        getVariables(),
        BusinessVaultConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES,
        BaseMessages.getString(
            DvIntegerSettingValidationSupport.class,
            "DvIntegerSettingValidation.Label.TargetTableParallelCopies"));
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

  private BusinessVaultModel loadBusinessVaultModel(
      IHopMetadataProvider provider, IVariables variables) throws HopException {
    String realModelFile = variables.resolve(businessVaultModelFile);
    if (Utils.isEmpty(realModelFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.Error.NoModelFile"));
    }
    FileObject file = HopVfs.getFileObject(realModelFile, getVariables());
    Document document = XmlHandler.loadXmlFile(file);
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    if (rootNode == null) {
      rootNode = document.getDocumentElement();
    }

    BusinessVaultModel model = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    model.setFilename(realModelFile);
    return model;
  }

  @Override
  public String[] getReferencedObjectDescriptions() {
    return new String[] {
      BaseMessages.getString(PKG, "ActionBusinessVaultUpdate.ReferencedObject.Description"),
    };
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    return new boolean[] {StringUtils.isNotEmpty(businessVaultModelFile)};
  }

  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    return loadBusinessVaultModel(metadataProvider, variables);
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
