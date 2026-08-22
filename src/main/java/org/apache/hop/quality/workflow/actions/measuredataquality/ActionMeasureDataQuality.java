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
package org.apache.hop.quality.workflow.actions.measuredataquality;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.quality.CatalogQualitySubjectSupport;
import org.apache.hop.core.Const;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metrics.DvUpdateMetricsConstants;
import org.apache.hop.datavault.metrics.VaultUpdateExecutionSupport;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.SourceUsage;
import org.apache.hop.datavault.resourcedefinition.SourceUsageIndexBuilder;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.quality.history.DataQualityHistoryPublisher;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishContext;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishResult;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishStatus;
import org.apache.hop.quality.metadata.DataQualityRuleSetMeta;
import org.apache.hop.quality.model.DataQualityReport;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;
import org.apache.hop.quality.service.DataQualityMeasureService;
import org.apache.hop.quality.service.DataQualityReportFormatter;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * Measures data quality rules against catalog subjects. Does not fail the workflow for rule
 * findings — only infrastructure errors fail this action. Pair with Evaluate Quality Gate.
 */
@Action(
    id = "MEASURE_DATA_QUALITY",
    name = "i18n::ActionMeasureDataQuality.Name",
    description = "i18n::ActionMeasureDataQuality.Description",
    image = "data-catalog.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionMeasureDataQuality.Keywords",
    documentationUrl = "/workflow/actions/measuredataquality.html")
