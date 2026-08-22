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
package org.apache.hop.datavault.workflow.actions.harvestmetadata;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.BaselineMode;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestRequest;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestService;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryPublisher;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryPublisher.PublishContext;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryPublisher.PublishResult;
import org.apache.hop.catalog.harvest.history.SchemaHarvestHistoryPublisher.PublishStatus;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Const;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.metrics.DvUpdateMetricsConstants;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * Harvests live source metadata for record definitions in a resource definition group, stores
 * snapshots and drift in OPS tables, and does not rewrite the working catalog. Drift does not fail
 * this action — only infrastructure errors do (pair with the schema gate for policy).
 */
@Action(
    id = "HARVEST_SOURCE_METADATA",
    name = "i18n::ActionHarvestSourceMetadata.Name",
    description = "i18n::ActionHarvestSourceMetadata.Description",
    image = "data-catalog.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionHarvestSourceMetadata.Keywords",
    documentationUrl = "/workflow/actions/harvestsourcemetadata.html")
@GuiPlugin(description = "Harvest Source Metadata action")
@Getter
@Setter
public class ActionHarvestSourceMetadata extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionHarvestSourceMetadata.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "HARVEST_SOURCE_METADATA_ACTION";

  /** Variable set after a successful harvest for downstream actions. */
  public static final String VAR_SCHEMA_HARVEST_RUN_ID =
      SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_RUN_ID;

  public static final String RESULT_ATTR_REPORT = "schemaHarvestReportText";

  public static final String DEFAULT_LOAD_ID =
      "${" + DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID + "}";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::ActionHarvestSourceMetadata.Group.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::ActionHarvestSourceMetadata.CatalogConnection.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.CatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String catalogConnection;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.COMBO,
      variables = false,
      comboValuesMethod = "getBaselineModeOptions",
      label = "i18n::ActionHarvestSourceMetadata.BaselineMode.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.BaselineMode.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String baselineMode = BaselineMode.WORKING_CATALOG.name();

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionHarvestSourceMetadata.BaselineVersion.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.BaselineVersion.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String baselineVersionTag;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionHarvestSourceMetadata.RecordSourceGroup.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.RecordSourceGroup.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordSourceGroupFilter;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionHarvestSourceMetadata.ConnectionFilter.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.ConnectionFilter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String connectionNameFilter;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionHarvestSourceMetadata.PersistHistory.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.PersistHistory.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean persistHistory = true;

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::ActionHarvestSourceMetadata.HistoryDatabase.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.HistoryDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String historyDatabase;

  @GuiWidgetElement(
      order = "0900",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionHarvestSourceMetadata.HistorySchema.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.HistorySchema.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String historySchema = "";

  @GuiWidgetElement(
      order = "1000",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionHarvestSourceMetadata.AutoCreateTables.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.AutoCreateTables.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean autoCreateTables = true;

  @GuiWidgetElement(
      order = "1100",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionHarvestSourceMetadata.PublishCatalogDefinitions.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.PublishCatalogDefinitions.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean publishCatalogDefinitions = true;

  @GuiWidgetElement(
      order = "1200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionHarvestSourceMetadata.FailOnPersistError.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.FailOnPersistError.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failOnPersistError = true;

  @GuiWidgetElement(
      order = "1300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionHarvestSourceMetadata.FailOnDiscoveryErrors.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.FailOnDiscoveryErrors.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failOnDiscoveryErrors = false;

  @GuiWidgetElement(
      order = "1400",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionHarvestSourceMetadata.ReportOutputPath.Label",
      toolTip = "i18n::ActionHarvestSourceMetadata.ReportOutputPath.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String reportOutputPath;

  public ActionHarvestSourceMetadata(String name) {
    super(name, "");
  }

  public ActionHarvestSourceMetadata() {
    this("");
  }

  @Override
  public Object clone() {
    return super.clone();
  }

  @Override
  public String getDialogClassName() {
    return ActionHarvestSourceMetadataDialog.class.getName();
  }

  /** Hop GUI comboValuesMethod contract: {@code (ILogChannel, IHopMetadataProvider) -> List}. */
  public List<String> getBaselineModeOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(BaselineMode.WORKING_CATALOG.name(), BaselineMode.CATALOG_VERSION.name());
  }

  @Override
  public Result execute(Result prevResult, int nr) throws HopException {
    Result result = prevResult != null ? prevResult : new Result();
    result.setResult(false);
    result.setNrErrors(0);

    String groupName = resolve(resourceDefinitionGroup);
    if (Utils.isEmpty(groupName)) {
      logError(BaseMessages.getString(PKG, "ActionHarvestSourceMetadata.Error.MissingGroup"));
      result.setNrErrors(1);
      return result;
    }

    BaselineMode mode = BaselineMode.WORKING_CATALOG;
    if (!Utils.isEmpty(baselineMode)) {
      try {
        mode = BaselineMode.valueOf(resolve(baselineMode).trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        mode = BaselineMode.WORKING_CATALOG;
      }
    }

    String workflowExecutionId =
        Const.NVL(getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID), "");

    HarvestRequest request =
        HarvestRequest.builder()
            .resourceGroupName(groupName)
            .catalogConnection(emptyToNull(resolve(catalogConnection)))
            .baselineMode(mode)
            .baselineVersionTag(emptyToNull(resolve(baselineVersionTag)))
            .recordSourceGroupFilter(emptyToNull(resolve(recordSourceGroupFilter)))
            .connectionNameFilter(emptyToNull(resolve(connectionNameFilter)))
            .workflowName(
                getParentWorkflow() != null ? getParentWorkflow().getWorkflowName() : null)
            .workflowExecutionId(emptyToNull(workflowExecutionId))
            .build();

    HarvestResult harvest;
    try {
      harvest = SchemaHarvestService.harvest(request, getLogChannel(), this, getMetadataProvider());
    } catch (HopException e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionHarvestSourceMetadata.Error.HarvestFailed", e.getMessage()),
          e);
      result.setNrErrors(1);
      return result;
    }

    String reportText = SchemaHarvestService.formatMarkdownReport(harvest);
    getExtensionDataMap().put(RESULT_ATTR_REPORT, reportText);
    if (getParentWorkflow() != null) {
      getParentWorkflow().getExtensionDataMap().put(RESULT_ATTR_REPORT, reportText);
      getParentWorkflow().setVariable(VAR_SCHEMA_HARVEST_RUN_ID, harvest.getHarvestRunId());
    }
    setVariable(VAR_SCHEMA_HARVEST_RUN_ID, harvest.getHarvestRunId());

    logBasic(
        BaseMessages.getString(
            PKG,
            "ActionHarvestSourceMetadata.Info.Complete",
            harvest.getStatus() != null ? harvest.getStatus().name() : "?",
            Integer.toString(harvest.subjectCount()),
            Integer.toString(harvest.subjectsWithChanges()),
            Integer.toString(harvest.changeCount())));

    writeReportFile(reportText, harvest);

    if (persistHistory) {
      String historyDb = resolveHistoryDatabase();
      if (Utils.isEmpty(historyDb)) {
        String msg =
            BaseMessages.getString(PKG, "ActionHarvestSourceMetadata.Error.NoHistoryDatabase");
        logError(msg);
        if (failOnPersistError) {
          result.setNrErrors(1);
          return result;
        }
      } else {
        PublishContext publishContext =
            new PublishContext(
                historyDb,
                resolve(historySchema),
                emptyToNull(resolve(catalogConnection)),
                publishCatalogDefinitions,
                true,
                autoCreateTables);
        PublishResult publishResult =
            SchemaHarvestHistoryPublisher.publish(
                getLogChannel(), harvest, publishContext, this, getMetadataProvider());
        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionHarvestSourceMetadata.Info.PersistResult",
                publishResult.status().name(),
                publishResult.message()));
        if (publishResult.status() == PublishStatus.FAILED && failOnPersistError) {
          result.setNrErrors(1);
          return result;
        }
      }
    }

    // Drift never fails by itself; optional fail when discovery had errors/partial.
    if (failOnDiscoveryErrors
        && (harvest.getStatus() == HarvestStatus.FAILED
            || harvest.getStatus() == HarvestStatus.PARTIAL)) {
      logError(
          BaseMessages.getString(
              PKG,
              "ActionHarvestSourceMetadata.Error.DiscoveryErrors",
              harvest.getStatus().name(),
              Integer.toString(harvest.errorCount())));
      result.setNrErrors(1);
      return result;
    }

    result.setResult(true);
    return result;
  }

  String resolveHistoryDatabase() {
    String configured = resolve(historyDatabase);
    if (!Utils.isEmpty(configured)) {
      return configured.trim();
    }
    String fromVar =
        resolve("${" + SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_DATABASE + "}");
    if (!Utils.isEmpty(fromVar) && !fromVar.contains("${")) {
      return fromVar.trim();
    }
    // Common retail/ops default when metadata is available.
    try {
      DatabaseMeta ops = getMetadataProvider().getSerializer(DatabaseMeta.class).load("OPS");
      if (ops != null) {
        return "OPS";
      }
    } catch (Exception ignored) {
      // fall through
    }
    return null;
  }

  private void writeReportFile(String reportText, HarvestResult harvest) {
    String path = resolve(reportOutputPath);
    if (Utils.isEmpty(path) || Utils.isEmpty(reportText)) {
      return;
    }
    try {
      String filePath = path;
      if (!path.toLowerCase().endsWith(".md") && !path.toLowerCase().endsWith(".html")) {
        String base =
            "schema-harvest-"
                + Const.NVL(harvest.getResourceGroupName(), "group")
                + "-"
                + Const.NVL(harvest.getHarvestRunId(), "run");
        if (!path.endsWith("/") && !path.endsWith("\\")) {
          filePath = path + "/" + base + ".md";
        } else {
          filePath = path + base + ".md";
        }
      }
      try (FileObject file = HopVfs.getFileObject(filePath)) {
        if (file.getParent() != null && !file.getParent().exists()) {
          file.getParent().createFolder();
        }
        try (var out = HopVfs.getOutputStream(file, false)) {
          out.write(reportText.getBytes(StandardCharsets.UTF_8));
        }
      }
      logBasic(
          BaseMessages.getString(PKG, "ActionHarvestSourceMetadata.Info.ReportWritten", filePath));
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionHarvestSourceMetadata.Error.ReportWriteFailed", e.getMessage()));
    }
  }

  private static String emptyToNull(String value) {
    if (Utils.isEmpty(value)) {
      return null;
    }
    return value.trim();
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  @Override
  public boolean isUnconditional() {
    return false;
  }
}
