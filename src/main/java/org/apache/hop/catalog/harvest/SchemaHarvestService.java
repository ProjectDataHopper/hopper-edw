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
package org.apache.hop.catalog.harvest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.hop.catalog.discovery.RecordDefinitionPhysicalRefSupport;
import org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.BaselineMode;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestRequest;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.versioning.CatalogVersionService;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.resourcedefinition.ParallelValidationSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Orchestrates schema metadata harvesting: resolve subjects → connection-batched discovery → diff
 * vs catalog contract → durable result. Does not rewrite the working catalog.
 */
public final class SchemaHarvestService {

  private SchemaHarvestService() {}

  public static HarvestResult harvest(
      HarvestRequest request,
      ILogChannel log,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    Instant started = Instant.now();
    String runId =
        !Utils.isEmpty(request.getHarvestRunId())
            ? request.getHarvestRunId().trim()
            : UUID.randomUUID().toString();

    if (Utils.isEmpty(request.getResourceGroupName())) {
      throw new HopException("Resource definition group is required for schema harvest");
    }

    List<SchemaHarvestSubjectResolver.ResolvedSubject> subjects =
        SchemaHarvestSubjectResolver.resolveFromGroup(
            request.getResourceGroupName(),
            request.getCatalogConnection(),
            request.getRecordSourceGroupFilter(),
            request.getConnectionNameFilter(),
            variables,
            metadataProvider);

    if (log != null) {
      log.logBasic(
          "Schema harvest "
              + runId
              + ": resolved "
              + subjects.size()
              + " subject(s) from group "
              + request.getResourceGroupName());
    }

    Map<String, SchemaHarvestConnectionBatcher.DiscoveryOutcome> outcomes =
        SchemaHarvestConnectionBatcher.discoverAll(subjects, variables, metadataProvider);

    int batchedConnections =
        (int) SchemaHarvestConnectionBatcher.countByConnection(subjects).keySet().stream().count();
    if (log != null) {
      log.logBasic(
          "Schema harvest "
              + runId
              + ": discovered via "
              + batchedConnections
              + " database connection partition(s)");
    }

    List<HarvestSubjectResult> subjectResults = new ArrayList<>();
    List<String> infraErrors = new ArrayList<>();

    for (SchemaHarvestSubjectResolver.ResolvedSubject subject : subjects) {
      SchemaHarvestConnectionBatcher.DiscoveryOutcome outcome = outcomes.get(subject.subjectKey());
      subjectResults.add(
          buildSubjectResult(subject, outcome, request, variables, metadataProvider, infraErrors));
    }

    HarvestStatus status = deriveStatus(subjectResults, infraErrors);
    String expectedBaseline = describeBaseline(request);

    Instant finished = Instant.now();
    HarvestResult result =
        HarvestResult.builder()
            .harvestRunId(runId)
            .startedAt(started)
            .finishedAt(finished)
            .resourceGroupName(request.getResourceGroupName())
            .catalogConnection(request.getCatalogConnection())
            .expectedBaseline(expectedBaseline)
            .status(status)
            .workflowName(request.getWorkflowName())
            .workflowExecutionId(request.getWorkflowExecutionId())
            .scopeSummary(buildScopeSummary(request, subjects.size(), batchedConnections))
            .subjects(subjectResults)
            .infraErrors(infraErrors)
            .build();

    if (log != null) {
      log.logBasic(
          "Schema harvest "
              + runId
              + " finished: status="
              + status
              + ", subjects="
              + result.subjectCount()
              + ", withChanges="
              + result.subjectsWithChanges()
              + ", changeEvents="
              + result.changeCount()
              + ", errors="
              + result.errorCount());
    }
    return result;
  }

