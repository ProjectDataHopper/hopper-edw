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
package org.hopper.edw.datavault.workflow.actions.dimensionalupdate;

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
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
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
import org.hopper.edw.datavault.catalog.DmCatalogPublisher;
import org.hopper.edw.datavault.config.DvRunConfigurationSupport;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.lineage.DdlLineageExplainSupport;
import org.hopper.edw.datavault.metadata.DvDdlSupport;
import org.hopper.edw.datavault.metadata.DvIntegerSettingValidationSupport;
import org.hopper.edw.datavault.metadata.DvLoadCycleSupport;
import org.hopper.edw.datavault.metadata.DvModelBulkUpdateExecutionSupport;
import org.hopper.edw.datavault.metadata.DvTargetLoadMode;
import org.hopper.edw.datavault.metadata.GeneratedPipelineMetadataConstants;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceRecordDefinitionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.metadata.dimensional.pipeline.DmGeneratedPipelineSupport;
import org.hopper.edw.datavault.metadata.dimensional.pipeline.DmUpdateExecutionSupport;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingMeta;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingSupport;
import org.hopper.edw.datavault.metrics.DvUpdateMetricsCollector;
import org.hopper.edw.datavault.metrics.ExecutionMetricsProfileResolver;
import org.hopper.edw.datavault.metrics.ResolvedExecutionMetrics;
import org.hopper.edw.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
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

/** Generates and executes dimensional warehouse update pipelines from a .hdm model. */
@Action(
    id = "DIMENSIONAL_UPDATE",
    name = "i18n::ActionDimensionalUpdate.Name",
    description = "i18n::ActionDimensionalUpdate.Description",
    image = "dimensional-model.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionDimensionalUpdate.Keywords",
    documentationUrl = "/workflow/actions/dimensionalupdate.html")