@GuiPlugin(description = "Measure Data Quality action")
@Getter
@Setter
public class ActionMeasureDataQuality extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionMeasureDataQuality.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "MEASURE_DATA_QUALITY_ACTION";
  public static final String RESULT_ATTR_REPORT = "dataQualityReportText";

  /** Default load id variable expression (matches Begin Vault Update / metrics constants). */
  public static final String DEFAULT_LOAD_ID =
      "${" + DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID + "}";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::ActionMeasureDataQuality.CatalogConnection.Label",
      toolTip = "i18n::ActionMeasureDataQuality.CatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String catalogConnection;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.METADATA,
      metadata = DataQualityRuleSetMeta.class,
      label = "i18n::ActionMeasureDataQuality.RuleSet.Label",
      toolTip = "i18n::ActionMeasureDataQuality.RuleSet.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String ruleSetName;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::ActionMeasureDataQuality.Group.Label",
      toolTip = "i18n::ActionMeasureDataQuality.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::ActionMeasureDataQuality.RecordKeys.Label",
      toolTip = "i18n::ActionMeasureDataQuality.RecordKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordDefinitionKeys;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      label = "i18n::ActionMeasureDataQuality.NamespacePrefix.Label",
      toolTip = "i18n::ActionMeasureDataQuality.NamespacePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String namespacePrefix;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.COMBO,
      label = "i18n::ActionMeasureDataQuality.EvaluationMode.Label",
      toolTip = "i18n::ActionMeasureDataQuality.EvaluationMode.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String evaluationMode = QualityEvaluationMode.AUTO.name();

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.COMBO,
      label = "i18n::ActionMeasureDataQuality.Lifecycle.Label",
      toolTip = "i18n::ActionMeasureDataQuality.Lifecycle.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String lifecycle = QualityLifecycle.PRE_UPDATE.name();

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.TEXT,
      label = "i18n::ActionMeasureDataQuality.SampleLimit.Label",
      toolTip = "i18n::ActionMeasureDataQuality.SampleLimit.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sampleLimit = "1000";

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionMeasureDataQuality.PersistHistory.Label",
      toolTip = "i18n::ActionMeasureDataQuality.PersistHistory.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean persistHistory;

  @GuiWidgetElement(
      order = "0810",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::ActionMeasureDataQuality.HistoryDatabase.Label",
      toolTip = "i18n::ActionMeasureDataQuality.HistoryDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String historyDatabase;

  @GuiWidgetElement(
      order = "0820",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionMeasureDataQuality.HistorySchema.Label",
      toolTip = "i18n::ActionMeasureDataQuality.HistorySchema.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String historySchema = DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME;

  @GuiWidgetElement(
      order = "0830",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionMeasureDataQuality.LoadId.Label",
      toolTip = "i18n::ActionMeasureDataQuality.LoadId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String loadId = DEFAULT_LOAD_ID;

  @GuiWidgetElement(
      order = "0840",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionMeasureDataQuality.AutoCreateTables.Label",
      toolTip = "i18n::ActionMeasureDataQuality.AutoCreateTables.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean autoCreateTables = true;

  @GuiWidgetElement(
      order = "0850",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionMeasureDataQuality.PublishCatalogDefinitions.Label",
      toolTip = "i18n::ActionMeasureDataQuality.PublishCatalogDefinitions.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean publishCatalogDefinitions = true;

  @GuiWidgetElement(
      order = "0860",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionMeasureDataQuality.FailOnPersistError.Label",
      toolTip = "i18n::ActionMeasureDataQuality.FailOnPersistError.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failOnPersistError;

  public ActionMeasureDataQuality() {
    super();
  }

  public ActionMeasureDataQuality(ActionMeasureDataQuality meta) {
    super(meta);
    this.catalogConnection = meta.catalogConnection;
    this.ruleSetName = meta.ruleSetName;
    this.resourceDefinitionGroup = meta.resourceDefinitionGroup;
    this.recordDefinitionKeys = meta.recordDefinitionKeys;
    this.namespacePrefix = meta.namespacePrefix;
    this.evaluationMode = meta.evaluationMode;
    this.lifecycle = meta.lifecycle;
    this.sampleLimit = meta.sampleLimit;
    this.persistHistory = meta.persistHistory;
    this.historyDatabase = meta.historyDatabase;
    this.historySchema = meta.historySchema;
    this.loadId = meta.loadId;
    this.autoCreateTables = meta.autoCreateTables;
    this.publishCatalogDefinitions = meta.publishCatalogDefinitions;
    this.failOnPersistError = meta.failOnPersistError;
  }

  @Override
  public String getDialogClassName() {
    return ActionMeasureDataQualityDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    List<RecordDefinition> subjects = resolveSubjects();
    if (subjects.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionMeasureDataQuality.Error.NoSubjects"));
    }

    QualityEvaluationMode mode = parseMode(evaluationMode);
    QualityLifecycle life = parseLifecycle(lifecycle);
    int limit = parseSampleLimit(sampleLimit);

    DataQualityReport report =
        DataQualityMeasureService.measureDefinitions(
            subjects,
            mode,
            life,
            limit,
            getVariables(),
            getMetadataProvider(),
            getLogChannel(),
            Const.NVL(ruleSetName, "").trim());

    String formatted = DataQualityReportFormatter.format(report);
    if (!Utils.isEmpty(formatted)) {
      logBasic(formatted);
    }
    // Hand off to Evaluate Quality Gate via workflow extension data (same run).
    result.setLogText(formatted);
    getExtensionDataMap().put(RESULT_ATTR_REPORT, report);
    if (getParentWorkflow() != null) {
      getParentWorkflow().getExtensionDataMap().put(RESULT_ATTR_REPORT, report);
    }

    if (persistHistory) {
      PublishResult publishResult = persistQualityHistory(report, subjects);
      if (publishResult != null
          && publishResult.status() == PublishStatus.FAILED
          && failOnPersistError) {
        // Error already logged in persistQualityHistory — do not log twice.
        result.setResult(false);
        result.setNrErrors(Math.max(1, result.getNrErrors()));
        return result;
      }
    }

    if (report.hasInfraErrors()) {
      result.setResult(false);
      result.setNrErrors(Math.max(1, report.getInfraErrors().size()));
      logError(
          BaseMessages.getString(PKG, "ActionMeasureDataQuality.Error.InfraErrors")
              + ": "
              + String.join("; ", report.getInfraErrors()));
      return result;
    }

    // Measure never fails on rule findings.
    result.setResult(true);
    result.setNrErrors(0);
    logBasic(
        BaseMessages.getString(
            PKG,
            "ActionMeasureDataQuality.Info.Complete",
            String.valueOf(report.getFindingCount()),
            String.valueOf(subjects.size())));
    return result;
  }

  private PublishResult persistQualityHistory(
      DataQualityReport report, List<RecordDefinition> subjects) {
    try {
      String targetDatabase = resolveHistoryDatabase(subjects, report);
      if (Utils.isEmpty(targetDatabase)) {
        String msg =
            BaseMessages.getString(PKG, "ActionMeasureDataQuality.Error.NoHistoryDatabase");
        logError(msg);
        return new PublishResult(PublishStatus.FAILED, msg);
      }
      logBasic(
          BaseMessages.getString(
              PKG, "ActionMeasureDataQuality.Info.HistoryDatabaseResolved", targetDatabase));

      String schema = resolveHistorySchema();
      String resolvedLoadId = resolveLoadId();
      String workflowName = resolveWorkflowName();
      String workflowExecutionId = resolveWorkflowExecutionId();

      boolean publishCatalog =
          publishCatalogDefinitions && !Utils.isEmpty(Const.NVL(catalogConnection, "").trim());

      PublishContext context =
          new PublishContext(
              targetDatabase, schema, catalogConnection, publishCatalog, true, autoCreateTables);

      PublishResult publishResult =
          DataQualityHistoryPublisher.publish(
              getLogChannel(),
              report,
              context,
              resolvedLoadId,
              workflowName,
              workflowExecutionId,
              getVariables(),
              getMetadataProvider());
      if (publishResult.status() == PublishStatus.FAILED) {
        logError(
            BaseMessages.getString(
                PKG, "ActionMeasureDataQuality.Error.PersistFailed", publishResult.message()));
      } else {
        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionMeasureDataQuality.Info.PersistResult",
                publishResult.status().name(),
                publishResult.message()));
      }
      return publishResult;
    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      logError(BaseMessages.getString(PKG, "ActionMeasureDataQuality.Error.PersistFailed", msg), e);
      return new PublishResult(PublishStatus.FAILED, msg);
    }
  }

  String resolveHistoryDatabase(List<RecordDefinition> subjects, DataQualityReport report) {
    String fromField = resolveOptional(historyDatabase);
    if (!Utils.isEmpty(fromField)) {
      return fromField;
    }
    String fromVar =
        resolveOptional(
            getVariables() != null
                ? getVariables()
                    .getVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_DATABASE)
                : null);
    if (!Utils.isEmpty(fromVar)) {
      return fromVar;
    }
    // First successfully profiled subject physical DB (measure order).
    if (subjects != null && report != null) {
      for (RecordDefinition def : subjects) {
        if (def == null) {
          continue;
        }
        String key = CatalogQualitySubjectSupport.subjectKey(def);
        if (!report.getProfilesBySubject().containsKey(key)) {
          continue;
        }
        String db = physicalDatabaseName(def);
        if (!Utils.isEmpty(db)) {
          return db;
        }
      }
    }
    // Fallback: first subject with a physical table.
    if (subjects != null) {
      for (RecordDefinition def : subjects) {
        String db = physicalDatabaseName(def);
        if (!Utils.isEmpty(db)) {
          return db;
        }
      }
    }
    return null;
  }

  String resolveHistorySchema() {
    String fromField = resolveOptional(historySchema);
    if (!Utils.isEmpty(fromField)) {
      return fromField;
    }
    String fromVar =
        resolveOptional(
            getVariables() != null
                ? getVariables().getVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_SCHEMA)
                : null);
    if (!Utils.isEmpty(fromVar)) {
      return fromVar;
    }
    return DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME;
  }

  String resolveLoadId() {
    String expression = Utils.isEmpty(loadId) ? DEFAULT_LOAD_ID : loadId;
    String resolved = resolveOptional(expression);
    if (!Utils.isEmpty(resolved)) {
      return resolved;
    }
    return VaultUpdateExecutionSupport.resolveExecutionId(
        getVariables(),
        VaultUpdateExecutionSupport.defaultExecutionIdVariableName(),
        true,
        VaultUpdateExecutionSupport.resolveWorkflowLogChannelId(getParentWorkflow()));
  }

  private String resolveWorkflowExecutionId() {
    return VaultUpdateExecutionSupport.resolveExecutionId(
        getVariables(),
        VaultUpdateExecutionSupport.defaultExecutionIdVariableName(),
        true,
        VaultUpdateExecutionSupport.resolveWorkflowLogChannelId(getParentWorkflow()));
  }

  private String resolveWorkflowName() {
    if (getParentWorkflow() != null && getParentWorkflow().getWorkflowMeta() != null) {
      return getParentWorkflow().getWorkflowMeta().getName();
    }
    return null;
  }

  private String resolveOptional(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    String resolved = getVariables() != null ? getVariables().resolve(raw.trim()) : raw.trim();
    if (Utils.isEmpty(resolved) || resolved.contains("${")) {
      return null;
    }
    return resolved;
  }

  private static String physicalDatabaseName(RecordDefinition def) {
    if (def == null) {
      return null;
    }
    PhysicalTableRef table = def.getPhysicalTable();
    if (table == null || Utils.isEmpty(table.getDatabaseMetaName())) {
      return null;
    }
    return table.getDatabaseMetaName().trim();
  }

  private List<RecordDefinition> resolveSubjects() throws HopException {
    List<RecordDefinition> subjects = new ArrayList<>();
    if (!Utils.isEmpty(recordDefinitionKeys)) {
      if (Utils.isEmpty(catalogConnection)) {
        throw new HopException(
            BaseMessages.getString(PKG, "ActionMeasureDataQuality.Error.MissingCatalog"));
      }
      List<String> keys =
          Arrays.stream(recordDefinitionKeys.split("[,;\\n]"))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toList());
      subjects.addAll(
          CatalogQualitySubjectSupport.loadByKeys(
              catalogConnection, keys, getVariables(), getMetadataProvider()));
    }
    if (!Utils.isEmpty(namespacePrefix)) {
      if (Utils.isEmpty(catalogConnection)) {
        throw new HopException(
            BaseMessages.getString(PKG, "ActionMeasureDataQuality.Error.MissingCatalog"));
      }
      subjects.addAll(
          CatalogQualitySubjectSupport.loadByNamespacePrefix(
              catalogConnection, namespacePrefix, getVariables(), getMetadataProvider()));
    }
    if (!Utils.isEmpty(resourceDefinitionGroup)) {
      ValidationModels models =
          ResourceDefinitionGroupResolver.resolve(
              ResourceDefinitionGroupResolver.loadGroup(
                  resourceDefinitionGroup, getMetadataProvider()),
              getVariables(),
              getMetadataProvider());
      var usageIndex = SourceUsageIndexBuilder.build(models, getVariables());
      String defaultCatalog =
          !Utils.isEmpty(catalogConnection)
              ? catalogConnection
              : models.group() != null ? models.group().getDataCatalogConnection() : null;
      String defaultNamespace =
          org.apache.hop.datavault.catalog.DvCatalogNamespaces.projectSourcesNamespace(
              getVariables());
      for (var entry : usageIndex.entrySet()) {
        String catalog = defaultCatalog;
        for (SourceUsage usage : entry.getValue()) {
          if (!Utils.isEmpty(usage.catalogConnection())) {
            catalog = usage.catalogConnection();
            break;
          }
        }
        if (Utils.isEmpty(catalog)) {
          continue;
        }
        var resolvedKey =
            SourceUsageIndexBuilder.resolveKey(
                entry.getKey(), catalog, getVariables(), defaultNamespace);
        RecordDefinition definition =
            org.apache.hop.catalog.registry.RecordDefinitionRegistry.getInstance()
                .read(catalog, resolvedKey, getVariables(), getMetadataProvider());
        if (definition != null) {
          subjects.add(definition);
        }
      }
    }
    // de-dupe by subject key
    java.util.LinkedHashMap<String, RecordDefinition> unique = new java.util.LinkedHashMap<>();
    for (RecordDefinition def : subjects) {
      unique.put(CatalogQualitySubjectSupport.subjectKey(def), def);
    }
    return new ArrayList<>(unique.values());
  }

  private static QualityEvaluationMode parseMode(String raw) {
    if (Utils.isEmpty(raw)) {
      return QualityEvaluationMode.AUTO;
    }
    try {
      return QualityEvaluationMode.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return QualityEvaluationMode.AUTO;
    }
  }

  private static QualityLifecycle parseLifecycle(String raw) {
    if (Utils.isEmpty(raw)) {
      return QualityLifecycle.PRE_UPDATE;
    }
    try {
      return QualityLifecycle.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return QualityLifecycle.PRE_UPDATE;
    }
  }

  private static int parseSampleLimit(String raw) {
    try {
      return Math.max(1, Integer.parseInt(org.apache.hop.core.Const.NVL(raw, "1000").trim()));
    } catch (Exception e) {
      return 1000;
    }
  }

  @Override
  public IAction clone() {
    return new ActionMeasureDataQuality(this);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }
}