  private static HarvestSubjectResult buildSubjectResult(
      SchemaHarvestSubjectResolver.ResolvedSubject subject,
      SchemaHarvestConnectionBatcher.DiscoveryOutcome outcome,
      HarvestRequest request,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<String> infraErrors) {
    RecordDefinition definition = subject.definition();
    String sourceTypeName = null;
    String databaseMetaName = null;
    String schemaName = null;
    String tableName = null;

    if (definition != null) {
      DvSourceType sourceType = RecordDefinitionPhysicalRefSupport.resolveSourceType(definition);
      sourceTypeName = sourceType != null ? sourceType.name() : null;
      if (definition.getPhysicalTable() != null) {
        databaseMetaName = definition.getPhysicalTable().getDatabaseMetaName();
        schemaName = definition.getPhysicalTable().getSchemaName();
        tableName = definition.getPhysicalTable().getTableName();
      }
    }

    if (definition == null) {
      return HarvestSubjectResult.builder()
          .subjectKey(subject.subjectKey())
          .catalogConnection(subject.catalogConnection())
          .discoveryStatus(DiscoveryStatus.ERROR)
          .inSync(false)
          .message("Record definition not found in catalog")
          .build();
    }

    if (!RecordDefinitionPhysicalRefSupport.supportsRefreshFromSource(definition)) {
      return HarvestSubjectResult.builder()
          .subjectKey(subject.subjectKey())
          .catalogConnection(subject.catalogConnection())
          .sourceType(sourceTypeName)
          .databaseMetaName(databaseMetaName)
          .schemaName(schemaName)
          .tableName(tableName)
          .discoveryStatus(DiscoveryStatus.UNSUPPORTED)
          .inSync(true)
          .message("Source type does not support live metadata discovery")
          .build();
    }

    if (outcome == null) {
      return HarvestSubjectResult.builder()
          .subjectKey(subject.subjectKey())
          .catalogConnection(subject.catalogConnection())
          .sourceType(sourceTypeName)
          .databaseMetaName(databaseMetaName)
          .schemaName(schemaName)
          .tableName(tableName)
          .discoveryStatus(DiscoveryStatus.ERROR)
          .inSync(false)
          .message("No discovery outcome")
          .build();
    }

    if (!Utils.isEmpty(outcome.errorMessage())) {
      DiscoveryStatus status =
          outcome.errorMessage().toLowerCase().contains("not found")
                  || outcome.errorMessage().toLowerCase().contains("does not exist")
              ? DiscoveryStatus.UNAVAILABLE
              : DiscoveryStatus.ERROR;
      return HarvestSubjectResult.builder()
          .subjectKey(subject.subjectKey())
          .catalogConnection(subject.catalogConnection())
          .sourceType(sourceTypeName)
          .databaseMetaName(databaseMetaName)
          .schemaName(schemaName)
          .tableName(tableName)
          .discoveryStatus(status)
          .inSync(false)
          .message(outcome.errorMessage())
          .build();
    }

    List<SourceField> expectedFields =
        loadExpectedFields(definition, request, variables, metadataProvider);
    List<SourceField> discoveredFields = outcome.fields();

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(expectedFields, discoveredFields);

    List<HarvestedField> fields = new ArrayList<>();
    if (expectedFields != null) {
      for (SourceField field : expectedFields) {
        HarvestedField hf = HarvestedField.fromSourceField(field, FieldRole.EXPECTED);
        if (hf != null) {
          fields.add(hf);
        }
      }
    }
    if (discoveredFields != null) {
      for (SourceField field : discoveredFields) {
        HarvestedField hf = HarvestedField.fromSourceField(field, FieldRole.DISCOVERED);
        if (hf != null) {
          fields.add(hf);
        }
      }
    }

    List<HarvestedForeignKey> foreignKeys = new ArrayList<>();
    List<HarvestedForeignKey> expectedFks = List.of();
    if (definition.getDvSource() != null) {
      expectedFks =
          SchemaHarvestFkDiffSupport.fromCatalogFields(
              definition.getDvSource().getFields(), schemaName, tableName);
      foreignKeys.addAll(expectedFks);
    }
    List<HarvestedForeignKey> discoveredFks =
        SchemaHarvestFkDiffSupport.fromDiscovered(outcome.foreignKeys(), FieldRole.DISCOVERED);
    foreignKeys.addAll(discoveredFks);

    List<HarvestChange> changes = new ArrayList<>();
    if (diff != null && diff.changes() != null) {
      for (RecordDefinitionSchemaDiffSupport.FieldChange change : diff.changes()) {
        HarvestChange hc = HarvestChange.fromFieldChange(change);
        if (hc != null) {
          changes.add(hc);
        }
      }
    }
    changes.addAll(SchemaHarvestFkDiffSupport.diff(expectedFks, discoveredFks));

    boolean inSync = changes.stream().noneMatch(c -> c != null && !"INFO".equals(c.getSeverity()));
    String message = null;
    if (!changes.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      if (diff != null && diff.hasChanges()) {
        sb.append(RecordDefinitionSchemaDiffSupport.formatDiff(diff));
      }
      long fkChanges =
          changes.stream()
              .filter(
                  c ->
                      c != null
                          && c.getChangeKind() != null
                          && c.getChangeKind().startsWith("FOREIGN_KEY"))
              .count();
      if (fkChanges > 0) {
        if (sb.length() > 0) {
          sb.append('\n');
        }
        sb.append(fkChanges).append(" foreign-key change(s) detected");
      }
      message = sb.length() > 0 ? sb.toString() : null;
    }
    return HarvestSubjectResult.builder()
        .subjectKey(subject.subjectKey())
        .catalogConnection(subject.catalogConnection())
        .sourceType(sourceTypeName)
        .databaseMetaName(databaseMetaName)
        .schemaName(schemaName)
        .tableName(tableName)
        .discoveryStatus(DiscoveryStatus.OK)
        .inSync(inSync)
        .message(message)
        .fields(fields)
        .foreignKeys(foreignKeys)
        .changes(changes)
        .build();
  }

