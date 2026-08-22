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
package org.apache.hop.datavault.metadata;

import java.util.Arrays;
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
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Configuration for Data Vault 2.0 physical implementation and update strategy.
 *
 * <p>Project metadata that many {@link DataVaultModel} files can share. Older {@code .hdv} files
 * may still embed a copy inline; prefer a named reference via {@code configurationName}.
 */
@HopMetadata(
    key = "data-vault-configuration",
    name = "i18n::DataVaultConfiguration.name",
    description = "i18n::DataVaultConfiguration.description",
    image = "datavault-configuration.svg",
    documentationUrl = "/metadata-types/data-vault-configuration.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
@GuiPlugin
public class DataVaultConfiguration extends HopMetadataBase
    implements IHopMetadata, IDvTargetLoadConfiguration {

  public static final String GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID =
      "DATAVAULT_CONFIGURATION_GENERAL_TAB";

  public static final String GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID =
      "DATAVAULT_CONFIGURATION_UNKNOWN_TAB";

  public static final String GUI_PLUGIN_ELEMENT_INVALID_TAB_ID =
      "DATAVAULT_CONFIGURATION_INVALID_TAB";

  public static final String GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID =
      "DATAVAULT_CONFIGURATION_ORPHAN_TAB";

  public static final String GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID =
      "DATAVAULT_CONFIGURATION_COLUMNS_TAB";

  public static final String GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID =
      "DATAVAULT_CONFIGURATION_TARGET_LOAD_TAB";

  public static final String GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID =
      "DATAVAULT_CONFIGURATION_GENERATED_PIPELINES_TAB";

  /**
   * Default commit size used by {@link
   * org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta}.
   */
  public static final String DEFAULT_TARGET_TABLE_BATCH_SIZE = "1000";

  /** Default number of parallel Table Output copies writing to the target table. */
  public static final String DEFAULT_TARGET_TABLE_PARALLEL_COPIES = "1";

  /** Default in-memory row buffer for generated Sort Rows transforms (Hop SortRowsMeta default). */
  public static final String DEFAULT_SORT_ROWS_SIZE = "1000000";

  public static final String DEFAULT_HUB_PIPELINE_NAME_PREFIX = "hub-";
  public static final String DEFAULT_LINK_PIPELINE_NAME_PREFIX = "link-";
  public static final String DEFAULT_SATELLITE_PIPELINE_NAME_PREFIX = "sat-";
  public static final String DEFAULT_STS_PIPELINE_NAME_PREFIX = "sts-";
  public static final String DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX = "DV Bulk Update - ";

  public static final String DEFAULT_BULK_LOAD_STAGING_FOLDER = "${java.io.tmpdir}/dv2/bulk/";
  public static final String DEFAULT_BULK_LOAD_DELIMITER = ",";
  public static final String DEFAULT_BULK_LOAD_ENCLOSURE = "\"";
  public static final String DEFAULT_BULK_LOAD_ENCODING = "UTF-8";

  @HopMetadataProperty private String description;

  /**
   * The target database (DatabaseMeta) in which the Data Vault will be implemented. This makes the
   * configuration aware of the destination RDBMS for quoting, DDL, etc.
   */
  @GuiWidgetElement(
      order = "0050",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::DataVaultConfiguration.TargetDatabase.Label",
      toolTip = "i18n::DataVaultConfiguration.TargetDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty(key = "targetDatabase")
  private String targetDatabase;

  @GuiWidgetElement(
      order = "0060",
      type = GuiElementType.METADATA,
      metadata = DataCatalogMeta.class,
      label = "i18n::DataVaultConfiguration.DataCatalogConnection.Label",
      toolTip = "i18n::DataVaultConfiguration.DataCatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String dataCatalogConnection;

  // --- Hashing Strategy ---
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashAlgorithm.Label",
      toolTip = "i18n::DataVaultConfiguration.HashAlgorithm.ToolTip",
      comboValuesMethod = "getHashAlgorithmOptions",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String hashAlgorithm = HashAlgorithm.MD5.name();

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @GuiWidgetElement(
      order = "0110",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashKeyDataType.Label",
      toolTip = "i18n::DataVaultConfiguration.HashKeyDataType.ToolTip",
      comboValuesMethod = "getHashKeyDataTypeOptions",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String hashKeyDataType = HashKeyDataType.HEX.name();

  // --- Unknown / ghost record handling (common DV pattern) ---
  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GenerateUnknownRecord.Label",
      toolTip = "i18n::DataVaultConfiguration.GenerateUnknownRecord.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private boolean generateUnknownRecord = true;

  @GuiWidgetElement(
      order = "0210",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.UnknownBusinessKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownBusinessKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private String unknownBusinessKeyValue;

  @GuiWidgetElement(
      order = "0220",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.UnknownHashKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownHashKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private String unknownHashKeyValue;

  @GuiWidgetElement(
      order = "0230",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.UnknownLinkHashKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownLinkHashKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private String unknownLinkHashKeyValue;

  @GuiWidgetElement(
      order = "0240",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.UnknownRecordSource.Label",
      toolTip = "i18n::DataVaultConfiguration.UnknownRecordSource.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_UNKNOWN_TAB_ID)
  @HopMetadataProperty
  private String unknownRecordSource;

  // --- Invalid / error record handling ---
  @GuiWidgetElement(
      order = "0250",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GenerateInvalidRecord.Label",
      toolTip = "i18n::DataVaultConfiguration.GenerateInvalidRecord.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private boolean generateInvalidRecord = true;

  @GuiWidgetElement(
      order = "0260",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InvalidBusinessKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.InvalidBusinessKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private String invalidBusinessKeyValue;

  @GuiWidgetElement(
      order = "0270",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InvalidHashKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.InvalidHashKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private String invalidHashKeyValue;

  @GuiWidgetElement(
      order = "0280",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InvalidLinkHashKeyValue.Label",
      toolTip = "i18n::DataVaultConfiguration.InvalidLinkHashKeyValue.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private String invalidLinkHashKeyValue;

  @GuiWidgetElement(
      order = "0290",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InvalidRecordSource.Label",
      toolTip = "i18n::DataVaultConfiguration.InvalidRecordSource.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_INVALID_TAB_ID)
  @HopMetadataProperty
  private String invalidRecordSource;

  // --- Optional orphan handling (child-before-parent / incomplete identity) ---
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.OrphanPolicy.Label",
      toolTip = "i18n::DataVaultConfiguration.OrphanPolicy.ToolTip",
      comboValuesMethod = "getOrphanPolicyOptions",
      parentId = GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID)
  @HopMetadataProperty
  private String orphanPolicy = DvOrphanPolicy.PASS.name();

  @GuiWidgetElement(
      order = "0310",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InferredRecordSource.Label",
      toolTip = "i18n::DataVaultConfiguration.InferredRecordSource.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID)
  @HopMetadataProperty
  private String inferredRecordSource;

  @GuiWidgetElement(
      order = "0320",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.StoreInferredFlag.Label",
      toolTip = "i18n::DataVaultConfiguration.StoreInferredFlag.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID)
  @HopMetadataProperty
  private boolean storeInferredFlag;

  @GuiWidgetElement(
      order = "0330",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.InferredFlagField.Label",
      toolTip = "i18n::DataVaultConfiguration.InferredFlagField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID)
  @HopMetadataProperty
  private String inferredFlagField;

  @GuiWidgetElement(
      order = "0340",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.QuarantineTable.Label",
      toolTip = "i18n::DataVaultConfiguration.QuarantineTable.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ORPHAN_TAB_ID)
  @HopMetadataProperty
  private String quarantineTable;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @GuiWidgetElement(
      order = "0120",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.HashContentCasing.Label",
      toolTip = "i18n::DataVaultConfiguration.HashContentCasing.ToolTip",
      comboValuesMethod = "getHashContentCasingOptions",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String hashContentCasing = HashContentCasing.UPPER.name();

  @GuiWidgetElement(
      order = "0130",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BusinessKeyDelimiter.Label",
      toolTip = "i18n::DataVaultConfiguration.BusinessKeyDelimiter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String businessKeyDelimiter = "||";

  @GuiWidgetElement(
      order = "0135",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.HashContentPrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.HashContentPrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String hashContentPrefix;

  @GuiWidgetElement(
      order = "0140",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.NullPlaceholder.Label",
      toolTip = "i18n::DataVaultConfiguration.NullPlaceholder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String nullPlaceholder = "^^";

  @GuiWidgetElement(
      order = "0145",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.HashContentSuffix.Label",
      toolTip = "i18n::DataVaultConfiguration.HashContentSuffix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private String hashContentSuffix;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.TrimBusinessKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.TrimBusinessKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private boolean trimBusinessKeys = true;

  /**
   * When {@code true}, hub hash inputs for composite business keys use the composed vault BK field
   * only. Default {@code false}: hash over ordered source parts (VaultSpeed-style trailing suffix
   * applies to the joined parts, not to a separately stored composed string with suffix).
   *
   * <p>Satellites and links do not store composed BK columns; they hash from mapped source parts
   * using the hub definition and these hash settings.
   */
  @GuiWidgetElement(
      order = "0155",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.HashUsesComposedBusinessKey.Label",
      toolTip = "i18n::DataVaultConfiguration.HashUsesComposedBusinessKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERAL_TAB_ID)
  @HopMetadataProperty
  private boolean hashUsesComposedBusinessKey;

  @GuiWidgetElement(
      order = "0330",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.LoadDateField.Label",
      toolTip = "i18n::DataVaultConfiguration.LoadDateField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String loadDateField = "LOAD_DATE";

  @GuiWidgetElement(
      order = "0340",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.LoadEndDateField.Label",
      toolTip = "i18n::DataVaultConfiguration.LoadEndDateField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String loadEndDateField = "LOAD_END_DATE";

  @GuiWidgetElement(
      order = "0350",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.RecordSourceField.Label",
      toolTip = "i18n::DataVaultConfiguration.RecordSourceField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String recordSourceField = "RECORD_SOURCE";

  @GuiWidgetElement(
      order = "0355",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.RecordSourceFieldLength.Label",
      toolTip = "i18n::DataVaultConfiguration.RecordSourceFieldLength.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String recordSourceFieldLength = "100";

  @GuiWidgetElement(
      order = "0360",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.StoreLoadCycleId.Label",
      toolTip = "i18n::DataVaultConfiguration.StoreLoadCycleId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private boolean storeLoadCycleId;

  @GuiWidgetElement(
      order = "0365",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.LoadCycleIdField.Label",
      toolTip = "i18n::DataVaultConfiguration.LoadCycleIdField.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String loadCycleIdField = DvLoadCycleSupport.DEFAULT_FIELD_NAME;

  @GuiWidgetElement(
      order = "0367",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.LoadCycleControlTable.Label",
      toolTip = "i18n::DataVaultConfiguration.LoadCycleControlTable.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private String loadCycleControlTable = DvLoadCycleSupport.DEFAULT_CONTROL_TABLE;

  @GuiWidgetElement(
      order = "0410",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.UseLoadEndDate.Label",
      toolTip = "i18n::DataVaultConfiguration.UseLoadEndDate.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private boolean useLoadEndDate = false;

  @GuiWidgetElement(
      order = "0420",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GeneratePrimaryKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratePrimaryKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private boolean generatePrimaryKeys = false;

  @GuiWidgetElement(
      order = "0430",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.GenerateForeignKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.GenerateForeignKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COLUMNS_TAB_ID)
  @HopMetadataProperty
  private boolean generateForeignKeys = false;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.TargetTableBatchSize.Label",
      toolTip = "i18n::DataVaultConfiguration.TargetTableBatchSize.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String targetTableBatchSize = DEFAULT_TARGET_TABLE_BATCH_SIZE;

  @GuiWidgetElement(
      order = "0510",
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
  private String bulkLoadStagingFolder = DEFAULT_BULK_LOAD_STAGING_FOLDER;

  @GuiWidgetElement(
      order = "0514",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BulkLoadDelimiter.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadDelimiter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String bulkLoadDelimiter = DEFAULT_BULK_LOAD_DELIMITER;

  @GuiWidgetElement(
      order = "0515",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BulkLoadEnclosure.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadEnclosure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String bulkLoadEnclosure = DEFAULT_BULK_LOAD_ENCLOSURE;

  @GuiWidgetElement(
      order = "0516",
      type = GuiElementType.TEXT,
      label = "i18n::DataVaultConfiguration.BulkLoadEncoding.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadEncoding.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String bulkLoadEncoding = DEFAULT_BULK_LOAD_ENCODING;

  @GuiWidgetElement(
      order = "0517",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.BulkLoadLocalFileRequired.Label",
      toolTip = "i18n::DataVaultConfiguration.BulkLoadLocalFileRequired.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private boolean bulkLoadLocalFileRequired = true;

  @GuiWidgetElement(
      order = "0520",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.SortRowsSize.Label",
      toolTip = "i18n::DataVaultConfiguration.SortRowsSize.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private String sortRowsSize = DEFAULT_SORT_ROWS_SIZE;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @GuiWidgetElement(
      order = "0535",
      type = GuiElementType.COMBO,
      label = "i18n::DataVaultConfiguration.ExecutionLogLevel.Label",
      toolTip = "i18n::DataVaultConfiguration.ExecutionLogLevel.ToolTip",
      comboValuesMethod = "getExecutionLogLevelDescriptions",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty(key = "execution_log_level")
  private String executionLogLevel = LogLevel.BASIC.getCode();

  @GuiWidgetElement(
      order = "0545",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.SingleStoreShardKeyOnHashKey.Label",
      toolTip = "i18n::DataVaultConfiguration.SingleStoreShardKeyOnHashKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private boolean singleStoreShardKeyOnHashKey = false;

  @GuiWidgetElement(
      order = "0550",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DataVaultConfiguration.SingleStoreShardKeyIncludeDrivingKeys.Label",
      toolTip = "i18n::DataVaultConfiguration.SingleStoreShardKeyIncludeDrivingKeys.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_TARGET_LOAD_TAB_ID)
  @HopMetadataProperty
  private boolean singleStoreShardKeyIncludeDrivingKeys = true;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::DataVaultConfiguration.GeneratedPipelineFolder.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratedPipelineFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String generatedPipelineFolder;

  @GuiWidgetElement(
      order = "0610",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.HubPipelineNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.HubPipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String hubPipelineNamePrefix = DEFAULT_HUB_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0620",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.LinkPipelineNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.LinkPipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String linkPipelineNamePrefix = DEFAULT_LINK_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0630",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.SatellitePipelineNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.SatellitePipelineNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String satellitePipelineNamePrefix = DEFAULT_SATELLITE_PIPELINE_NAME_PREFIX;

  @GuiWidgetElement(
      order = "0640",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DataVaultConfiguration.GeneratedWorkflowNamePrefix.Label",
      toolTip = "i18n::DataVaultConfiguration.GeneratedWorkflowNamePrefix.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_GENERATED_PIPELINES_TAB_ID)
  @HopMetadataProperty
  private String generatedWorkflowNamePrefix = DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX;

  @HopMetadataProperty private String stsPipelineNamePrefix = DEFAULT_STS_PIPELINE_NAME_PREFIX;

  public DataVaultConfiguration() {
    super();
    this.unknownHashKeyValue = "00000000000000000000000000000000";
    this.unknownLinkHashKeyValue = "00000000000000000000000000000000";
    this.unknownRecordSource = "UNKNOWN";
    this.invalidHashKeyValue = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    this.invalidLinkHashKeyValue = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    this.invalidRecordSource = "INVALID";
    this.orphanPolicy = DvOrphanPolicy.PASS.name();
    this.inferredRecordSource = DvOrphanHandlingSupport.DEFAULT_INFERRED_RECORD_SOURCE;
    this.inferredFlagField = DvOrphanHandlingSupport.DEFAULT_INFERRED_FLAG_FIELD;
    this.quarantineTable = DvOrphanHandlingSupport.DEFAULT_QUARANTINE_TABLE;
  }

  /**
   * Resolves the batch size (commit size) for generated Table Output transforms writing to target
   * DV tables.
   */
  public String resolveTargetTableCommitSize(IVariables variables) {
    String size = targetTableBatchSize;
    if (Utils.isEmpty(size)) {
      return DEFAULT_TARGET_TABLE_BATCH_SIZE;
    }
    if (variables != null) {
      size = variables.resolve(size);
    }
    return size;
  }

  /**
   * Resolves the number of parallel copies for generated Table Output transforms writing to target
   * DV tables ({@link org.apache.hop.pipeline.transform.TransformMeta#setCopiesString(String)}).
   */
  public String resolveTargetTableParallelCopies(IVariables variables) {
    String copies = targetTableParallelCopies;
    if (Utils.isEmpty(copies)) {
      return DEFAULT_TARGET_TABLE_PARALLEL_COPIES;
    }
    if (variables != null) {
      copies = variables.resolve(copies);
    }
    return copies;
  }

  /**
   * Resolves the in-memory row buffer ({@code sortSize}) for generated Sort Rows transforms used in
   * hub, link, and satellite update pipelines.
   */
  public String resolveSortRowsSize(IVariables variables) {
    String size = sortRowsSize;
    if (Utils.isEmpty(size)) {
      return DEFAULT_SORT_ROWS_SIZE;
    }
    if (variables != null) {
      size = variables.resolve(size);
    }
    return size;
  }

  /** Combo items for the hash algorithm widget. */
  public List<String> getHashAlgorithmOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return enumNames(HashAlgorithm.class);
  }

  /** Combo items for the hash key data type widget. */
  public List<String> getHashKeyDataTypeOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return enumNames(HashKeyDataType.class);
  }

  /** Combo items for the hash content casing widget. */
  public List<String> getHashContentCasingOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return enumNames(HashContentCasing.class);
  }

  /** Concrete orphan policies for the model default (INHERIT is table-level only). */
  public List<String> getOrphanPolicyOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return List.of(
        DvOrphanPolicy.PASS.name(),
        DvOrphanPolicy.INFER.name(),
        DvOrphanPolicy.SENTINEL.name(),
        DvOrphanPolicy.QUARANTINE.name(),
        DvOrphanPolicy.FAIL.name());
  }

  public String getOrphanPolicy() {
    return resolveOrphanPolicy().name();
  }

  public void setOrphanPolicy(String value) {
    orphanPolicy = parseOrphanPolicy(value, DvOrphanPolicy.PASS).name();
  }

  public DvOrphanPolicy resolveOrphanPolicy() {
    DvOrphanPolicy parsed = parseOrphanPolicy(orphanPolicy, DvOrphanPolicy.PASS);
    return parsed == DvOrphanPolicy.INHERIT ? DvOrphanPolicy.PASS : parsed;
  }

  public String resolveInferredRecordSource(IVariables variables) {
    String value = inferredRecordSource;
    if (variables != null) {
      value = variables.resolve(value);
    }
    return Utils.isEmpty(value) ? DvOrphanHandlingSupport.DEFAULT_INFERRED_RECORD_SOURCE : value;
  }

  public String resolveInferredFlagField(IVariables variables) {
    String value = inferredFlagField;
    if (variables != null) {
      value = variables.resolve(value);
    }
    return Utils.isEmpty(value) ? DvOrphanHandlingSupport.DEFAULT_INFERRED_FLAG_FIELD : value;
  }

  public String resolveQuarantineTableName(IVariables variables) {
    String value = quarantineTable;
    if (variables != null) {
      value = variables.resolve(value);
    }
    return Utils.isEmpty(value) ? DvOrphanHandlingSupport.DEFAULT_QUARANTINE_TABLE : value;
  }

  private static DvOrphanPolicy parseOrphanPolicy(String value, DvOrphanPolicy defaultValue) {
    return DvOrphanPolicy.parse(value, defaultValue);
  }

  /** Combo items for the target load mode widget (localized descriptions). */
  public List<String> getTargetLoadModeOptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    DatabaseMeta targetDatabase = resolveTargetDatabase(metadataProvider);
    List<String> available = DvBulkLoadPluginSupport.getAvailableModeDescriptions(targetDatabase);
    if (available.isEmpty()) {
      return List.of(DvTargetLoadMode.TABLE_OUTPUT.getDescription());
    }
    return available;
  }

  private DatabaseMeta resolveTargetDatabase(IHopMetadataProvider metadataProvider) {
    if (metadataProvider == null || Utils.isEmpty(targetDatabase)) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(DatabaseMeta.class).load(targetDatabase);
    } catch (Exception e) {
      return null;
    }
  }

  /** Returns the localized target load mode description for the GUI combo. */
  public String getTargetLoadMode() {
    return DvTargetLoadMode.lookupCode(targetLoadMode).getDescription();
  }

  /** Persists a target load mode code or localized description from the GUI combo. */
  public void setTargetLoadMode(String descriptionOrCode) {
    if (Utils.isEmpty(descriptionOrCode)) {
      targetLoadMode = DvTargetLoadMode.TABLE_OUTPUT.getCode();
      return;
    }
    String value = descriptionOrCode.trim();
    for (DvTargetLoadMode mode : DvTargetLoadMode.values()) {
      if (value.equals(mode.getDescription()) || value.equals(mode.getCode())) {
        targetLoadMode = mode.getCode();
        return;
      }
    }
    targetLoadMode = DvTargetLoadMode.lookupCode(value).getCode();
  }

  /** Resolves the configured target load strategy for pipeline generation. */
  public DvTargetLoadMode resolveTargetLoadMode() {
    return DvTargetLoadMode.lookupCode(targetLoadMode);
  }

  @Override
  public String resolveBulkLoadStagingFolder(IVariables variables, String modelName) {
    return DvTargetLoadConfigurationSupport.resolveBulkLoadStagingFolder(
        bulkLoadStagingFolder, DEFAULT_BULK_LOAD_STAGING_FOLDER, variables, modelName);
  }

  @Override
  public String resolveBulkLoadDelimiter(IVariables variables) {
    return DvTargetLoadConfigurationSupport.resolveBulkLoadTextSetting(
        bulkLoadDelimiter, DEFAULT_BULK_LOAD_DELIMITER, variables);
  }

  @Override
  public String resolveBulkLoadEnclosure(IVariables variables) {
    return DvTargetLoadConfigurationSupport.resolveBulkLoadTextSetting(
        bulkLoadEnclosure, DEFAULT_BULK_LOAD_ENCLOSURE, variables);
  }

  @Override
  public String resolveBulkLoadEncoding(IVariables variables) {
    return DvTargetLoadConfigurationSupport.resolveBulkLoadTextSetting(
        bulkLoadEncoding, DEFAULT_BULK_LOAD_ENCODING, variables);
  }

  @Override
  public String resolveGeneratedWorkflowName(IVariables variables, String modelName) {
    return DvTargetLoadConfigurationSupport.resolveGeneratedWorkflowName(
        generatedWorkflowNamePrefix, DEFAULT_GENERATED_WORKFLOW_NAME_PREFIX, variables, modelName);
  }

  /** Combo items for the execution log level widget (localized descriptions). */
  public List<String> getExecutionLogLevelDescriptions(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return List.of(LogLevel.getLogLevelDescriptions());
  }

  /**
   * Returns the localized log level description for the GUI combo. The persisted value is the log
   * level code (e.g. {@code Basic}).
   */
  public String getExecutionLogLevel() {
    return LogLevel.lookupCode(executionLogLevel).getDescription();
  }

  /** Persists the log level code resolved from a combo description (or code). */
  public void setExecutionLogLevel(String descriptionOrCode) {
    if (Utils.isEmpty(descriptionOrCode)) {
      executionLogLevel = LogLevel.BASIC.getCode();
      return;
    }
    String value = descriptionOrCode.trim();
    for (LogLevel level : LogLevel.values()) {
      if (value.equals(level.getDescription()) || value.equals(level.getCode())) {
        executionLogLevel = level.getCode();
        return;
      }
    }
    executionLogLevel = LogLevel.lookupCode(value).getCode();
  }

  /** Resolves the log level applied to generated pipeline engines at execution time. */
  public LogLevel resolveExecutionLogLevel() {
    return LogLevel.lookupCode(executionLogLevel);
  }

  public String buildHubPipelineName(
      IVariables variables, String targetTableName, String sourceName) {
    return buildPipelineName(variables, hubPipelineNamePrefix, "", targetTableName, sourceName);
  }

  public String buildLinkPipelineName(
      IVariables variables, String targetTableName, String sourceName) {
    return buildPipelineName(variables, linkPipelineNamePrefix, "", targetTableName, sourceName);
  }

  public String buildSatellitePipelineName(
      IVariables variables, String targetTableName, String sourceName) {
    return buildPipelineName(
        variables, satellitePipelineNamePrefix, "", targetTableName, sourceName);
  }

  public String buildStsPipelineName(
      IVariables variables, String statusTableName, String sourceName) {
    return buildPipelineName(variables, stsPipelineNamePrefix, "", statusTableName, sourceName);
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
    return prefix + targetTableName + "-" + sourceName;
  }

  /** Returns the persisted hash algorithm name for GUI combo widgets. */
  public String getHashAlgorithm() {
    return Utils.isEmpty(hashAlgorithm) ? HashAlgorithm.MD5.name() : hashAlgorithm;
  }

  /** Persists a hash algorithm name, ignoring blank or unknown values. */
  public void setHashAlgorithm(String value) {
    hashAlgorithm = parseHashAlgorithm(value, HashAlgorithm.MD5).name();
  }

  /** Resolves the configured hash algorithm for pipeline generation and DDL. */
  public HashAlgorithm resolveHashAlgorithm() {
    return parseHashAlgorithm(hashAlgorithm, HashAlgorithm.MD5);
  }

  /** Returns the persisted hash key data type name for GUI combo widgets. */
  public String getHashKeyDataType() {
    return Utils.isEmpty(hashKeyDataType) ? HashKeyDataType.HEX.name() : hashKeyDataType;
  }

  /** Persists a hash key data type name, ignoring blank or unknown values. */
  public void setHashKeyDataType(String value) {
    hashKeyDataType = parseHashKeyDataType(value, HashKeyDataType.HEX).name();
  }

  /** Resolves the configured hash key data type for pipeline generation and DDL. */
  public HashKeyDataType resolveHashKeyDataType() {
    return parseHashKeyDataType(hashKeyDataType, HashKeyDataType.HEX);
  }

  /** Returns the persisted hash content casing name for GUI combo widgets. */
  public String getHashContentCasing() {
    return Utils.isEmpty(hashContentCasing) ? HashContentCasing.UPPER.name() : hashContentCasing;
  }

  /** Persists a hash content casing name, ignoring blank or unknown values. */
  public void setHashContentCasing(String value) {
    hashContentCasing = parseHashContentCasing(value, HashContentCasing.UPPER).name();
  }

  /** Resolves the configured hash content casing for hash key transforms. */
  public HashContentCasing resolveHashContentCasing() {
    return parseHashContentCasing(hashContentCasing, HashContentCasing.UPPER);
  }

  private static HashAlgorithm parseHashAlgorithm(String value, HashAlgorithm defaultValue) {
    if (Utils.isEmpty(value)) {
      return defaultValue;
    }
    try {
      return HashAlgorithm.valueOf(value.trim());
    } catch (IllegalArgumentException ignored) {
      return defaultValue;
    }
  }

  private static HashKeyDataType parseHashKeyDataType(String value, HashKeyDataType defaultValue) {
    if (Utils.isEmpty(value)) {
      return defaultValue;
    }
    try {
      return HashKeyDataType.valueOf(value.trim());
    } catch (IllegalArgumentException ignored) {
      return defaultValue;
    }
  }

  private static HashContentCasing parseHashContentCasing(
      String value, HashContentCasing defaultValue) {
    if (Utils.isEmpty(value)) {
      return defaultValue;
    }
    try {
      return HashContentCasing.valueOf(value.trim());
    } catch (IllegalArgumentException ignored) {
      return defaultValue;
    }
  }

  private static <E extends Enum<E>> List<String> enumNames(Class<E> enumClass) {
    return Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toList();
  }
}