@GuiPlugin(description = "Dimensional Update action")
@Getter
@Setter
public class ActionDimensionalUpdate extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionDimensionalUpdate.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DIMENSIONAL_UPDATE_ACTION";
  public static final String GUI_PLUGIN_ELEMENT_MODEL_TAB_ID =
      "DIMENSIONAL_UPDATE_ACTION_MODEL_TAB";
  public static final String GUI_PLUGIN_ELEMENT_DDL_TAB_ID = "DIMENSIONAL_UPDATE_ACTION_DDL_TAB";
  public static final String DEFAULT_STAGING_PREFIX = "${java.io.tmpdir}/dm/";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDimensionalUpdate.DimensionalModel.Label",
      toolTip = "i18n::ActionDimensionalUpdate.DimensionalModel.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String dimensionalModelFile;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = PipelineRunConfiguration.class,
      label = "i18n::ActionDimensionalUpdate.PipelineRunConfiguration.Label",
      toolTip = "i18n::ActionDimensionalUpdate.PipelineRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String pipelineRunConfiguration;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.METADATA,
      metadata = WorkflowRunConfiguration.class,
      label = "i18n::ActionDimensionalUpdate.WorkflowRunConfiguration.Label",
      toolTip = "i18n::ActionDimensionalUpdate.WorkflowRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String workflowRunConfiguration;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalUpdate.LogModelCheckFailures.Label",
      toolTip = "i18n::ActionDimensionalUpdate.LogModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean logModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalUpdate.AbortOnModelCheckFailures.Label",
      toolTip = "i18n::ActionDimensionalUpdate.AbortOnModelCheckFailures.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean abortOnModelCheckFailures = true;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionDimensionalUpdate.ParallelPipelineCopies.Label",
      toolTip = "i18n::ActionDimensionalUpdate.ParallelPipelineCopies.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String parallelPipelineCopies = "1";

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionDimensionalUpdate.PipelineStagingFolder.Label",
      toolTip = "i18n::ActionDimensionalUpdate.PipelineStagingFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String pipelineStagingFolder;

  @GuiWidgetElement(
      order = "0695",
      type = GuiElementType.METADATA,
      metadata = ExecutionMetricsProfileMeta.class,
      label = "i18n::ActionDimensionalUpdate.ExecutionMetricsProfile.Label",
      toolTip = "i18n::ActionDimensionalUpdate.ExecutionMetricsProfile.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String executionMetricsProfile;

  @HopMetadataProperty private String metricsOutputFolder;

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionDimensionalUpdate.SelectedTableNames.Label",
      toolTip = "i18n::ActionDimensionalUpdate.SelectedTableNames.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String selectedTableNames;

  @GuiWidgetElement(
      order = "0900",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalUpdate.DoNotUpdateTargetDatabase.Label",
      toolTip = "i18n::ActionDimensionalUpdate.DoNotUpdateTargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean doNotUpdateTargetDatabase;

  @GuiWidgetElement(
      order = "0950",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalUpdate.PublishToCatalog.Label",
      toolTip = "i18n::ActionDimensionalUpdate.PublishToCatalog.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private boolean publishToCatalog;

  @GuiWidgetElement(
      order = "0960",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::ActionDimensionalUpdate.DataCatalogConnection.Label",
      toolTip = "i18n::ActionDimensionalUpdate.DataCatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MODEL_TAB_ID)
  @HopMetadataProperty
  private String dataCatalogConnection;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalUpdate.UpdateTargetStructure.Label",
      toolTip = "i18n::ActionDimensionalUpdate.UpdateTargetStructure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private boolean updateTargetDatabaseStructure = true;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.METADATA,
      metadata = TargetTypeMappingMeta.class,
      variables = true,
      label = "i18n::ActionDimensionalUpdate.TargetTypeMapping.Label",
      toolTip = "i18n::ActionDimensionalUpdate.TargetTypeMapping.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private String targetTypeMapping;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FILENAME,
      label = "i18n::ActionDimensionalUpdate.DdlSqlFilename.Label",
      toolTip = "i18n::ActionDimensionalUpdate.DdlSqlFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private String ddlSqlFilename;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionDimensionalUpdate.FailIfDdlNeeded.Label",
      toolTip = "i18n::ActionDimensionalUpdate.FailIfDdlNeeded.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DDL_TAB_ID)
  @HopMetadataProperty
  private boolean failIfDdlNeeded;

  public ActionDimensionalUpdate(String name) {
    super(name, "");
  }

  public ActionDimensionalUpdate() {
    this("");
  }

  public ActionDimensionalUpdate(ActionDimensionalUpdate meta) {
    super(meta);
    this.dimensionalModelFile = meta.dimensionalModelFile;
    this.pipelineRunConfiguration = meta.pipelineRunConfiguration;
    this.workflowRunConfiguration = meta.workflowRunConfiguration;
    this.logModelCheckFailures = meta.logModelCheckFailures;
    this.abortOnModelCheckFailures = meta.abortOnModelCheckFailures;
    this.parallelPipelineCopies = meta.parallelPipelineCopies;
    this.pipelineStagingFolder = meta.pipelineStagingFolder;
    this.executionMetricsProfile = meta.executionMetricsProfile;
    this.metricsOutputFolder = meta.metricsOutputFolder;
    this.selectedTableNames = meta.selectedTableNames;
    this.doNotUpdateTargetDatabase = meta.doNotUpdateTargetDatabase;
    this.publishToCatalog = meta.publishToCatalog;
    this.dataCatalogConnection = meta.dataCatalogConnection;
    this.updateTargetDatabaseStructure = meta.updateTargetDatabaseStructure;
    this.targetTypeMapping = meta.targetTypeMapping;
    this.ddlSqlFilename = meta.ddlSqlFilename;
    this.failIfDdlNeeded = meta.failIfDdlNeeded;
  }

  @Override
  public ActionDimensionalUpdate clone() {
    return new ActionDimensionalUpdate(this);
  }

  @Override
  public String getDialogClassName() {
    return ActionDimensionalUpdateDialog.class.getName();
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
        logError(BaseMessages.getString(PKG, "ActionDimensionalUpdate.Error.NoRunConfig"));
        return result;
      }

      DimensionalModel dmModel = loadDimensionalModel(getMetadataProvider(), getVariables());

      DimensionalConfiguration dmConfiguration = dmModel.getConfigurationOrDefault();
      if (dmConfiguration.isStoreLoadCycleId()) {
        DatabaseMeta loadCycleDatabase =
            DmTargetDatabaseSupport.loadTargetDatabase(getMetadataProvider(), dmConfiguration);
        if (loadCycleDatabase == null) {
          throw new HopException(
              BaseMessages.getString(PKG, "ActionDimensionalUpdate.Error.LoadCycleTargetMissing"));
        }
        ILoggingObject loadCycleLog =
            new SimpleLoggingObject("ActionDimensionalUpdate", LoggingObjectType.GENERAL, null);
        long cycleId =
            DvLoadCycleSupport.allocateNext(
                loadCycleDatabase,
                getVariables(),
                loadCycleLog,
                dmConfiguration.getLoadCycleControlTable());
        if (getParentWorkflow() != null) {
          DvLoadCycleSupport.setCycleIdVariable(getParentWorkflow(), cycleId);
        }
        logBasic(
            BaseMessages.getString(
                PKG, "ActionDimensionalUpdate.Log.UsingLoadCycleId", Long.toString(cycleId)));
      }

      if (logModelCheckFailures || abortOnModelCheckFailures) {
        List<ICheckResult> remarks = dmModel.check(getMetadataProvider(), getVariables());
        for (ICheckResult remark : remarks) {
          String message =
              BaseMessages.getString(
                  PKG,
                  "ActionDimensionalUpdate.Log.ModelCheckResult",
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
                BaseMessages.getString(PKG, "ActionDimensionalUpdate.Log.AbortingOnModelCheck"));
            return result;
          }
        }
      }

      List<IDmTable> tables = filterTables(dmModel.getTables());
      if (tables.isEmpty()) {
        logBasic(BaseMessages.getString(PKG, "ActionDimensionalUpdate.Log.NoTables"));
        return finishExecution(result, true, 0, dmModel);
      }

      boolean success = true;
      int totalErrors = 0;
      String realDdlSqlFilename = resolve(ddlSqlFilename);
      TargetTypeMappingSupport.applyExplicitName(getVariables(), targetTypeMapping);
      boolean processDdl =
          updateTargetDatabaseStructure || failIfDdlNeeded || !Utils.isEmpty(realDdlSqlFilename);

      if (processDdl) {
        boolean ddlPhaseFailed = false;
        List<String> ddlStatements = new ArrayList<>();
        for (IDmTable table : tables) {
          try {
            for (String ddl :
                table.generateBuildDdl(getMetadataProvider(), getVariables(), dmModel)) {
              if (!Utils.isEmpty(ddl)) {
                ddlStatements.add(ddl);
              }
            }
          } catch (Exception ex) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionDimensionalUpdate.Error.DdlGenerateFailed", table.getName()),
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
                DdlLineageExplainSupport.explain(ddlStatements, dmModel, getVariables());
            if (!Utils.isEmpty(explanation)) {
              if (failIfDdlNeeded) {
                logError(explanation);
              } else {
                logBasic(explanation);
              }
            }
          } catch (Exception lineageEx) {
            logError(
                BaseMessages.getString(PKG, "ActionDimensionalUpdate.Log.LineageExplainFailed"),
                lineageEx);
          }

          if (failIfDdlNeeded) {
            logError(
                BaseMessages.getString(PKG, "ActionDimensionalUpdate.Log.AbortingOnDdlNeeded"));
            result.setResult(false);
            result.setNrErrors(1);
            return result;
          }

          DatabaseMeta targetDatabase =
              DmTargetDatabaseSupport.loadTargetDatabase(
                  getMetadataProvider(), dmModel.getConfigurationOrDefault());

          if (!Utils.isEmpty(realDdlSqlFilename)) {
            try {
              writeDdlToFile(ddlStatements, targetDatabase, realDdlSqlFilename);
              logBasic(
                  BaseMessages.getString(
                      PKG, "ActionDimensionalUpdate.Log.DdlSavedToFile", realDdlSqlFilename));
            } catch (Exception e) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionDimensionalUpdate.Error.DdlSaveFailed", realDdlSqlFilename),
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
                      PKG, "ActionDimensionalUpdate.Error.DdlExecutionFailed", ""));
              totalErrors++;
              success = false;
              ddlPhaseFailed = true;
            } else {
              ILoggingObject loggingObject =
                  new SimpleLoggingObject(
                      "ActionDimensionalUpdate", LoggingObjectType.GENERAL, null);
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
                            "ActionDimensionalUpdate.Log.SkippingCreateTable",
                            tableName != null ? tableName : ddl.trim()));
                    if (!Utils.isEmpty(tableName)) {
                      createdInBatch.add(tableName.toLowerCase(Locale.ROOT));
                    }
                    continue;
                  }
                  logBasic(
                      BaseMessages.getString(
                          PKG,
                          "ActionDimensionalUpdate.Log.ExecutingDdl",
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
                        "ActionDimensionalUpdate.Error.DdlExecutionFailed",
                        targetDatabase.getName()),
                    e);
                totalErrors++;
                success = false;
                ddlPhaseFailed = true;
              }
            }
          } else {
            logBasic(
                BaseMessages.getString(PKG, "ActionDimensionalUpdate.Log.SkippingDdlExecution"));
          }
        } else {
          logBasic(BaseMessages.getString(PKG, "ActionDimensionalUpdate.Log.NoDdlNeeded"));
        }

        if (updateTargetDatabaseStructure && ddlPhaseFailed) {
          logError(BaseMessages.getString(PKG, "ActionDimensionalUpdate.Log.AbortingOnDdlFailure"));
          return finishExecution(result, false, Math.max(totalErrors, 1), dmModel);
        }
      }

      if (doNotUpdateTargetDatabase) {
        logBasic(BaseMessages.getString(PKG, "ActionDimensionalUpdate.Log.SkippingDataUpdate"));
        return finishExecution(result, success, totalErrors, dmModel);
      }

      DimensionalConfiguration pipelineConfig = dmModel.getConfigurationOrDefault();
      validatePipelineIntegerSettings(pipelineConfig);

      List<PipelineMeta> allPipelineMetas = new ArrayList<>();
      Date loadTimestamp = new Date();

      for (IDmTable table : DmUpdateExecutionSupport.orderTablesForPipelineExecution(tables)) {
        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionDimensionalUpdate.Log.GeneratingForTable",
                table.getName(),
                table.getTableType()));

        List<PipelineMeta> pipelineMetas =
            table.generateUpdatePipelines(
                getMetadataProvider(), getVariables(), dmModel, loadTimestamp);

        if (pipelineMetas == null || pipelineMetas.isEmpty()) {
          logError(
              BaseMessages.getString(
                  PKG, "ActionDimensionalUpdate.Error.GenerateFailed", table.getName()));
          totalErrors++;
          success = false;
          continue;
        }

        for (PipelineMeta pipelineMeta : pipelineMetas) {
          if (pipelineMeta == null) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionDimensionalUpdate.Error.GenerateFailed", table.getName()));
            totalErrors++;
            success = false;
            continue;
          }

          pipelineMeta.lookupReferencesAfterLoading();
          allPipelineMetas.add(pipelineMeta);

          String savedPipelineFile =
              DmGeneratedPipelineSupport.saveBeforeExecution(
                  pipelineConfig, getVariables(), pipelineMeta);
          if (!Utils.isEmpty(savedPipelineFile)) {
            logBasic(
                BaseMessages.getString(
                    PKG,
                    "ActionDimensionalUpdate.Log.SavedGeneratedPipeline",
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
                resolve(dataCatalogConnection),
                pipelineConfig.getTargetDatabase(),
                GeneratedPipelineMetadataConstants.MODEL_TYPE_DM,
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
              DmTargetDatabaseSupport.loadTargetDatabase(getMetadataProvider(), pipelineConfig);
          outcome =
              DvModelBulkUpdateExecutionSupport.executeStagingFileUpdate(
                  result,
                  dmModel.getName(),
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
                  resolve(dimensionalModelFile),
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
                  dmModel.getName(),
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

      return finishExecution(result, success, totalErrors, dmModel);

    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionDimensionalUpdate.Error.General"), e);
      result.setResult(false);
      result.setNrErrors(1);
      return result;
    }
  }

  private Result finishExecution(
      Result result, boolean success, int totalErrors, DimensionalModel dmModel) {
    if (publishToCatalog) {
      if (!publishModelToCatalog(dmModel)) {
        success = false;
        totalErrors++;
      }
    }
    result.setResult(success);
    result.setNrErrors(success ? 0 : (totalErrors > 0 ? totalErrors : 1));
    return result;
  }

  private boolean publishModelToCatalog(DimensionalModel dmModel) {
    try {
      String catalogConnection = resolveCatalogConnection(dmModel);
      if (Utils.isEmpty(catalogConnection)) {
        logError(
            BaseMessages.getString(PKG, "ActionDimensionalUpdate.Error.NoDataCatalogConnection"));
        return false;
      }
      DmCatalogPublisher.PublishResult publishResult =
          DmCatalogPublisher.publish(
              catalogConnection,
              dmModel,
              getVariables(),
              getMetadataProvider(),
              getName(),
              new DmCatalogPublisher.CatalogPublishLog() {
                @Override
                public void logBasic(String message) {
                  ActionDimensionalUpdate.this.logBasic(message);
                }

                @Override
                public void logError(String message, Throwable throwable) {
                  ActionDimensionalUpdate.this.logError(message, throwable);
                }
              });
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionDimensionalUpdate.Log.CatalogPublished",
              publishResult.getTableCount(),
              catalogConnection,
              publishResult.getErrorCount()));
      return publishResult.isSuccess();
    } catch (Exception e) {
      logError(
          BaseMessages.getString(PKG, "ActionDimensionalUpdate.Error.CatalogPublishFailed"), e);
      return false;
    }
  }

  private String resolveCatalogConnection(DimensionalModel dmModel) throws HopException {
    String actionConnection = resolve(dataCatalogConnection);
    if (!Utils.isEmpty(actionConnection)) {
      return actionConnection;
    }
    DimensionalConfiguration config = dmModel != null ? dmModel.getConfigurationOrDefault() : null;
    return DmSourceRecordDefinitionSupport.resolveCatalogConnection(
        config, null, getVariables(), getMetadataProvider());
  }

  private List<IDmTable> filterTables(List<IDmTable> tables) {
    List<IDmTable> result = new ArrayList<>();
    if (tables == null) {
      return result;
    }
    Set<String> selected = parseSelectedTableNames(resolve(selectedTableNames));
    for (IDmTable table : tables) {
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
      return "dimensional-model";
    }
    return modelName.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private void validatePipelineIntegerSettings(DimensionalConfiguration pipelineConfig)
      throws HopException {
    DvIntegerSettingValidationSupport.requirePositiveInteger(
        pipelineConfig.getTargetTableBatchSize(),
        getVariables(),
        DimensionalConfiguration.DEFAULT_TARGET_TABLE_BATCH_SIZE,
        BaseMessages.getString(
            DvIntegerSettingValidationSupport.class,
            "DvIntegerSettingValidation.Label.TargetTableBatchSize"));
    DvIntegerSettingValidationSupport.requirePositiveInteger(
        pipelineConfig.getTargetTableParallelCopies(),
        getVariables(),
        DimensionalConfiguration.DEFAULT_TARGET_TABLE_PARALLEL_COPIES,
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

  private DimensionalModel loadDimensionalModel(IHopMetadataProvider provider, IVariables variables)
      throws HopException {
    String realModelFile = variables.resolve(dimensionalModelFile);
    if (Utils.isEmpty(realModelFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionDimensionalUpdate.Error.NoModelFile"));
    }
    FileObject file = HopVfs.getFileObject(realModelFile, getVariables());
    Document document = XmlHandler.loadXmlFile(file);
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    if (rootNode == null) {
      rootNode = document.getDocumentElement();
    }

    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    model.setFilename(realModelFile);
    return model;
  }

  @Override
  public String[] getReferencedObjectDescriptions() {
    return new String[] {
      BaseMessages.getString(PKG, "ActionDimensionalUpdate.ReferencedObject.Description"),
    };
  }

  @Override
  public boolean[] isReferencedObjectEnabled() {
    return new boolean[] {StringUtils.isNotEmpty(dimensionalModelFile)};
  }

  @Override
  public IHasFilename loadReferencedObject(
      int index, IHopMetadataProvider metadataProvider, IVariables variables) throws HopException {
    return loadDimensionalModel(metadataProvider, variables);
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
