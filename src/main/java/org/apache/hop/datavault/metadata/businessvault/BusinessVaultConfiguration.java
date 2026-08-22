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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.ArrayList;
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
import org.apache.hop.datavault.metadata.DvLoadCycleSupport;
import org.apache.hop.datavault.metadata.DvTargetLoadConfigurationSupport;
import org.apache.hop.datavault.metadata.DvTargetLoadMode;
import org.apache.hop.datavault.metadata.IDvTargetLoadConfiguration;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Configuration for Business Vault physical implementation and update strategy.
 *
 * <p>Project metadata that many {@link BusinessVaultModel} files can share. Older {@code .hbv}
 * files may still embed a copy inline; prefer a named reference via {@code configurationName}.
 */
@HopMetadata(
    key = "business-vault-configuration",
    name = "i18n::BusinessVaultConfiguration.name",
    description = "i18n::BusinessVaultConfiguration.description",
    image = "business-vault-model.svg",
    documentationUrl = "/metadata-types/business-vault-configuration.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
@GuiPlugin
public class BusinessVaultConfiguration extends HopMetadataBase
    implements IHopMetadata, IDvTargetLoadConfiguration {

  public static final String GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID =
      "BUSINESS_VAULT_CONFIGURATION_GENERAL_TAB";
  public static final String GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID =
      "BUSINESS_VAULT_CONFIGURATION_TARGET_LOAD_TAB";
  public static final String GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID =
      "BUSINESS_VAULT_CONFIGURATION_GENERATED_ARTIFACTS_TAB";

  public static final String DEFAULT_SCD2_PIPELINE_NAME_PREFIX = "bv-scd2-";
  public static final String DEFAULT_PIT_PIPELINE_NAME_PREFIX = "bv-pit-";
  public static final String DEFAULT_BUSINESS_TABLE_PIPELINE_NAME_PREFIX = "bv-biz-";
  public static final String DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX = "BV Bulk Update - ";
  public static final String DEFAULT_OPEN_START_SENTINEL = "1900-01-01 00:00:00";
  public static final String DEFAULT_OPEN_END_SENTINEL = "9999-12-31 23:59:59";
  public static final String DEFAULT_VALID_FROM_FIELD = "valid_from";
  public static final String DEFAULT_VALID_TO_FIELD = "valid_to";
  public static final String DEFAULT_TARGET_TABLE_BATCH_SIZE = "1000";
  public static final String DEFAULT_TARGET_TABLE_PARALLEL_COPIES = "1";

  @HopMetadataProperty private String description;

  @GuiWidgetElement(
      order = "0050",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::BusinessVaultConfiguration.TargetDatabase.Label",
      toolTip = "i18n::BusinessVaultConfiguration.TargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty(key = "targetDatabase")
  private String targetDatabase;

  /**
   * Default data catalog metadata connection for catalog publish and registry lookups. Same pattern
   * as {@code DataVaultConfiguration} / {@code DimensionalConfiguration}.
   */
  @GuiWidgetElement(
      order = "0060",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::BusinessVaultConfiguration.DataCatalogConnection.Label",
      toolTip = "i18n::BusinessVaultConfiguration.DataCatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String dataCatalogConnection;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.FOLDER,
      label = "i18n::BusinessVaultConfiguration.Scd2Defaults.Label",
      toolTip = "i18n::BusinessVaultConfiguration.Scd2Defaults.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String scd2DefaultsFolder;

  @GuiWidgetElement(
      order = "0160",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.FunctionalTimestampField.Label",
      toolTip = "i18n::BusinessVaultConfiguration.FunctionalTimestampField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String functionalTimestampField;

  @GuiWidgetElement(
      order = "0170",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.LoadDateFieldFallback.Label",
      toolTip = "i18n::BusinessVaultConfiguration.LoadDateFieldFallback.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String loadDateFieldFallback = "LOAD_DATE";

  @GuiWidgetElement(
      order = "0172",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.StoreLoadCycleId.Label",
      toolTip = "i18n::BusinessVaultConfiguration.StoreLoadCycleId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private boolean storeLoadCycleId;

  @GuiWidgetElement(
      order = "0174",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.LoadCycleIdField.Label",
      toolTip = "i18n::BusinessVaultConfiguration.LoadCycleIdField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String loadCycleIdField = DvLoadCycleSupport.DEFAULT_FIELD_NAME;

  @GuiWidgetElement(
      order = "0176",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.LoadCycleControlTable.Label",
      toolTip = "i18n::BusinessVaultConfiguration.LoadCycleControlTable.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String loadCycleControlTable = DvLoadCycleSupport.DEFAULT_CONTROL_TABLE;

  @GuiWidgetElement(
      order = "0180",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.ValidFromField.Label",
      toolTip = "i18n::BusinessVaultConfiguration.ValidFromField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String validFromField = DEFAULT_VALID_FROM_FIELD;

  @GuiWidgetElement(
      order = "0190",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.ValidToField.Label",
      toolTip = "i18n::BusinessVaultConfiguration.ValidToField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String validToField = DEFAULT_VALID_TO_FIELD;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GeneratePrimaryKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratePrimaryKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private boolean generatePrimaryKeys = false;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GenerateForeignKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.GenerateForeignKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private boolean generateForeignKeys = false;

  /**
   * Optional comma-separated Jinja macro library names. Empty means every enabled library in the
   * project metadata provider is in scope.
   */
  @GuiWidgetElement(
      order = "0220",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessVaultConfiguration.JinjaMacroLibraries.Label",
      toolTip = "i18n::BusinessVaultConfiguration.JinjaMacroLibraries.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String jinjaMacroLibraries;

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
  private String bulkLoadStagingFolder =
      DvTargetLoadConfigurationSupport.DEFAULT_BULK_LOAD_STAGING_FOLDER;

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
      label = "i18n::BusinessVaultConfiguration.Scd2PipelineNamePrefix.Label",
      toolTip = "i18n::BusinessVaultConfiguration.Scd2PipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String scd2PipelineNamePrefix = DEFAULT_SCD2_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0620",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.PitPipelineNamePrefix.Label",
      toolTip = "i18n::BusinessVaultConfiguration.PitPipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String pitPipelineNamePrefix = DEFAULT_PIT_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0630",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::BusinessVaultConfiguration.BusinessTablePipelineNamePrefix.Label",
      toolTip = "i18n::BusinessVaultConfiguration.BusinessTablePipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String businessTablePipelineNamePrefix = DEFAULT_BUSINESS_TABLE_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0640",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.GeneratedWorkflowNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratedWorkflowNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_ARTIFACTS_TAB_ID)
  @HopMetadataProperty
  private String generatedWorkflowNamePrefix = DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX;

  @HopMetadataProperty private String openStartSentinel = DEFAULT_OPEN_START_SENTINEL;

  @HopMetadataProperty private String openEndSentinel = DEFAULT_OPEN_END_SENTINEL;

  public List<String> getTargetLoadModeOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return DvTargetLoadConfigurationSupport.getTargetLoadModeOptions(
        log, metadataProvider, targetDatabase);
  }

  /**
   * Parsed library names from {@link #jinjaMacroLibraries}. Empty means every enabled library is in
   * scope.
   */
  public List<String> getJinjaMacroLibraryNames() {
    List<String> names = new ArrayList<>();
    if (Utils.isEmpty(jinjaMacroLibraries)) {
      return names;
    }
    for (String part : jinjaMacroLibraries.split(",")) {
      if (part != null && !Utils.isEmpty(part.trim())) {
        names.add(part.trim());
      }
    }
    return names;
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

  public String buildScd2PipelineName(
      IVariables variables, String targetTableName, String sourceName) {
    return buildPipelineName(
        variables,
        scd2PipelineNamePrefix,
        DEFAULT_SCD2_PIPELINE_NAME_PREFIX,
        targetTableName,
        sourceName);
  }

  public String buildPitPipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables, pitPipelineNamePrefix, DEFAULT_PIT_PIPELINE_NAME_PREFIX, targetTableName, null);
  }

  public String buildBusinessTablePipelineName(IVariables variables, String targetTableName) {
    return buildPipelineName(
        variables,
        businessTablePipelineNamePrefix,
        DEFAULT_BUSINESS_TABLE_PIPELINE_NAME_PREFIX,
        targetTableName,
        null);
  }

  private static String buildPipelineName(
      IVariables variables,
      String configuredPrefix,
      String defaultPrefix,
      String targetTableName,
      String sourceName) {
    String prefix = Utils.isEmpty(configuredPrefix) ? defaultPrefix : configuredPrefix;
    if (variables != null) {
      prefix = variables.resolve(prefix);
    }
    StringBuilder name = new StringBuilder(prefix);
    if (!Utils.isEmpty(targetTableName)) {
      name.append(targetTableName);
    }
    if (!Utils.isEmpty(sourceName)) {
      name.append("-").append(sourceName);
    }
    return name.toString();
  }
}