  private static List<SourceField> loadExpectedFields(
      RecordDefinition definition,
      HarvestRequest request,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (request.getBaselineMode() == BaselineMode.CATALOG_VERSION
        && !Utils.isEmpty(request.getBaselineVersionTag())
        && !Utils.isEmpty(request.getCatalogConnection())
        && definition.getKey() != null) {
      try {
        var versioned =
            CatalogVersionService.readDefinition(
                request.getCatalogConnection(),
                request.getBaselineVersionTag(),
                definition.getKey(),
                variables,
                metadataProvider);
        if (versioned.isPresent()) {
          return DvSourceFieldSupport.sourceFieldsFromDefinition(versioned.get());
        }
      } catch (Exception ignored) {
        // Fall back to working catalog contract.
      }
    }
    try {
      return DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
    } catch (Exception e) {
      return List.of();
    }
  }

  private static HarvestStatus deriveStatus(
      List<HarvestSubjectResult> subjects, List<String> infraErrors) {
    if (infraErrors != null && !infraErrors.isEmpty()) {
      return HarvestStatus.FAILED;
    }
    if (subjects == null || subjects.isEmpty()) {
      return HarvestStatus.SUCCESS;
    }
    boolean anyError = false;
    boolean anyOk = false;
    for (HarvestSubjectResult subject : subjects) {
      if (subject == null) {
        continue;
      }
      if (subject.getDiscoveryStatus() == DiscoveryStatus.OK
          || subject.getDiscoveryStatus() == DiscoveryStatus.UNSUPPORTED) {
        anyOk = true;
      } else {
        anyError = true;
      }
    }
    if (anyError && anyOk) {
      return HarvestStatus.PARTIAL;
    }
    if (anyError) {
      return HarvestStatus.FAILED;
    }
    return HarvestStatus.SUCCESS;
  }

