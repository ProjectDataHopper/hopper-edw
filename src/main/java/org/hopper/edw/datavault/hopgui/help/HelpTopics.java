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
package org.hopper.edw.datavault.hopgui.help;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/**
 * Maps dialog Help buttons to plugin-shipped HTML pages under {@code
 * plugins/misc/hopper-edw/docs/}.
 */
public final class HelpTopics {

  private static final Class<?> PKG = HelpTopics.class;

  public static final String DV_HUB = "dv-hub-dialog";
  public static final String DV_SATELLITE = "dv-satellite-dialog";
  public static final String DV_LINK = "dv-link-dialog";
  public static final String DV_REFERENCE = "dv-reference-dialog";
  public static final String DV_MODEL = "dv-model-dialog";
  public static final String DV_NOTE = "dv-note-dialog";
  public static final String DV_LINK_HUB_SOURCE = "dv-link-hub-source-dialog";
  public static final String DV_LINK_SATELLITE_SOURCE = "dv-link-satellite-source-dialog";
  public static final String DV_HUB_SOURCE_KEY_FIELD = "dv-hub-source-key-field-dialog";
  public static final String DV_SATELLITE_SOURCE_KEY_FIELD = "dv-satellite-source-key-field-dialog";

  public static final String BV_MODEL = "bv-model-dialog";
  public static final String BV_TABLE = "bv-table-dialog";
  public static final String BV_SCD2_TABLE = "bv-scd2-table-dialog";
  public static final String BV_BUSINESS_TABLE = "bv-business-table-dialog";

  public static final String DM_MODEL = "dm-model-dialog";
  public static final String DM_TABLE = "dm-table-dialog";
  public static final String DM_DIMENSION = "dm-dimension-dialog";
  public static final String DM_DATE_DIMENSION = "dm-date-dimension-dialog";
  public static final String DM_FACT = "dm-fact-dialog";
  public static final String DM_DIMENSION_ALIAS = "dm-dimension-alias-dialog";
  public static final String DM_JUNK_DIMENSION = "dm-junk-dimension-dialog";
  public static final String DM_RANGE_DIMENSION = "dm-range-dimension-dialog";
  public static final String DM_BRIDGE = "dm-bridge-dialog";

  public static final String ACTION_DATAVAULT_UPDATE = "action-datavault-update-dialog";
  public static final String ACTION_DIMENSIONAL_UPDATE = "action-dimensional-update-dialog";
  public static final String ACTION_BUSINESSVAULT_UPDATE = "action-businessvault-update-dialog";
  public static final String ACTION_DIMENSIONAL_PUBLISH = "action-dimensional-publish-dialog";
  public static final String ACTION_GENERATE_EXECUTION_MAP = "action-generate-executionmap-dialog";
  public static final String ACTION_BEGIN_VAULT_UPDATE = "action-begin-vault-update-dialog";
  public static final String ACTION_END_VAULT_UPDATE = "action-end-vault-update-dialog";
  public static final String ACTION_VALIDATE_RESOURCE_DEFINITIONS =
      "action-validate-resource-definitions-dialog";
  public static final String ACTION_EXPORT_DATA_LINEAGE = "action-export-data-lineage-dialog";
  public static final String ACTION_EXPORT_ARCHITECTURE = "action-export-architecture-dialog";
  public static final String ACTION_IMPORT_DBT = "action-import-dbt-project-dialog";
  public static final String ACTION_UPDATE_RESOURCE_GROUP =
      "action-update-resource-definition-group-dialog";
  public static final String ACTION_HARVEST_SOURCE_METADATA =
      "action-harvest-source-metadata-dialog";
  public static final String ACTION_MEASURE_DATA_QUALITY = "action-measure-data-quality-dialog";
  public static final String ACTION_EVALUATE_QUALITY_GATE = "action-evaluate-quality-gate-dialog";

  public static final String IMPORT_DATABASE_TABLES_CATALOG =
      "import-database-tables-catalog-dialog";
  public static final String IMPORT_DATABASE_TABLES_OPTIONS =
      "import-database-tables-options-dialog";
  public static final String IMPORT_CSV_FILE_OPTIONS = "import-csv-file-options-dialog";
  public static final String IMPORT_PARQUET_FILE_OPTIONS = "import-parquet-file-options-dialog";
  public static final String IMPORT_ICEBERG_TABLE = "import-iceberg-table-dialog";
  public static final String IMPORT_ICEBERG_TABLE_OPTIONS = "import-iceberg-table-options-dialog";
  public static final String IMPORT_DM_DATABASE_TABLES_OPTIONS =
      "import-dm-database-tables-options-dialog";
  public static final String IMPORT_SOURCE_SCHEMA_OPTIONS = "import-source-schema-options-dialog";

