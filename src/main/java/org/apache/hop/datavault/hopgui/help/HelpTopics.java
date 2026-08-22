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
package org.apache.hop.datavault.hopgui.help;

import java.util.Map;
import org.apache.hop.core.util.Utils;

/**
 * Classpath topic ids for dialog help markdown under {@code org/apache/hop/datavault/hopgui/help/}.
 */
public final class HelpTopics {

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

  public static final String DM_MODEL = "dm-model-dialog";
  public static final String DM_TABLE = "dm-table-dialog";
  public static final String DM_RANGE_DIMENSION = "dm-range-dimension-dialog";

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
  public static final String ACTION_IMPORT_DBT = "action-import-dbt-project-dialog";

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

  private static final Map<String, String> TITLE_KEYS =
      Map.ofEntries(
          Map.entry(DV_HUB, "HelpTopics.DvHubDialog.Title"),
          Map.entry(DV_SATELLITE, "HelpTopics.DvSatelliteDialog.Title"),
          Map.entry(DV_LINK, "HelpTopics.DvLinkDialog.Title"),
          Map.entry(DV_REFERENCE, "HelpTopics.DvReferenceTableDialog.Title"),
          Map.entry(DV_MODEL, "HelpTopics.DvModelDialog.Title"),
          Map.entry(DV_NOTE, "HelpTopics.DvNoteDialog.Title"),
          Map.entry(DV_LINK_HUB_SOURCE, "HelpTopics.DvLinkHubSourceDialog.Title"),
          Map.entry(DV_LINK_SATELLITE_SOURCE, "HelpTopics.DvLinkSatelliteSourceDialog.Title"),
          Map.entry(DV_HUB_SOURCE_KEY_FIELD, "HelpTopics.HubSourceKeyFieldDialog.Title"),
          Map.entry(
              DV_SATELLITE_SOURCE_KEY_FIELD, "HelpTopics.SatelliteSourceKeyFieldDialog.Title"),
          Map.entry(BV_MODEL, "HelpTopics.BvModelDialog.Title"),
          Map.entry(BV_TABLE, "HelpTopics.BvTableDialog.Title"),
          Map.entry(BV_SCD2_TABLE, "HelpTopics.BvScd2TableDialog.Title"),
          Map.entry(DM_MODEL, "HelpTopics.DmModelDialog.Title"),
          Map.entry(DM_TABLE, "HelpTopics.DmTableDialog.Title"),
          Map.entry(DM_RANGE_DIMENSION, "HelpTopics.DmRangeDimensionDialog.Title"),
          Map.entry(ACTION_DATAVAULT_UPDATE, "HelpTopics.ActionDataVaultUpdateDialog.Title"),
          Map.entry(ACTION_DIMENSIONAL_UPDATE, "HelpTopics.ActionDimensionalUpdateDialog.Title"),
          Map.entry(
              ACTION_BUSINESSVAULT_UPDATE, "HelpTopics.ActionBusinessVaultUpdateDialog.Title"),
          Map.entry(ACTION_DIMENSIONAL_PUBLISH, "HelpTopics.ActionDimensionalPublishDialog.Title"),
          Map.entry(
              ACTION_GENERATE_EXECUTION_MAP, "HelpTopics.ActionGenerateExecutionMapDialog.Title"),
          Map.entry(ACTION_BEGIN_VAULT_UPDATE, "HelpTopics.ActionBeginVaultUpdateDialog.Title"),
          Map.entry(ACTION_END_VAULT_UPDATE, "HelpTopics.ActionEndVaultUpdateDialog.Title"),
          Map.entry(
              ACTION_VALIDATE_RESOURCE_DEFINITIONS,
              "HelpTopics.ActionValidateResourceDefinitionsDialog.Title"),
          Map.entry(ACTION_EXPORT_DATA_LINEAGE, "HelpTopics.ActionExportDataLineageDialog.Title"),
          Map.entry(ACTION_IMPORT_DBT, "HelpTopics.ActionImportDbtProjectDialog.Title"),
          Map.entry(
              IMPORT_DATABASE_TABLES_CATALOG, "HelpTopics.ImportDatabaseTablesCatalogDialog.Title"),
          Map.entry(
              IMPORT_DATABASE_TABLES_OPTIONS, "HelpTopics.ImportDatabaseTablesOptionsDialog.Title"),
          Map.entry(IMPORT_CSV_FILE_OPTIONS, "HelpTopics.ImportCsvFileOptionsDialog.Title"),
          Map.entry(IMPORT_PARQUET_FILE_OPTIONS, "HelpTopics.ImportParquetFileOptionsDialog.Title"),
          Map.entry(IMPORT_ICEBERG_TABLE, "HelpTopics.ImportIcebergTableDialog.Title"),
          Map.entry(
              IMPORT_ICEBERG_TABLE_OPTIONS, "HelpTopics.ImportIcebergTableOptionsDialog.Title"),
          Map.entry(
              IMPORT_DM_DATABASE_TABLES_OPTIONS,
              "HelpTopics.ImportDmDatabaseTablesOptionsDialog.Title"),
          Map.entry(DV_AI_ADVISOR, "HelpTopics.DvAiAdvisorDialog.Title"),
          Map.entry(BV_AI_ADVISOR, "HelpTopics.BvAiAdvisorDialog.Title"),
          Map.entry(DM_AI_ADVISOR, "HelpTopics.DmAiAdvisorDialog.Title"),
          Map.entry(PIPELINE_AI_ADVISOR, "HelpTopics.PipelineAiAdvisorDialog.Title"),
          Map.entry(WORKFLOW_AI_ADVISOR, "HelpTopics.WorkflowAiAdvisorDialog.Title"),
          Map.entry(DV_AI_PROPOSAL_REVIEW, "HelpTopics.DvAiProposalReviewDialog.Title"),
          Map.entry(HOP_AI_PROPOSAL_REVIEW, "HelpTopics.HopAiProposalReviewDialog.Title"),
          Map.entry(MODEL_AI_PROPOSAL_REVIEW, "HelpTopics.ModelAiProposalReviewDialog.Title"),
          Map.entry(RESOURCE_DEFINITION_ISSUE, "HelpTopics.ResourceDefinitionIssueDialog.Title"),
          Map.entry(
              RESOURCE_DEFINITION_VALIDATION_OPTIONS,
              "HelpTopics.ResourceDefinitionValidationOptionsDialog.Title"),
          Map.entry(
              RESOURCE_DEFINITION_VALIDATION_RESULTS,
              "HelpTopics.ResourceDefinitionValidationResultsDialog.Title"),
          Map.entry(
              ACKNOWLEDGE_VALIDATION_ISSUE, "HelpTopics.AcknowledgeValidationIssueDialog.Title"),
          Map.entry(EXECUTION_MAP_GENERATION, "HelpTopics.ExecutionMapGenerationDialog.Title"),
          Map.entry(ELK_LAYOUT, "HelpTopics.ElkLayoutDialog.Title"),
          Map.entry(
              REFRESH_RECORD_DEFINITION_FROM_SOURCE,
              "HelpTopics.RefreshRecordDefinitionFromSourceDialog.Title"),
          Map.entry(JINJA_MACRO_LIBRARY, "HelpTopics.JinjaMacroLibraryDialog.Title"),
          Map.entry(BV_DBT_IMPORT, "HelpTopics.BvDbtImportDialog.Title"),
          Map.entry(DATA_TYPE_MAPPING, "HelpTopics.DataTypeMappingDialog.Title"),
          Map.entry(TARGET_TYPE_MAPPING, "HelpTopics.TargetTypeMappingDialog.Title"),
          Map.entry(SOURCE_TO_VAULT_REVIEW, "HelpTopics.SourceToVaultReviewDialog.Title"));

  private HelpTopics() {}

  public static String titleKey(String topicId) {
    if (Utils.isEmpty(topicId)) {
      return "HelpTopics.Default.Title";
    }
    return TITLE_KEYS.getOrDefault(topicId, "HelpTopics.Default.Title");
  }
}
