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
 *
 */

package org.apache.hop.datavault.metadata.dimensional;

import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.datavault.metadata.DvTargetLoadConfigurationSupport;
import org.apache.hop.datavault.metadata.DvTargetLoadMode;
import org.apache.hop.datavault.metadata.IDvTargetLoadConfiguration;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;


/** Warehouse configuration for a dimensional model. */
@GuiPlugin
@Getter
@Setter
public class DimensionalConfiguration extends HopMetadataBase
    implements IHopMetadata, IDvTargetLoadConfiguration {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DIMENSIONAL_MODEL_DIALOG";
  public static final String GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID = "DIMENSIONAL_CONFIGURATION_COLUMNS_TAB";
  public static final String GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID =
      "DIMENSIONAL_CONFIGURATION_TARGET_LOAD_TAB";
  public static final String GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID =
      "DIMENSIONAL_CONFIGURATION_GENERATED_ARTIFACTS_TAB";

  public static final String DEFAULT_DIM_KEY_FIELD = "dim_key";
  public static final String DEFAULT_VERSION_FIELD = "version";
  public static final String DEFAULT_DATE_FROM_FIELD = "date_from";
  public static final String DEFAULT_DATE_TO_FIELD = "date_to";
  public static final String DEFAULT_LOAD_DATE_FIELD = "load_dt";
  public static final String DEFAULT_CURRENT_FLAG_FIELD = "is_current";
  public static final String DEFAULT_OPEN_START_SENTINEL = "1900-01-01 00:00:00";
  public static final String DEFAULT_OPEN_END_SENTINEL = "9999-12-31 23:59:59";
  public static final String DEFAULT_TARGET_TABLE_BATCH_SIZE = "1000";
  public static final String DEFAULT_TARGET_TABLE_PARALLEL_COPIES = "1";
  public static final String DEFAULT_DIMENSION_PIPELINE_NAME_PREFIX = "dm-dim-";
  public static final String DEFAULT_JUNK_DIMENSION_PIPELINE_NAME_PREFIX = "dm-junk-";
  public static final String DEFAULT_BRIDGE_PIPELINE_NAME_PREFIX = "dm-bridge-";
  public static final String DEFAULT_FACT_PIPELINE_NAME_PREFIX = "dm-fact-";
  public static final String DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX = "DM Bulk Update - ";

  public static DimensionalConfiguration createFromPluginDefaults() {
    DimensionalConfiguration configuration = new DimensionalConfiguration();
    DataVaultConfigSingleton.getConfig().getDimensionalDefaultFieldNames().applyTo(configuration);
    return configuration;
  }

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::DimensionalConfiguration.TargetDatabase.Label",
      toolTip = "i18n::DimensionalConfiguration.TargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String targetDatabase;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::DimensionalConfiguration.SourceDatabase.Label",
      toolTip = "i18n::DimensionalConfiguration.SourceDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceDatabase;

  @GuiWidgetElement(
      order = "0175",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::DimensionalConfiguration.DataCatalogConnection.Label",
      toolTip = "i18n::DimensionalConfiguration.DataCatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dataCatalogConnection;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FOLDER,
      label = "i18n::DimensionalConfiguration.StandardColumns.Label",
      toolTip = "i18n::DimensionalConfiguration.StandardColumns.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String standardColumnsFolder;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.DimKeyField.Label",
      toolTip = "i18n::DimensionalConfiguration.DimKeyField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dimKeyField = DEFAULT_DIM_KEY_FIELD;

  @GuiWidgetElement(
      order = "0220",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.VersionField.Label",
      toolTip = "i18n::DimensionalConfiguration.VersionField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String versionField = DEFAULT_VERSION_FIELD;

  @GuiWidgetElement(
      order = "0230",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.DateFromField.Label",
      toolTip = "i18n::DimensionalConfiguration.DateFromField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dateFromField = DEFAULT_DATE_FROM_FIELD;

  @GuiWidgetElement(
      order = "0240",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.DateToField.Label",
      toolTip = "i18n::DimensionalConfiguration.DateToField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dateToField = DEFAULT_DATE_TO_FIELD;

  @GuiWidgetElement(
      order = "0250",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.LoadDateField.Label",
      toolTip = "i18n::DimensionalConfiguration.LoadDateField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String loadDateField = DEFAULT_LOAD_DATE_FIELD;

  @GuiWidgetElement(
      order = "0260",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.CurrentFlagField.Label",
      toolTip = "i18n::DimensionalConfiguration.CurrentFlagField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String currentFlagField = DEFAULT_CURRENT_FLAG_FIELD;

  @GuiWidgetElement(
      order = "0270",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GeneratePrimaryKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratePrimaryKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean generatePrimaryKeys = false;

  @GuiWidgetElement(
      order = "0280",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GenerateForeignKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.GenerateForeignKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean generateForeignKeys = false;

  @HopMetadataProperty private String openStartSentinel = DEFAULT_OPEN_START_SENTINEL;

  @HopMetadataProperty private String openEndSentinel = DEFAULT_OPEN_END_SENTINEL;

  @GuiWidgetElement(
      order = "0510",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.TargetTableBatchSize.Label",
      toolTip = "i18n::DataVaultConfiguration.TargetTableBatchSize.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String targetTableBatchSize = DEFAULT_TARGET_TABLE_BATCH_SIZE;

  @GuiWidgetElement(
      order = "0511",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.TargetTableParallelCopies.Label",
      toolTip = "i18n::DataVaultConfiguration.TargetTableParallelCopies.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String targetTableParallelCopies = DEFAULT_TARGET_TABLE_PARALLEL_COPIES;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @GuiWidgetElement(
      order = "0512",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.TargetLoadMode.Label",
      toolTip = "i18n::DataVaultConfiguration.TargetLoadMode.ToolTip",
      comboValuesMethod = "getTargetLoadModeOptions",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty(key = "target_load_mode")
  private String targetLoadMode = DvTargetLoadMode.TABLE_OUTPUT.getCode();

  @GuiWidgetElement(
      order = "0513",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::DataVaultConfiguration.BulkLoadStagingFolder.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadStagingFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String bulkLoadStagingFolder = DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_STAGING_FOLDER;

  @GuiWidgetElement(
      order = "0514",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BulkLoadDelimiter.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadDelimiter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String bulkLoadDelimiter = DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_DELIMITER;

  @GuiWidgetElement(
      order = "0515",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BulkLoadEnclosure.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadEnclosure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String bulkLoadEnclosure = DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_ENCLOSURE;

  @GuiWidgetElement(
      order = "0516",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BulkLoadEncoding.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadEncoding.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String bulkLoadEncoding = DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_ENCODING;

  @GuiWidgetElement(
      order = "0517",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.BulkLoadLocalFileRequired.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadLocalFileRequired.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private boolean bulkLoadLocalFileRequired = true;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::DataVaultConfiguration.GeneratedPipelineFolder.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratedPipelineFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String generatedPipelineFolder;

  @GuiWidgetElement(
      order = "0610",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.DimensionPipelineNamePrefix.Label",
      toolTip = "i18n::DimensionalConfiguration.DimensionPipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String dimensionPipelineNamePrefix = DEFAULT_DIMENSION_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0620",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.JunkDimensionPipelineNamePrefix.Label",
      toolTip = "i18n::DimensionalConfiguration.JunkDimensionPipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String junkDimensionPipelineNamePrefix = DEFAULT_JUNK_DIMENSION_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0630",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.BridgePipelineNamePrefix.Label",
      toolTip = "i18n::DimensionalConfiguration.BridgePipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String bridgePipelineNamePrefix = DEFAULT_BRIDGE_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0640",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DimensionalConfiguration.FactPipelineNamePrefix.Label",
      toolTip = "i18n::DimensionalConfiguration.FactPipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String factPipelineNamePrefix = DEFAULT_FACT_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0650",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.GeneratedWorkflowNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratedWorkflowNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String generatedWorkflowNamePrefix = DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX;

  public List<String> getTargetLoadModeOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return DvTargetLoadConfigurationSupport.getTargetLoadModeOptions(
        log, metadataProvider, targetDatabase);
  }

  public String getTargetLoadMode() {
    return DvTargetLoadConfigurationSupport.getTargetLoadModeDescription(targetLoadMode);
  }

  public void setTargetLoadMode(String descriptionOrCode) {
    DvTargetLoadConfigurationSupport.TargetLoadModeHolder holder =
        new DvTargetLoadConfigurationSupport.TargetLoadModeHolder();
    holder.targetLoadMode = targetLoadMode;
    DvTargetLoadConfigurationSupport.setTargetLoadModeFromDescriptionOrCode(
        descriptionOrCode, holder);
    targetLoadMode = holder.targetLoadMode;
  }

  @Override
  public DvTargetLoadMode resolveTargetLoadMode() {
    return DvTargetLoadMode.lookupCode(targetLoadMode);
  }

  @Override
  public String resolveTargetTableCommitSize(IVariables variables) {
    return DvTargetLoadConfigurationSupport.resolveTargetTableCommitSize(
        targetTableBatchSize, DEFAULT_TARGET_TABLE_BATCH_SIZE, variables);
  }

  @Override
  public String resolveTargetTableParallelCopies(IVariables variables) {
    return DvTargetLoadConfigurationSupport.resolveTargetTableParallelCopies(
        targetTableParallelCopies, DEFAULT_TARGET_TABLE_PARALLEL_COPIES, variables);
  }

  @Override
  public String resolveBulkLoadStagingFolder(IVariables variables, String modelName) {
    return DvTargetLoadConfigurationSupport.resolveBulkLoadStagingFolder(
        bulkLoadStagingFolder,
        DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_STAGING_FOLDER,
        variables,
        modelName);
  }

  @Override
  public String resolveBulkLoadDelimiter(IVariables variables) {
    return DvTargetLoadConfigurationSupport.resolveBulkLoadTextSetting(
        bulkLoadDelimiter, DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_DELIMITER, variables);
  }

  @Override
  public String resolveBulkLoadEnclosure(IVariables variables) {
    return DvTargetLoadConfigurationSupport.resolveBulkLoadTextSetting(
        bulkLoadEnclosure, DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_ENCLOSURE, variables);
  }

  @Override
  public String resolveBulkLoadEncoding(IVariables variables) {
    return DvTargetLoadConfigurationSupport.resolveBulkLoadTextSetting(
        bulkLoadEncoding, DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_ENCODING, variables);
  }

  @Override
  public String resolveGeneratedWorkflowName(IVariables variables, String modelName) {
    return DvTargetLoadConfigurationSupport.resolveGeneratedWorkflowName(
        generatedWorkflowNamePrefix, DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX, variables, modelName);
  }

  public String resolveDimKeyField(IVariables variables) {
    return resolveField(dimKeyField, DEFAULT_DIM_KEY_FIELD, variables);
  }

  public String resolveVersionField(IVariables variables) {
    return resolveField(versionField, DEFAULT_VERSION_FIELD, variables);
  }

  public String resolveDateFromField(IVariables variables) {
    return resolveField(dateFromField, DEFAULT_DATE_FROM_FIELD, variables);
  }

  public String resolveDateToField(IVariables variables) {
    return resolveField(dateToField, DEFAULT_DATE_TO_FIELD, variables);
  }

  public String resolveLoadDateField(IVariables variables) {
    return resolveField(loadDateField, DEFAULT_LOAD_DATE_FIELD, variables);
  }

  public String resolveCurrentFlagField(IVariables variables) {
    return resolveField(currentFlagField, DEFAULT_CURRENT_FLAG_FIELD, variables);
  }

  public String buildDimensionPipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables, dimensionPipelineNamePrefix, DEFAULT_DIMENSION_PIPELINE_NAME_PREFIX, targetTableName);
  }

  public String buildJunkDimensionPipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables,
        junkDimensionPipelineNamePrefix,
        DEFAULT_JUNK_DIMENSION_PIPELINE_NAME_PREFIX,
        targetTableName);
  }

  public String buildBridgePipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables, bridgePipelineNamePrefix, DEFAULT_BRIDGE_PIPELINE_NAME_PREFIX, targetTableName);
  }

  public String buildFactPipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables, factPipelineNamePrefix, DEFAULT_FACT_PIPELINE_NAME_PREFIX, targetTableName);
  }

  private static String resolveField(String value, String defaultValue, IVariables variables) {
    String resolved = Utils.isEmpty(value) ? defaultValue : value;
    if (variables != null) {
      resolved = variables.resolve(resolved);
    }
    return resolved;
  }

  private static String buildPipelineName(
      IVariables variables, String configuredPrefix, String defaultPrefix, String targetTableName) {
    String prefix = Utils.isEmpty(configuredPrefix) ? defaultPrefix : configuredPrefix;
    if (variables != null) {
      prefix = variables.resolve(prefix);
    }
    StringBuilder name = new StringBuilder(prefix);
    if (!Utils.isEmpty(targetTableName)) {
      name.append(targetTableName);
    }
    return name.toString();
  }
}