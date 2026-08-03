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
package org.apache.hop.datavault.workflow.actions.exportdatalineage;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.openlineage.OpenLineageDestinationMode;
import org.apache.hop.datavault.openlineage.OpenLineageExportOptions;
import org.apache.hop.datavault.openlineage.OpenLineageExportResult;
import org.apache.hop.datavault.openlineage.OpenLineageExportService;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * Exports model-derived OpenLineage events (table + column lineage) for DV/BV/DM models in a
 * resource definition group to a folder and/or an OpenLineage HTTP endpoint (Marquez, Collibra, …).
 */
@Action(
    id = "EXPORT_DATA_LINEAGE",
    name = "i18n::ActionExportDataLineage.Name",
    description = "i18n::ActionExportDataLineage.Description",
    image = "execution-map.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionExportDataLineage.Keywords",
    documentationUrl = "/workflow/actions/exportdatalineage.html")
@GuiPlugin(description = "Export Data Lineage action")
@Getter
@Setter
public class ActionExportDataLineage extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionExportDataLineage.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "EXPORT_DATA_LINEAGE_ACTION";

  public static final String WIDGET_ID_RESOURCE_GROUP = "export-lineage-resource-group";
  public static final String WIDGET_ID_INCLUDE_DV = "export-lineage-include-dv";
  public static final String WIDGET_ID_INCLUDE_BV = "export-lineage-include-bv";
  public static final String WIDGET_ID_INCLUDE_DM = "export-lineage-include-dm";
  public static final String WIDGET_ID_INCLUDE_COLUMN = "export-lineage-include-column";
  public static final String WIDGET_ID_INCLUDE_OPS = "export-lineage-include-ops";
  public static final String WIDGET_ID_OPS_DATABASE = "export-lineage-ops-database";
  public static final String WIDGET_ID_OPS_SCHEMA = "export-lineage-ops-schema";
  public static final String WIDGET_ID_DESTINATION = "export-lineage-destination";
  public static final String WIDGET_ID_OUTPUT_FOLDER = "export-lineage-output-folder";
  public static final String WIDGET_ID_HTTP_URL = "export-lineage-http-url";
  public static final String WIDGET_ID_HTTP_API_KEY_HEADER = "export-lineage-http-api-key-header";
  public static final String WIDGET_ID_HTTP_API_KEY = "export-lineage-http-api-key";
  public static final String WIDGET_ID_JOB_NAMESPACE = "export-lineage-job-namespace";
  public static final String WIDGET_ID_DATASET_NAMESPACE = "export-lineage-dataset-namespace";
  public static final String WIDGET_ID_FAIL_ON_HTTP = "export-lineage-fail-on-http";
  public static final String WIDGET_ID_TIMEOUT_MS = "export-lineage-timeout-ms";

  @GuiWidgetElement(
      id = WIDGET_ID_RESOURCE_GROUP,
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::ActionExportDataLineage.Group.Label",
      toolTip = "i18n::ActionExportDataLineage.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  @GuiWidgetElement(
      id = WIDGET_ID_INCLUDE_DV,
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionExportDataLineage.IncludeDv.Label",
      toolTip = "i18n::ActionExportDataLineage.IncludeDv.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeDv = true;

  @GuiWidgetElement(
      id = WIDGET_ID_INCLUDE_BV,
      order = "0300",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionExportDataLineage.IncludeBv.Label",
      toolTip = "i18n::ActionExportDataLineage.IncludeBv.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeBv = true;

  @GuiWidgetElement(
      id = WIDGET_ID_INCLUDE_DM,
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionExportDataLineage.IncludeDm.Label",
      toolTip = "i18n::ActionExportDataLineage.IncludeDm.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeDm = true;

  @GuiWidgetElement(
      id = WIDGET_ID_INCLUDE_COLUMN,
      order = "0500",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionExportDataLineage.IncludeColumnLineage.Label",
      toolTip = "i18n::ActionExportDataLineage.IncludeColumnLineage.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeColumnLineage = true;

  @GuiWidgetElement(
      id = WIDGET_ID_INCLUDE_OPS,
      order = "0600",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionExportDataLineage.IncludeOperationalMetrics.Label",
      toolTip = "i18n::ActionExportDataLineage.IncludeOperationalMetrics.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeOperationalMetrics;

  @GuiWidgetElement(
      id = WIDGET_ID_OPS_DATABASE,
      order = "0700",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::ActionExportDataLineage.OpsDatabase.Label",
      toolTip = "i18n::ActionExportDataLineage.OpsDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String opsDatabase;

  @GuiWidgetElement(
      id = WIDGET_ID_OPS_SCHEMA,
      order = "0800",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionExportDataLineage.OpsSchema.Label",
      toolTip = "i18n::ActionExportDataLineage.OpsSchema.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String opsSchema;

  @GuiWidgetElement(
      id = WIDGET_ID_DESTINATION,
      order = "0900",
      type = GuiElementType.COMBO,
      variables = false,
      comboValuesMethod = "getDestinationModeOptions",
      label = "i18n::ActionExportDataLineage.DestinationMode.Label",
      toolTip = "i18n::ActionExportDataLineage.DestinationMode.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String destinationMode = OpenLineageDestinationMode.FILE.name();

  @GuiWidgetElement(
      id = WIDGET_ID_OUTPUT_FOLDER,
      order = "1000",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionExportDataLineage.OutputFolder.Label",
      toolTip = "i18n::ActionExportDataLineage.OutputFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String outputFolder;

  @GuiWidgetElement(
      id = WIDGET_ID_HTTP_URL,
      order = "1100",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionExportDataLineage.HttpUrl.Label",
      toolTip = "i18n::ActionExportDataLineage.HttpUrl.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String httpUrl;

  @GuiWidgetElement(
      id = WIDGET_ID_HTTP_API_KEY_HEADER,
      order = "1200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionExportDataLineage.HttpApiKeyHeader.Label",
      toolTip = "i18n::ActionExportDataLineage.HttpApiKeyHeader.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String httpApiKeyHeader;

  @GuiWidgetElement(
      id = WIDGET_ID_HTTP_API_KEY,
      order = "1300",
      type = GuiElementType.TEXT,
      variables = true,
      password = true,
      label = "i18n::ActionExportDataLineage.HttpApiKey.Label",
      toolTip = "i18n::ActionExportDataLineage.HttpApiKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String httpApiKey;

  @GuiWidgetElement(
      id = WIDGET_ID_JOB_NAMESPACE,
      order = "1400",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionExportDataLineage.JobNamespace.Label",
      toolTip = "i18n::ActionExportDataLineage.JobNamespace.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String jobNamespace;

  @GuiWidgetElement(
      id = WIDGET_ID_DATASET_NAMESPACE,
      order = "1450",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionExportDataLineage.DatasetNamespace.Label",
      toolTip = "i18n::ActionExportDataLineage.DatasetNamespace.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String datasetNamespace;

  @GuiWidgetElement(
      id = WIDGET_ID_FAIL_ON_HTTP,
      order = "1500",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionExportDataLineage.FailOnHttpError.Label",
      toolTip = "i18n::ActionExportDataLineage.FailOnHttpError.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean failOnHttpError = true;

  @GuiWidgetElement(
      id = WIDGET_ID_TIMEOUT_MS,
      order = "1600",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ActionExportDataLineage.TimeoutMs.Label",
      toolTip = "i18n::ActionExportDataLineage.TimeoutMs.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String timeoutMs = "30000";

  public ActionExportDataLineage() {
    super();
  }

  public ActionExportDataLineage(ActionExportDataLineage meta) {
    super(meta);
    this.resourceDefinitionGroup = meta.resourceDefinitionGroup;
    this.includeDv = meta.includeDv;
    this.includeBv = meta.includeBv;
    this.includeDm = meta.includeDm;
    this.includeColumnLineage = meta.includeColumnLineage;
    this.includeOperationalMetrics = meta.includeOperationalMetrics;
    this.opsDatabase = meta.opsDatabase;
    this.opsSchema = meta.opsSchema;
    this.destinationMode = meta.destinationMode;
    this.outputFolder = meta.outputFolder;
    this.httpUrl = meta.httpUrl;
    this.httpApiKeyHeader = meta.httpApiKeyHeader;
    this.httpApiKey = meta.httpApiKey;
    this.jobNamespace = meta.jobNamespace;
    this.datasetNamespace = meta.datasetNamespace;
    this.failOnHttpError = meta.failOnHttpError;
    this.timeoutMs = meta.timeoutMs;
  }

  @Override
  public Object clone() {
    return new ActionExportDataLineage(this);
  }

  @Override
  public String getDialogClassName() {
    return ActionExportDataLineageDialog.class.getName();
  }

  /**
   * Hop GUI {@code comboValuesMethod} contract: {@code (ILogChannel, IHopMetadataProvider) ->
   * List}.
   */
  public List<String> getDestinationModeOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        OpenLineageDestinationMode.FILE.name(),
        OpenLineageDestinationMode.HTTP.name(),
        OpenLineageDestinationMode.FILE_AND_HTTP.name());
  }

  @Override
  public Result execute(Result result, int nr) throws HopException {
    result.setResult(false);
    result.setNrErrors(1);

    if (Utils.isEmpty(resourceDefinitionGroup)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionExportDataLineage.Error.MissingGroup"));
    }

    OpenLineageDestinationMode mode = OpenLineageDestinationMode.parse(destinationMode);
    String resolvedFolder =
        Utils.isEmpty(outputFolder) ? null : getVariables().resolve(outputFolder);
    String resolvedHttpUrl = Utils.isEmpty(httpUrl) ? null : getVariables().resolve(httpUrl);

    if (mode.writesFiles() && Utils.isEmpty(resolvedFolder)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionExportDataLineage.Error.MissingOutputFolder"));
    }
    if (mode.postsHttp() && Utils.isEmpty(resolvedHttpUrl)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ActionExportDataLineage.Error.MissingHttpUrl"));
    }

    int timeout = 30_000;
    if (!Utils.isEmpty(timeoutMs)) {
      try {
        timeout = Integer.parseInt(getVariables().resolve(timeoutMs).trim());
      } catch (NumberFormatException e) {
        logBasic(
            BaseMessages.getString(
                PKG, "ActionExportDataLineage.Log.InvalidTimeout", timeoutMs, "30000"));
      }
    }

    OpenLineageExportOptions options =
        OpenLineageExportOptions.builder()
            .includeDv(includeDv)
            .includeBv(includeBv)
            .includeDm(includeDm)
            .includeColumnLineage(includeColumnLineage)
            .includeOperationalMetrics(includeOperationalMetrics)
            .destinationMode(mode)
            .outputFolder(resolvedFolder)
            .httpUrl(resolvedHttpUrl)
            .httpApiKeyHeader(
                Utils.isEmpty(httpApiKeyHeader) ? null : getVariables().resolve(httpApiKeyHeader))
            .httpApiKey(Utils.isEmpty(httpApiKey) ? null : getVariables().resolve(httpApiKey))
            .jobNamespace(Utils.isEmpty(jobNamespace) ? null : getVariables().resolve(jobNamespace))
            .datasetNamespace(
                Utils.isEmpty(datasetNamespace) ? null : getVariables().resolve(datasetNamespace))
            .opsDatabase(Utils.isEmpty(opsDatabase) ? null : getVariables().resolve(opsDatabase))
            .opsSchema(Utils.isEmpty(opsSchema) ? null : getVariables().resolve(opsSchema))
            .failOnHttpError(failOnHttpError)
            .timeoutMs(timeout)
            .build();

    logBasic(
        BaseMessages.getString(
            PKG, "ActionExportDataLineage.Log.Exporting", resourceDefinitionGroup, mode.name()));

    OpenLineageExportResult exportResult =
        OpenLineageExportService.exportFromGroup(
            resourceDefinitionGroup,
            options,
            getVariables(),
            getMetadataProvider(),
            getLogChannel());

    for (String warning : exportResult.getWarnings()) {
      logBasic(BaseMessages.getString(PKG, "ActionExportDataLineage.Log.Warning", warning));
    }

    if (exportResult.hasErrors() && failOnHttpError) {
      result.setNrErrors(exportResult.getHttpFailed() + exportResult.getErrors().size());
      result.setLogText(
          BaseMessages.getString(
              PKG,
              "ActionExportDataLineage.Result.Failed",
              exportResult.getEventCount(),
              exportResult.getHttpFailed()));
      return result;
    }

    result.setResult(true);
    result.setNrErrors(0);
    result.setLogText(
        BaseMessages.getString(
            PKG,
            "ActionExportDataLineage.Result.Success",
            exportResult.getEventCount(),
            exportResult.getFilesWritten(),
            exportResult.getHttpPosted()));
    return result;
  }
}
