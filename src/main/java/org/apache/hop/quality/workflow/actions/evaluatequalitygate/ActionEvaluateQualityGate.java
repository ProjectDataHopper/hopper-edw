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
package org.apache.hop.quality.workflow.actions.evaluatequalitygate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
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
import org.apache.hop.datavault.metrics.VaultUpdateExecutionSupport;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.SourceUsage;
import org.apache.hop.datavault.resourcedefinition.SourceUsageIndexBuilder;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.quality.alert.QualityAlertContext;
import org.apache.hop.quality.alert.QualityAlertSupport;
import org.apache.hop.quality.disposition.DispositionResult;
import org.apache.hop.quality.disposition.QualityDisposition;
import org.apache.hop.quality.disposition.QualityDispositionMode;
import org.apache.hop.quality.history.DataQualityHistoryPublisher;
import org.apache.hop.quality.history.DataQualityHistoryPublisher.PublishContext;
import org.apache.hop.quality.metadata.DataQualityRuleSetMeta;
import org.apache.hop.quality.model.DataQualityReport;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;
import org.apache.hop.quality.service.DataQualityMeasureService;
import org.apache.hop.quality.service.DataQualityReportFormatter;
import org.apache.hop.quality.workflow.actions.measuredataquality.ActionMeasureDataQuality;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * Applies a disposition policy to a data quality report. Prefer placing after Measure Data Quality;
 * when no prior report is available, re-measures using the same scope fields. After disposition,
 * optionally publishes alerts (log / ops table) per the Phase 2 sink matrix.
 */
@Action(
    id = "EVALUATE_QUALITY_GATE",
    name = "i18n::ActionEvaluateQualityGate.Name",
    description = "i18n::ActionEvaluateQualityGate.Description",
    image = "data-catalog.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionEvaluateQualityGate.Keywords",
    documentationUrl = "/workflow/actions/evaluatequalitygate.html")