  public static final String SOURCE_MODEL = "source-model-dialog";
  public static final String SOURCE_TABLE = "source-table-dialog";
  public static final String SOURCE_QUERY = "source-query-dialog";
  public static final String SOURCE_JSON = "source-json-dialog";
  public static final String SOURCE_PIPELINE = "source-pipeline-dialog";
  public static final String SOURCE_RELATIONSHIP = "source-relationship-dialog";

  public static final String DV_AI_ADVISOR = "dv-ai-advisor-dialog";
  public static final String BV_AI_ADVISOR = "bv-ai-advisor-dialog";
  public static final String DM_AI_ADVISOR = "dm-ai-advisor-dialog";
  public static final String PIPELINE_AI_ADVISOR = "pipeline-ai-advisor-dialog";
  public static final String WORKFLOW_AI_ADVISOR = "workflow-ai-advisor-dialog";
  public static final String DV_AI_PROPOSAL_REVIEW = "dv-ai-proposal-review-dialog";
  public static final String HOP_AI_PROPOSAL_REVIEW = "hop-ai-proposal-review-dialog";
  public static final String MODEL_AI_PROPOSAL_REVIEW = "model-ai-proposal-review-dialog";

  public static final String RESOURCE_DEFINITION_ISSUE = "resource-definition-issue-dialog";
  public static final String RESOURCE_DEFINITION_VALIDATION_RESULTS =
      "resource-definition-validation-results-dialog";
  public static final String RESOURCE_DEFINITION_VALIDATION_OPTIONS =
      "resource-definition-validation-options-dialog";
  public static final String ACKNOWLEDGE_VALIDATION_ISSUE = "acknowledge-validation-issue-dialog";
  public static final String EXECUTION_MAP_GENERATION = "execution-map-generation-dialog";
  public static final String ELK_LAYOUT = "elk-layout-dialog";
  public static final String REFRESH_RECORD_DEFINITION_FROM_SOURCE =
      "refresh-record-definition-from-source-dialog";
  public static final String JINJA_MACRO_LIBRARY = "jinja-macro-library-dialog";
  public static final String BV_DBT_IMPORT = "bv-dbt-import-dialog";
  public static final String DATA_TYPE_MAPPING = "data-type-mapping-dialog";
  public static final String TARGET_TYPE_MAPPING = "target-type-mapping-dialog";
  public static final String SOURCE_TO_VAULT_REVIEW = "source-to-vault-review-dialog";
  public static final String LINEAGE_VIEW_SETTINGS = "lineage-view-settings-dialog";
  public static final String LINEAGE_BACKEND = "lineage-backend-dialog";

  public static final String RECORD_DEFINITION_INPUT = "record-definition-input-dialog";
  public static final String RECORD_DEFINITION_DDL = "record-definition-ddl-dialog";
  public static final String RECORD_DEFINITION_OUTPUT = "record-definition-output-dialog";
  public static final String RECORD_DEFINITION_DATA_INPUT = "record-definition-data-input-dialog";
  public static final String DATABASE_TABLE_METADATA = "database-table-metadata-dialog";
  public static final String DATE_DIMENSION_GENERATOR = "date-dimension-generator-dialog";
  public static final String SOURCE_MODEL_SQL = "source-model-sql-dialog";
  public static final String DV_HASH_KEY = "dv-hash-key-dialog";
  public static final String MERGE_ROWS_PLUS = "merge-rows-plus-dialog";
  public static final String SORTED_SCHEMA_MERGE = "sorted-schema-merge-dialog";
  public static final String JUNK_DIMENSION = "junk-dimension-transform-dialog";

  public static final String DATA_CATALOG = "data-catalog-dialog";
  public static final String RESOURCE_DEFINITION_GROUP = "resource-definition-group-dialog";
  public static final String DV_CONFIGURATION = "dv-configuration-dialog";
  public static final String BV_CONFIGURATION = "bv-configuration-dialog";
  public static final String DM_CONFIGURATION = "dm-configuration-dialog";
  public static final String SOURCE_MODEL_CONFIGURATION = "source-model-configuration-dialog";
  public static final String SOURCE_MODEL_SERVICE = "source-model-service-dialog";
  public static final String EXECUTION_METRICS_PROFILE = "execution-metrics-profile-dialog";
  public static final String DATA_QUALITY_RULE_SET = "data-quality-rule-set-dialog";