  private static String describeBaseline(HarvestRequest request) {
    if (request.getBaselineMode() == BaselineMode.CATALOG_VERSION
        && !Utils.isEmpty(request.getBaselineVersionTag())) {
      return "VERSION:" + request.getBaselineVersionTag().trim();
    }
    return "WORKING";
  }

  private static String buildScopeSummary(
      HarvestRequest request, int subjectCount, int connectionPartitions) {
    StringBuilder sb = new StringBuilder();
    sb.append("group=").append(Const.NVL(request.getResourceGroupName(), ""));
    sb.append("; subjects=").append(subjectCount);
    sb.append("; dbPartitions=").append(connectionPartitions);
    if (!Utils.isEmpty(request.getRecordSourceGroupFilter())) {
      sb.append("; recordSourceGroup=").append(request.getRecordSourceGroupFilter());
    }
    if (!Utils.isEmpty(request.getConnectionNameFilter())) {
      sb.append("; connection=").append(request.getConnectionNameFilter());
    }
    return sb.toString();
  }

  /** Formats a short Markdown report for logging or file output. */
  public static String formatMarkdownReport(HarvestResult result) {
    if (result == null) {
      return "# Schema harvest\n\n(no result)\n";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("# Schema metadata harvest\n\n");
    sb.append("- **Run id:** `").append(Const.NVL(result.getHarvestRunId(), "")).append("`\n");
    sb.append("- **Group:** ").append(Const.NVL(result.getResourceGroupName(), "")).append("\n");
    sb.append("- **Baseline:** ")
        .append(Const.NVL(result.getExpectedBaseline(), "WORKING"))
        .append("\n");
    sb.append("- **Status:** ").append(result.getStatus()).append("\n");
    sb.append("- **Subjects:** ").append(result.subjectCount()).append("\n");
    sb.append("- **Subjects with changes:** ").append(result.subjectsWithChanges()).append("\n");
    sb.append("- **Change events:** ").append(result.changeCount()).append("\n");
    sb.append("- **Errors:** ").append(result.errorCount()).append("\n");
    if (!Utils.isEmpty(result.getScopeSummary())) {
      sb.append("- **Scope:** ").append(result.getScopeSummary()).append("\n");
    }
    sb.append("\n## Subjects\n\n");
    for (HarvestSubjectResult subject : result.subjectsView()) {
      if (subject == null) {
        continue;
      }
      sb.append("### `").append(subject.getSubjectKey()).append("`\n\n");
      sb.append("- Discovery: ").append(subject.getDiscoveryStatus()).append("\n");
      sb.append("- In sync: ").append(subject.isInSync()).append("\n");
      if (!Utils.isEmpty(subject.getSourceType())) {
        sb.append("- Source type: ").append(subject.getSourceType()).append("\n");
      }
      if (!Utils.isEmpty(subject.getDatabaseMetaName())) {
        sb.append("- Database: ").append(subject.getDatabaseMetaName());
        if (!Utils.isEmpty(subject.getSchemaName())) {
          sb.append(" / ").append(subject.getSchemaName());
        }
        if (!Utils.isEmpty(subject.getTableName())) {
          sb.append(" / ").append(subject.getTableName());
        }
        sb.append("\n");
      }
      if (!Utils.isEmpty(subject.getMessage())) {
        sb.append("- Message: ").append(subject.getMessage().replace("\n", " ")).append("\n");
      }
      if (subject.getChanges() != null && !subject.getChanges().isEmpty()) {
        sb.append("- Changes:\n");
        for (HarvestChange change : subject.getChanges()) {
          sb.append("  - ")
              .append(change.getChangeKind())
              .append(" ")
              .append(Const.NVL(change.getFieldName(), ""))
              .append(" ")
              .append(Const.NVL(change.getActualDetail(), ""))
              .append("\n");
        }
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  public static int resolveConnectionParallelism(String configured) {
    return ParallelValidationSupport.resolveParallelism(
        configured, ParallelValidationSupport.DEFAULT_PARALLELISM);
  }
}