@GuiPlugin(description = "Evaluate Quality Gate action")
@Getter
@Setter
public class ActionEvaluateQualityGate extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionEvaluateQualityGate.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "EVALUATE_QUALITY_GATE_ACTION";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.COMBO,
      label = "i18n::ActionEvaluateQualityGate.DispositionMode.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.DispositionMode.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dispositionMode = QualityDispositionMode.FAIL_ON_BLOCKING.name();

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEvaluateQualityGate.Remeasure.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.Remeasure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean remeasureIfNoPriorReport = true;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::ActionEvaluateQualityGate.CatalogConnection.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.CatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String catalogConnection;

  @GuiWidgetElement(
      order = "0350",
      type = GuiElementType.METADATA,
      metadata = DataQualityRuleSetMeta.class,
      label = "i18n::ActionEvaluateQualityGate.RuleSet.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.RuleSet.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String ruleSetName;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::ActionEvaluateQualityGate.Group.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      label = "i18n::ActionEvaluateQualityGate.RecordKeys.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.RecordKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordDefinitionKeys;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.TEXT,
      label = "i18n::ActionEvaluateQualityGate.NamespacePrefix.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.NamespacePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String namespacePrefix;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.TEXT,
      label = "i18n::ActionEvaluateQualityGate.AlertSinks.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.AlertSinks.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String alertSinks = QualityAlertSupport.DEFAULT_SINK_ID;

  @GuiWidgetElement(
      order = "0710",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEvaluateQualityGate.AlertOnGateFailure.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.AlertOnGateFailure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean alertOnGateFailure;

  @GuiWidgetElement(
      order = "0720",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::ActionEvaluateQualityGate.HistoryDatabase.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.HistoryDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String historyDatabase;

  @GuiWidgetElement(
      order = "0730",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionEvaluateQualityGate.HistorySchema.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.HistorySchema.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String historySchema = DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME;

  @GuiWidgetElement(
      order = "0740",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionEvaluateQualityGate.AutoCreateHistoryTables.Label",
      toolTip = "i18n::ActionEvaluateQualityGate.AutoCreateHistoryTables.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean autoCreateHistoryTables = true;

  public ActionEvaluateQualityGate() {
    super();
  }

  public ActionEvaluateQualityGate(ActionEvaluateQualityGate meta) {
    super(meta);
    this.dispositionMode = meta.dispositionMode;
    this.remeasureIfNoPriorReport = meta.remeasureIfNoPriorReport;
    this.catalogConnection = meta.catalogConnection;
    this.ruleSetName = meta.ruleSetName;
    this.resourceDefinitionGroup = meta.resourceDefinitionGroup;
    this.recordDefinitionKeys = meta.recordDefinitionKeys;
    this.namespacePrefix = meta.namespacePrefix;
    this.alertSinks = meta.alertSinks;
    this.alertOnGateFailure = meta.alertOnGateFailure;
    this.historyDatabase = meta.historyDatabase;
    this.historySchema = meta.historySchema;
    this.autoCreateHistoryTables = meta.autoCreateHistoryTables;
  }

  @Override
  public String getDialogClassName() {
    return ActionEvaluateQualityGateDialog.class.getName();
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    DataQualityReport report = findPriorReport();
    if (report == null && remeasureIfNoPriorReport) {
      report = remeasure();
    }
    if (report == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionEvaluateQualityGate.Error.NoReport"));
    }

    String formatted = DataQualityReportFormatter.format(report);
    if (!Utils.isEmpty(formatted)) {
      logBasic(formatted);
    }

    QualityDispositionMode mode = parseDisposition(dispositionMode);
    DispositionResult disposition = QualityDisposition.apply(report, mode);
    logBasic(disposition.getSummary());

    publishAlerts(report, disposition, mode);

    result.setResult(!disposition.isFailed());
    result.setNrErrors(disposition.isFailed() ? disposition.getNrErrors() : 0);
    if (disposition.isFailed()) {
      result.setLogText(disposition.getSummary());
      logError(disposition.getSummary());
    }
    return result;
  }

  private void publishAlerts(
      DataQualityReport report, DispositionResult disposition, QualityDispositionMode mode)
      throws HopException {
    PublishContext publishContext = null;
    String targetDb = resolveHistoryDatabase();
    if (!Utils.isEmpty(targetDb)) {
      publishContext =
          new PublishContext(
              targetDb,
              resolveHistorySchema(),
              catalogConnection,
              false,
              true,
              autoCreateHistoryTables);
    }

    String workflowExecutionId = resolveWorkflowExecutionId();
    QualityAlertContext context =
        QualityAlertContext.builder()
            .report(report)
            .disposition(disposition)
            .mode(mode)
            .log(getLogChannel())
            .variables(getVariables())
            .metadataProvider(getMetadataProvider())
            .publishContext(publishContext)
            .loadId(workflowExecutionId)
            .workflowName(resolveWorkflowName())
            .workflowExecutionId(workflowExecutionId)
            .build();

    QualityAlertSupport.publish(context, alertSinks, alertOnGateFailure);
  }

  String resolveHistoryDatabase() {
    String fromField = resolveOptional(historyDatabase);
    if (!Utils.isEmpty(fromField)) {
      return fromField;
    }
    return resolveOptional(
        getVariables() != null
            ? getVariables().getVariable(DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_DATABASE)
            : null);
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

  private String resolveOptional(String raw) {
    if (Utils.isEmpty(raw)) {
      return null;
    }
    String resolved = getVariables() != null ? resolve(raw.trim()) : raw.trim();
    if (Utils.isEmpty(resolved) || resolved.contains("${")) {
      return null;
    }
    return resolved;
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

  private DataQualityReport findPriorReport() {
    Object fromExtension = getExtensionDataMap().get(ActionMeasureDataQuality.RESULT_ATTR_REPORT);
    if (fromExtension instanceof DataQualityReport report) {
      return report;
    }
    // Parent workflow extension data (when measure ran earlier in the same workflow)
    if (getParentWorkflow() != null) {
      Object parent =
          getParentWorkflow()
              .getExtensionDataMap()
              .get(ActionMeasureDataQuality.RESULT_ATTR_REPORT);
      if (parent instanceof DataQualityReport report) {
        return report;
      }
    }
    return null;
  }

  private DataQualityReport remeasure() throws HopException {
    List<RecordDefinition> subjects = resolveSubjects();
    if (subjects.isEmpty()) {
      return null;
    }
    return DataQualityMeasureService.measureDefinitions(
        subjects,
        QualityEvaluationMode.AUTO,
        QualityLifecycle.AD_HOC,
        1000,
        getVariables(),
        getMetadataProvider(),
        getLogChannel(),
        Const.NVL(ruleSetName, "").trim());
  }

  private List<RecordDefinition> resolveSubjects() throws HopException {
    List<RecordDefinition> subjects = new ArrayList<>();
    if (!Utils.isEmpty(recordDefinitionKeys)) {
      if (Utils.isEmpty(catalogConnection)) {
        throw new HopException(
            BaseMessages.getString(PKG, "ActionEvaluateQualityGate.Error.MissingCatalog"));
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
            BaseMessages.getString(PKG, "ActionEvaluateQualityGate.Error.MissingCatalog"));
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
    java.util.LinkedHashMap<String, RecordDefinition> unique = new java.util.LinkedHashMap<>();
    for (RecordDefinition def : subjects) {
      unique.put(CatalogQualitySubjectSupport.subjectKey(def), def);
    }
    return new ArrayList<>(unique.values());
  }

  private static QualityDispositionMode parseDisposition(String raw) {
    if (Utils.isEmpty(raw)) {
      return QualityDispositionMode.FAIL_ON_BLOCKING;
    }
    try {
      return QualityDispositionMode.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return QualityDispositionMode.FAIL_ON_BLOCKING;
    }
  }

  @Override
  public IAction clone() {
    return new ActionEvaluateQualityGate(this);
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }
}