  /**
   * Plugin-shipped HTML page for a Help topic. {@code htmlPage} is relative to {@code docs/} (for
   * example {@code help/dv-hub-dialog.html} or {@code record-definition-input.html}).
   */
  public record HelpPage(String topicId, String htmlPage, String anchor, String titleKey) {
    public String adocRelative() {
      String page = htmlPage.endsWith(".html") ? htmlPage : htmlPage + ".html";
      return page.substring(0, page.length() - ".html".length()) + ".adoc";
    }
  }

  private static final List<HelpPage> PAGES =
      List.of(
          p(DV_HUB, "help/dv-hub-dialog.html", "HelpTopics.DvHubDialog.Title"),
          p(DV_SATELLITE, "help/dv-satellite-dialog.html", "HelpTopics.DvSatelliteDialog.Title"),
          p(DV_LINK, "help/dv-link-dialog.html", "HelpTopics.DvLinkDialog.Title"),
          p(
              DV_REFERENCE,
              "help/dv-reference-dialog.html",
              "HelpTopics.DvReferenceTableDialog.Title"),
          p(DV_MODEL, "help/dv-model-dialog.html", "HelpTopics.DvModelDialog.Title"),
          p(DV_NOTE, "help/dv-note-dialog.html", "HelpTopics.DvNoteDialog.Title"),
          p(
              DV_LINK_HUB_SOURCE,
              "help/dv-link-hub-source-dialog.html",
              "HelpTopics.DvLinkHubSourceDialog.Title"),
          p(
              DV_LINK_SATELLITE_SOURCE,
              "help/dv-link-satellite-source-dialog.html",
              "HelpTopics.DvLinkSatelliteSourceDialog.Title"),
          p(
              DV_HUB_SOURCE_KEY_FIELD,
              "help/dv-hub-source-key-field-dialog.html",
              "HelpTopics.HubSourceKeyFieldDialog.Title"),
          p(
              DV_SATELLITE_SOURCE_KEY_FIELD,
              "help/dv-satellite-source-key-field-dialog.html",
              "HelpTopics.SatelliteSourceKeyFieldDialog.Title"),
          p(BV_MODEL, "help/bv-model-dialog.html", "HelpTopics.BvModelDialog.Title"),
          p(BV_TABLE, "help/bv-table-dialog.html", "HelpTopics.BvTableDialog.Title"),
          p(BV_SCD2_TABLE, "help/bv-scd2-table-dialog.html", "HelpTopics.BvScd2TableDialog.Title"),
          p(
              BV_BUSINESS_TABLE,
              "help/bv-business-table-dialog.html",
              "HelpTopics.BvBusinessTableDialog.Title"),
          p(DM_MODEL, "help/dm-model-dialog.html", "HelpTopics.DmModelDialog.Title"),
          p(DM_TABLE, "help/dm-dimension-dialog.html", "HelpTopics.DmTableDialog.Title"),
          p(DM_DIMENSION, "help/dm-dimension-dialog.html", "HelpTopics.DmDimensionDialog.Title"),
          p(
              DM_DATE_DIMENSION,
              "help/dm-date-dimension-dialog.html",
              "HelpTopics.DmDateDimensionDialog.Title"),
          p(DM_FACT, "help/dm-fact-dialog.html", "HelpTopics.DmFactDialog.Title"),
          p(
              DM_DIMENSION_ALIAS,
              "help/dm-dimension-alias-dialog.html",
              "HelpTopics.DmDimensionAliasDialog.Title"),
          p(
              DM_JUNK_DIMENSION,
              "help/dm-junk-dimension-dialog.html",
              "HelpTopics.DmJunkDimensionDialog.Title"),
          p(
              DM_RANGE_DIMENSION,
              "help/dm-range-dimension-dialog.html",
              "HelpTopics.DmRangeDimensionDialog.Title"),
          p(DM_BRIDGE, "help/dm-bridge-dialog.html", "HelpTopics.DmBridgeDialog.Title"),
          p(
              ACTION_DATAVAULT_UPDATE,
              "datavault-update-action.html",
              "HelpTopics.ActionDataVaultUpdateDialog.Title"),
          p(
              ACTION_DIMENSIONAL_UPDATE,
              "dimensional-update-action.html",
              "HelpTopics.ActionDimensionalUpdateDialog.Title"),
          p(
              ACTION_BUSINESSVAULT_UPDATE,
              "business-vault-update-action.html",
              "HelpTopics.ActionBusinessVaultUpdateDialog.Title"),
          p(
              ACTION_DIMENSIONAL_PUBLISH,
              "dimensional-update-action.html",
              "dimensional-publish",
              "HelpTopics.ActionDimensionalPublishDialog.Title"),
          p(
              ACTION_GENERATE_EXECUTION_MAP,
              "execution-maps.html",
              "HelpTopics.ActionGenerateExecutionMapDialog.Title"),
          p(
              ACTION_BEGIN_VAULT_UPDATE,
              "help/action-begin-vault-update-dialog.html",
              "HelpTopics.ActionBeginVaultUpdateDialog.Title"),
          p(
              ACTION_END_VAULT_UPDATE,
              "help/action-end-vault-update-dialog.html",
              "HelpTopics.ActionEndVaultUpdateDialog.Title"),
          p(
              ACTION_VALIDATE_RESOURCE_DEFINITIONS,
              "resource-definition-validation.html",
              "HelpTopics.ActionValidateResourceDefinitionsDialog.Title"),
          p(
              ACTION_EXPORT_DATA_LINEAGE,
              "openlineage-export.html",
              "HelpTopics.ActionExportDataLineageDialog.Title"),
          p(
              ACTION_EXPORT_ARCHITECTURE,
              "architecture-export.html",
              "HelpTopics.ActionExportArchitectureDialog.Title"),
          p(ACTION_IMPORT_DBT, "dbt-import.html", "HelpTopics.ActionImportDbtProjectDialog.Title"),
          p(
              ACTION_UPDATE_RESOURCE_GROUP,
              "update-resource-definition-group-action.html",
              "HelpTopics.ActionUpdateResourceDefinitionGroupDialog.Title"),
          p(
              ACTION_HARVEST_SOURCE_METADATA,
              "metadata-harvesting.html",
              "harvest-action-dialog",
              "HelpTopics.ActionHarvestSourceMetadataDialog.Title"),
          p(
              ACTION_MEASURE_DATA_QUALITY,
              "data-quality.html",
              "measure-data-quality-action",
              "HelpTopics.ActionMeasureDataQualityDialog.Title"),
          p(
              ACTION_EVALUATE_QUALITY_GATE,
              "data-quality.html",
              "evaluate-quality-gate-action",
              "HelpTopics.ActionEvaluateQualityGateDialog.Title"),
          p(
              IMPORT_DATABASE_TABLES_CATALOG,
              "help/import-database-tables-catalog-dialog.html",
              "HelpTopics.ImportDatabaseTablesCatalogDialog.Title"),
          p(
              IMPORT_DATABASE_TABLES_OPTIONS,
              "help/import-database-tables-options-dialog.html",
              "HelpTopics.ImportDatabaseTablesOptionsDialog.Title"),
          p(
              IMPORT_CSV_FILE_OPTIONS,
              "help/import-csv-file-options-dialog.html",
              "HelpTopics.ImportCsvFileOptionsDialog.Title"),
          p(
              IMPORT_PARQUET_FILE_OPTIONS,
              "help/import-parquet-file-options-dialog.html",
              "HelpTopics.ImportParquetFileOptionsDialog.Title"),
          p(
              IMPORT_ICEBERG_TABLE,
              "help/import-iceberg-table-dialog.html",
              "HelpTopics.ImportIcebergTableDialog.Title"),
          p(
              IMPORT_ICEBERG_TABLE_OPTIONS,
              "help/import-iceberg-table-options-dialog.html",
              "HelpTopics.ImportIcebergTableOptionsDialog.Title"),
          p(
              IMPORT_DM_DATABASE_TABLES_OPTIONS,
              "help/import-dm-database-tables-options-dialog.html",
              "HelpTopics.ImportDmDatabaseTablesOptionsDialog.Title"),
          p(
              IMPORT_SOURCE_SCHEMA_OPTIONS,
              "help/import-source-schema-options-dialog.html",
              "HelpTopics.ImportSourceSchemaOptionsDialog.Title"),
          p(SOURCE_MODEL, "help/source-model-dialog.html", "HelpTopics.SourceModelDialog.Title"),
          p(SOURCE_TABLE, "help/source-table-dialog.html", "HelpTopics.SourceTableDialog.Title"),
          p(SOURCE_QUERY, "help/source-query-dialog.html", "HelpTopics.SourceQueryDialog.Title"),
          p(SOURCE_JSON, "help/source-json-dialog.html", "HelpTopics.SourceJsonDialog.Title"),
          p(
              SOURCE_PIPELINE,
              "source-modeler-overview.html",
              "source-pipeline",
              "HelpTopics.SourcePipelineDialog.Title"),
          p(
              SOURCE_RELATIONSHIP,
              "help/source-relationship-dialog.html",
              "HelpTopics.SourceRelationshipDialog.Title"),
          p(DV_AI_ADVISOR, "help/dv-ai-advisor-dialog.html", "HelpTopics.DvAiAdvisorDialog.Title"),
          p(BV_AI_ADVISOR, "help/bv-ai-advisor-dialog.html", "HelpTopics.BvAiAdvisorDialog.Title"),
          p(DM_AI_ADVISOR, "help/dm-ai-advisor-dialog.html", "HelpTopics.DmAiAdvisorDialog.Title"),
          p(
              PIPELINE_AI_ADVISOR,
              "help/pipeline-ai-advisor-dialog.html",
              "HelpTopics.PipelineAiAdvisorDialog.Title"),
          p(
              WORKFLOW_AI_ADVISOR,
              "help/workflow-ai-advisor-dialog.html",
              "HelpTopics.WorkflowAiAdvisorDialog.Title"),
          p(
              DV_AI_PROPOSAL_REVIEW,
              "help/dv-ai-proposal-review-dialog.html",
              "HelpTopics.DvAiProposalReviewDialog.Title"),
          p(
              HOP_AI_PROPOSAL_REVIEW,
              "help/hop-ai-proposal-review-dialog.html",
              "HelpTopics.HopAiProposalReviewDialog.Title"),
          p(
              MODEL_AI_PROPOSAL_REVIEW,
              "help/model-ai-proposal-review-dialog.html",
              "HelpTopics.ModelAiProposalReviewDialog.Title"),
          p(
              RESOURCE_DEFINITION_ISSUE,
              "help/resource-definition-issue-dialog.html",
              "HelpTopics.ResourceDefinitionIssueDialog.Title"),
          p(
              RESOURCE_DEFINITION_VALIDATION_OPTIONS,
              "help/resource-definition-validation-options-dialog.html",
              "HelpTopics.ResourceDefinitionValidationOptionsDialog.Title"),
          p(
              RESOURCE_DEFINITION_VALIDATION_RESULTS,
              "help/resource-definition-validation-results-dialog.html",
              "HelpTopics.ResourceDefinitionValidationResultsDialog.Title"),
          p(
              ACKNOWLEDGE_VALIDATION_ISSUE,
              "help/acknowledge-validation-issue-dialog.html",
              "HelpTopics.AcknowledgeValidationIssueDialog.Title"),
          p(
              EXECUTION_MAP_GENERATION,
              "execution-maps.html",
              "HelpTopics.ExecutionMapGenerationDialog.Title"),
          p(ELK_LAYOUT, "help/elk-layout-dialog.html", "HelpTopics.ElkLayoutDialog.Title"),
          p(
              REFRESH_RECORD_DEFINITION_FROM_SOURCE,
              "help/refresh-record-definition-from-source-dialog.html",
              "HelpTopics.RefreshRecordDefinitionFromSourceDialog.Title"),
          p(
              JINJA_MACRO_LIBRARY,
              "help/jinja-macro-library-dialog.html",
              "HelpTopics.JinjaMacroLibraryDialog.Title"),
          p(BV_DBT_IMPORT, "help/bv-dbt-import-dialog.html", "HelpTopics.BvDbtImportDialog.Title"),
          p(
              DATA_TYPE_MAPPING,
              "help/data-type-mapping-dialog.html",
              "HelpTopics.DataTypeMappingDialog.Title"),
          p(
              TARGET_TYPE_MAPPING,
              "help/target-type-mapping-dialog.html",
              "HelpTopics.TargetTypeMappingDialog.Title"),
          p(
              SOURCE_TO_VAULT_REVIEW,
              "help/source-to-vault-review-dialog.html",
              "HelpTopics.SourceToVaultReviewDialog.Title"),
          p(
              LINEAGE_VIEW_SETTINGS,
              "help/lineage-view-settings-dialog.html",
              "HelpTopics.LineageViewSettingsDialog.Title"),
          p(
              LINEAGE_BACKEND,
              "help/lineage-backend-dialog.html",
              "HelpTopics.LineageBackendDialog.Title"),
          p(
              RECORD_DEFINITION_INPUT,
              "record-definition-input.html",
              "HelpTopics.RecordDefinitionInputDialog.Title"),
          p(
              RECORD_DEFINITION_DDL,
              "record-definition-ddl.html",
              "HelpTopics.RecordDefinitionDdlDialog.Title"),
          p(
              RECORD_DEFINITION_OUTPUT,
              "record-definition-output.html",
              "HelpTopics.RecordDefinitionOutputDialog.Title"),
          p(
              RECORD_DEFINITION_DATA_INPUT,
              "record-definition-data-input.html",
              "HelpTopics.RecordDefinitionDataInputDialog.Title"),
          p(
              DATABASE_TABLE_METADATA,
              "database-table-metadata.html",
              "HelpTopics.DatabaseTableMetadataDialog.Title"),
          p(
              DATE_DIMENSION_GENERATOR,
              "date-dimension-generator.html",
              "HelpTopics.DateDimensionGeneratorDialog.Title"),
          p(
              SOURCE_MODEL_SQL,
              "help/source-model-sql-dialog.html",
              "HelpTopics.SourceModelSqlDialog.Title"),
          p(DV_HASH_KEY, "help/dv-hash-key-dialog.html", "HelpTopics.DvHashKeyDialog.Title"),
          p(
              MERGE_ROWS_PLUS,
              "help/merge-rows-plus-dialog.html",
              "HelpTopics.MergeRowsPlusDialog.Title"),
          p(
              SORTED_SCHEMA_MERGE,
              "help/sorted-schema-merge-dialog.html",
              "HelpTopics.SortedSchemaMergeDialog.Title"),
          p(
              JUNK_DIMENSION,
              "help/junk-dimension-transform-dialog.html",
              "HelpTopics.JunkDimensionDialog.Title"),
          p(DATA_CATALOG, "data-catalog.html", "HelpTopics.DataCatalogDialog.Title"),
          p(
              RESOURCE_DEFINITION_GROUP,
              "resource-definition-group.html",
              "HelpTopics.ResourceDefinitionGroupDialog.Title"),
          p(
              DV_CONFIGURATION,
              "datavault-configuration.html",
              "HelpTopics.DvConfigurationDialog.Title"),
          p(
              BV_CONFIGURATION,
              "business-vault-configuration.html",
              "HelpTopics.BvConfigurationDialog.Title"),
          p(
              DM_CONFIGURATION,
              "dimensional-modeler-overview.html",
              "HelpTopics.DmConfigurationDialog.Title"),
          p(
              SOURCE_MODEL_CONFIGURATION,
              "source-modeler-overview.html",
              "HelpTopics.SourceModelConfigurationDialog.Title"),
          p(
              SOURCE_MODEL_SERVICE,
              "source-modeler-overview.html",
              "hop-server-jdbc-dbeaver",
              "HelpTopics.SourceModelServiceDialog.Title"),
          p(
              EXECUTION_METRICS_PROFILE,
              "operations.html",
              "HelpTopics.ExecutionMetricsProfileDialog.Title"),
          p(
              DATA_QUALITY_RULE_SET,
              "data-quality.html",
              "HelpTopics.DataQualityRuleSetDialog.Title"));

  private static final Map<String, HelpPage> BY_ID = index(PAGES);

  private HelpTopics() {}

  public static Collection<HelpPage> pages() {
    return PAGES;
  }

  public static HelpPage page(String topicId) {
    if (Utils.isEmpty(topicId)) {
      return null;
    }
    return BY_ID.get(topicId);
  }

  public static HelpPage requirePage(String topicId) throws HopException {
    HelpPage page = page(topicId);
    if (page == null) {
      throw new HopException(BaseMessages.getString(PKG, "DialogHelp.MissingTopic", topicId));
    }
    return page;
  }

  public static String titleKey(String topicId) {
    HelpPage page = page(topicId);
    if (page == null || Utils.isEmpty(page.titleKey())) {
      return "HelpTopics.Default.Title";
    }
    return page.titleKey();
  }

  private static HelpPage p(String topicId, String htmlPage, String titleKey) {
    return new HelpPage(topicId, htmlPage, "", titleKey);
  }

  private static HelpPage p(String topicId, String htmlPage, String anchor, String titleKey) {
    return new HelpPage(topicId, htmlPage, anchor == null ? "" : anchor, titleKey);
  }

  private static Map<String, HelpPage> index(List<HelpPage> pages) {
    Map<String, HelpPage> map = new LinkedHashMap<>();
    for (HelpPage page : pages) {
      map.put(page.topicId(), page);
    }
    return Map.copyOf(map);
  }
}
