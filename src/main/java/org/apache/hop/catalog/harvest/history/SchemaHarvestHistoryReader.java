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
package org.apache.hop.catalog.harvest.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.DiscoveryStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.FieldRole;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestStatus;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.apache.hop.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Loads persisted schema harvest runs from OPS tables for schema-gate reuse (no second live
 * discovery).
 */
public final class SchemaHarvestHistoryReader {

  public static final String RESOLVED_FROM_VARIABLE = "variable";
  public static final String RESOLVED_FROM_EXPLICIT = "explicit";
  public static final String RESOLVED_FROM_CATALOG = "catalog:schema_harvest_run";
  public static final String RESOLVED_FROM_OPS_DEFAULT = "default:OPS";

  private SchemaHarvestHistoryReader() {}

  public static final int DEFAULT_HISTORY_LIMIT = 50;

  public static final String MSG_NOT_CONFIGURED =
      "No schema harvest history connection configured. Set SCHEMA_HARVEST_DATABASE, publish"
          + " schema_harvest_run to the catalog, or use OPS.";

  public static final String MSG_TABLES_MISSING =
      "No schema harvest history tables (run Harvest source metadata with auto-create).";

  public static final String MSG_NO_HISTORY = "No schema harvest history for this scope yet.";

  public record HistoryConnection(
      String databaseMetaName, String schemaName, String resolvedFrom) {}

  /** One harvest run header for the browser. */
  public record HarvestRunSummary(
      String harvestRunId,
      Date finishedAt,
      String resourceGroupName,
      String status,
      Long subjectCount,
      Long changeCount,
      Long errorCount,
      Long subjectsWithChanges,
      String expectedBaseline,
      String scopeSummary) {}

  /** One subject row within a harvest run (or subject timeline). */
  public record HarvestSubjectSummary(
      String harvestRunId,
      Date finishedAt,
      String subjectKey,
      String sourceType,
      String databaseMetaName,
      String schemaName,
      String tableName,
      String discoveryStatus,
      boolean inSync,
      Long changeCount,
      String message) {}

  /**
   * Resolution order:
   *
   * <ol>
   *   <li>Explicit database name (action field)
   *   <li>{@code SCHEMA_HARVEST_DATABASE} / {@code SCHEMA_HARVEST_SCHEMA}
   *   <li>Catalog operations definition {@code schema_harvest_run}
   *   <li>Hop connection named {@code OPS} when present
   * </ol>
   */
  public static HistoryConnection resolveConnection(
      String explicitDatabase,
      String explicitSchema,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (!Utils.isEmpty(explicitDatabase)) {
      String schema =
          !Utils.isEmpty(explicitSchema)
              ? explicitSchema.trim()
              : SchemaHarvestHistoryPublisher.DEFAULT_SCHEMA_NAME;
      return new HistoryConnection(explicitDatabase.trim(), schema, RESOLVED_FROM_EXPLICIT);
    }

    String fromVar =
        variableValue(variables, SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_DATABASE);
    if (!Utils.isEmpty(fromVar)) {
      String schema =
          variableValue(variables, SchemaHarvestHistoryPublisher.VAR_SCHEMA_HARVEST_SCHEMA);
      if (Utils.isEmpty(schema)) {
        schema = SchemaHarvestHistoryPublisher.DEFAULT_SCHEMA_NAME;
      }
      return new HistoryConnection(fromVar.trim(), schema.trim(), RESOLVED_FROM_VARIABLE);
    }

    HistoryConnection fromCatalog =
        connectionFromCatalogDefinition(catalogConnectionName, variables, metadataProvider);
    if (fromCatalog != null) {
      return fromCatalog;
    }

    if (metadataProvider != null) {
      try {
        DatabaseMeta ops = metadataProvider.getSerializer(DatabaseMeta.class).load("OPS");
        if (ops != null) {
          return new HistoryConnection(
              "OPS", SchemaHarvestHistoryPublisher.DEFAULT_SCHEMA_NAME, RESOLVED_FROM_OPS_DEFAULT);
        }
      } catch (Exception ignored) {
        // fall through
      }
    }
    return null;
  }

  /**
   * Latest harvest_run_id for a resource group (by finished_at, then started_at), or null when
   * none.
   */
  public static String findLatestRunId(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String resourceGroupName,
      IVariables variables)
      throws HopException {
    if (databaseMeta == null || Utils.isEmpty(resourceGroupName)) {
      return null;
    }
    String schema = SchemaHarvestHistoryDdlSupport.resolveSchema(operationsSchema);
    LoggingObject loggingObject = new LoggingObject(SchemaHarvestHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!harvestTablesExist(db, schema)) {
        throw new HopException(
            "Schema harvest history tables are missing (run Harvest source metadata with"
                + " auto-create).");
      }
      String runTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN);
      String sql =
          "SELECT harvest_run_id FROM "
              + runTable
              + " WHERE resource_group_name = "
              + SchemaHarvestHistoryPublisher.sqlLiteral(resourceGroupName.trim())
              + " ORDER BY finished_at DESC, started_at DESC";
      db.setQueryLimit(1);
      List<Object[]> rows = db.getRows(sql, 1);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return null;
      }
      return stringValue(rowMeta, rows.get(0), "harvest_run_id");
    } finally {
      db.disconnect();
    }
  }

  /** Full harvest result for a run id (subjects, fields, changes). */
  public static HarvestResult loadHarvestResult(
      DatabaseMeta databaseMeta, String operationsSchema, String harvestRunId, IVariables variables)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Harvest history database meta is null");
    }
    if (Utils.isEmpty(harvestRunId)) {
      throw new HopException("Harvest run id is required");
    }
    String schema = SchemaHarvestHistoryDdlSupport.resolveSchema(operationsSchema);
    String runId = harvestRunId.trim();

    LoggingObject loggingObject = new LoggingObject(SchemaHarvestHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!harvestTablesExist(db, schema)) {
        throw new HopException(
            "Schema harvest history tables are missing (run Harvest source metadata with"
                + " auto-create).");
      }

      HarvestResult header = loadRunHeader(db, databaseMeta, schema, runId);
      if (header == null) {
        throw new HopException("Harvest run not found: " + runId);
      }

      Map<String, HarvestSubjectResult> subjects = loadSubjects(db, databaseMeta, schema, runId);
      Map<String, List<HarvestedField>> fieldsBySubject =
          loadFields(db, databaseMeta, schema, runId);
      Map<String, List<HarvestChange>> changesBySubject =
          loadChanges(db, databaseMeta, schema, runId);
      Map<String, List<HarvestedForeignKey>> fksBySubject =
          loadForeignKeys(db, databaseMeta, schema, runId);

      List<HarvestSubjectResult> subjectList = new ArrayList<>();
      for (Map.Entry<String, HarvestSubjectResult> entry : subjects.entrySet()) {
        String subjectKey = entry.getKey();
        HarvestSubjectResult base = entry.getValue();
        subjectList.add(
            HarvestSubjectResult.builder()
                .subjectKey(base.getSubjectKey())
                .catalogConnection(base.getCatalogConnection())
                .sourceType(base.getSourceType())
                .databaseMetaName(base.getDatabaseMetaName())
                .schemaName(base.getSchemaName())
                .tableName(base.getTableName())
                .discoveryStatus(base.getDiscoveryStatus())
                .inSync(base.isInSync())
                .message(base.getMessage())
                .fields(fieldsBySubject.getOrDefault(subjectKey, List.of()))
                .changes(changesBySubject.getOrDefault(subjectKey, List.of()))
                .foreignKeys(fksBySubject.getOrDefault(subjectKey, List.of()))
                .build());
      }
      return HarvestResult.builder()
          .harvestRunId(header.getHarvestRunId())
          .startedAt(header.getStartedAt())
          .finishedAt(header.getFinishedAt())
          .resourceGroupName(header.getResourceGroupName())
          .catalogConnection(header.getCatalogConnection())
          .expectedBaseline(header.getExpectedBaseline())
          .status(header.getStatus())
          .workflowName(header.getWorkflowName())
          .workflowExecutionId(header.getWorkflowExecutionId())
          .scopeSummary(header.getScopeSummary())
          .subjects(subjectList)
          .build();
    } finally {
      db.disconnect();
    }
  }

  public static boolean harvestTablesExist(Database db, String operationsSchema)
      throws HopException {
    if (db == null) {
      return false;
    }
    String schema = SchemaHarvestHistoryDdlSupport.resolveSchema(operationsSchema);
    return db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN)
        && db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT);
  }

  public static DatabaseMeta loadDatabaseMeta(
      String databaseMetaName, IHopMetadataProvider metadataProvider) throws HopException {
    if (Utils.isEmpty(databaseMetaName) || metadataProvider == null) {
      return null;
    }
    return metadataProvider.getSerializer(DatabaseMeta.class).load(databaseMetaName.trim());
  }

  /**
   * Recent harvest runs, newest first. Optional {@code resourceGroupName} filter; empty means all
   * groups.
   */
  public static List<HarvestRunSummary> listRuns(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String resourceGroupName,
      IVariables variables,
      int limit)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Harvest history database meta is null");
    }
    String schema = SchemaHarvestHistoryDdlSupport.resolveSchema(operationsSchema);
    int rowLimit = limit > 0 ? limit : DEFAULT_HISTORY_LIMIT;

    LoggingObject loggingObject = new LoggingObject(SchemaHarvestHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!harvestTablesExist(db, schema)) {
        throw new HopException(MSG_TABLES_MISSING);
      }
      String runTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN);
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT harvest_run_id, finished_at, resource_group_name, status, subject_count,")
          .append(
              " change_count, error_count, subjects_with_changes, expected_baseline, scope_summary")
          .append(" FROM ")
          .append(runTable);
      if (!Utils.isEmpty(resourceGroupName)) {
        sql.append(" WHERE resource_group_name = ")
            .append(SchemaHarvestHistoryPublisher.sqlLiteral(resourceGroupName.trim()));
      }
      sql.append(" ORDER BY finished_at DESC, started_at DESC");

      db.setQueryLimit(rowLimit);
      List<Object[]> rows = db.getRows(sql.toString(), rowLimit);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return List.of();
      }
      List<HarvestRunSummary> entries = new ArrayList<>(rows.size());
      for (Object[] row : rows) {
        Instant finished = instantValue(rowMeta, row, "finished_at");
        entries.add(
            new HarvestRunSummary(
                stringValue(rowMeta, row, "harvest_run_id"),
                finished != null ? Date.from(finished) : null,
                stringValue(rowMeta, row, "resource_group_name"),
                stringValue(rowMeta, row, "status"),
                longValue(rowMeta, row, "subject_count"),
                longValue(rowMeta, row, "change_count"),
                longValue(rowMeta, row, "error_count"),
                longValue(rowMeta, row, "subjects_with_changes"),
                stringValue(rowMeta, row, "expected_baseline"),
                stringValue(rowMeta, row, "scope_summary")));
      }
      return entries;
    } finally {
      db.disconnect();
    }
  }

  /**
   * Subjects for one harvest run. Optional filters: only rows with changes, database connection
   * name, source type.
   */
  public static List<HarvestSubjectSummary> listSubjectsForRun(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String harvestRunId,
      String databaseMetaNameFilter,
      String sourceTypeFilter,
      boolean onlyWithChanges,
      IVariables variables)
      throws HopException {
    if (databaseMeta == null || Utils.isEmpty(harvestRunId)) {
      return List.of();
    }
    String schema = SchemaHarvestHistoryDdlSupport.resolveSchema(operationsSchema);
    LoggingObject loggingObject = new LoggingObject(SchemaHarvestHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!harvestTablesExist(db, schema)) {
        throw new HopException(MSG_TABLES_MISSING);
      }
      String subjectTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT);
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT subject_key, source_type, database_meta_name, schema_name, table_name,")
          .append(" discovery_status, in_sync, change_count, message FROM ")
          .append(subjectTable)
          .append(" WHERE harvest_run_id = ")
          .append(SchemaHarvestHistoryPublisher.sqlLiteral(harvestRunId.trim()));
      if (!Utils.isEmpty(databaseMetaNameFilter)) {
        sql.append(" AND database_meta_name = ")
            .append(SchemaHarvestHistoryPublisher.sqlLiteral(databaseMetaNameFilter.trim()));
      }
      if (!Utils.isEmpty(sourceTypeFilter)) {
        sql.append(" AND source_type = ")
            .append(
                SchemaHarvestHistoryPublisher.sqlLiteral(sourceTypeFilter.trim().toUpperCase()));
      }
      if (onlyWithChanges) {
        sql.append(" AND (change_count > 0 OR in_sync = ")
            .append(booleanFalseLiteral(databaseMeta))
            .append(")");
      }
      sql.append(" ORDER BY subject_key");

      List<Object[]> rows = db.getRows(sql.toString(), 100_000);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return List.of();
      }
      List<HarvestSubjectSummary> entries = new ArrayList<>(rows.size());
      for (Object[] row : rows) {
        Boolean inSync = booleanValue(rowMeta, row, "in_sync");
        entries.add(
            new HarvestSubjectSummary(
                harvestRunId.trim(),
                null,
                stringValue(rowMeta, row, "subject_key"),
                stringValue(rowMeta, row, "source_type"),
                stringValue(rowMeta, row, "database_meta_name"),
                stringValue(rowMeta, row, "schema_name"),
                stringValue(rowMeta, row, "table_name"),
                stringValue(rowMeta, row, "discovery_status"),
                inSync != null && inSync,
                longValue(rowMeta, row, "change_count"),
                stringValue(rowMeta, row, "message")));
      }
      return entries;
    } finally {
      db.disconnect();
    }
  }

  /** Timeline of harvest subject rows for one catalog key, newest first. */
  public static List<HarvestSubjectSummary> listSubjectHistory(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String subjectKey,
      IVariables variables,
      int limit)
      throws HopException {
    if (databaseMeta == null || Utils.isEmpty(subjectKey)) {
      return List.of();
    }
    String schema = SchemaHarvestHistoryDdlSupport.resolveSchema(operationsSchema);
    int rowLimit = limit > 0 ? limit : DEFAULT_HISTORY_LIMIT;
    LoggingObject loggingObject = new LoggingObject(SchemaHarvestHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!harvestTablesExist(db, schema)) {
        throw new HopException(MSG_TABLES_MISSING);
      }
      String subjectTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT);
      String runTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN);
      String sql =
          "SELECT s.harvest_run_id, r.finished_at, s.subject_key, s.source_type,"
              + " s.database_meta_name, s.schema_name, s.table_name, s.discovery_status,"
              + " s.in_sync, s.change_count, s.message FROM "
              + subjectTable
              + " s JOIN "
              + runTable
              + " r ON r.harvest_run_id = s.harvest_run_id WHERE s.subject_key = "
              + SchemaHarvestHistoryPublisher.sqlLiteral(subjectKey.trim())
              + " ORDER BY r.finished_at DESC, r.started_at DESC";

      db.setQueryLimit(rowLimit);
      List<Object[]> rows = db.getRows(sql, rowLimit);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return List.of();
      }
      List<HarvestSubjectSummary> entries = new ArrayList<>(rows.size());
      for (Object[] row : rows) {
        Instant finished = instantValue(rowMeta, row, "finished_at");
        Boolean inSync = booleanValue(rowMeta, row, "in_sync");
        entries.add(
            new HarvestSubjectSummary(
                stringValue(rowMeta, row, "harvest_run_id"),
                finished != null ? Date.from(finished) : null,
                stringValue(rowMeta, row, "subject_key"),
                stringValue(rowMeta, row, "source_type"),
                stringValue(rowMeta, row, "database_meta_name"),
                stringValue(rowMeta, row, "schema_name"),
                stringValue(rowMeta, row, "table_name"),
                stringValue(rowMeta, row, "discovery_status"),
                inSync != null && inSync,
                longValue(rowMeta, row, "change_count"),
                stringValue(rowMeta, row, "message")));
      }
      return entries;
    } finally {
      db.disconnect();
    }
  }

  /** Change events for one subject in one harvest run, ordered by sequence. */
  public static List<HarvestChange> listChangesForSubject(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String harvestRunId,
      String subjectKey,
      IVariables variables)
      throws HopException {
    if (databaseMeta == null || Utils.isEmpty(harvestRunId) || Utils.isEmpty(subjectKey)) {
      return List.of();
    }
    String schema = SchemaHarvestHistoryDdlSupport.resolveSchema(operationsSchema);
    LoggingObject loggingObject = new LoggingObject(SchemaHarvestHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE)) {
        return List.of();
      }
      String changeTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE);
      String sql =
          "SELECT change_kind, field_name, expected_detail, actual_detail, severity FROM "
              + changeTable
              + " WHERE harvest_run_id = "
              + SchemaHarvestHistoryPublisher.sqlLiteral(harvestRunId.trim())
              + " AND subject_key = "
              + SchemaHarvestHistoryPublisher.sqlLiteral(subjectKey.trim())
              + " ORDER BY change_seq";
      List<Object[]> rows = db.getRows(sql, 100_000);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return List.of();
      }
      List<HarvestChange> changes = new ArrayList<>(rows.size());
      for (Object[] row : rows) {
        changes.add(
            HarvestChange.builder()
                .changeKind(stringValue(rowMeta, row, "change_kind"))
                .fieldName(stringValue(rowMeta, row, "field_name"))
                .expectedDetail(stringValue(rowMeta, row, "expected_detail"))
                .actualDetail(stringValue(rowMeta, row, "actual_detail"))
                .severity(stringValue(rowMeta, row, "severity"))
                .build());
      }
      return changes;
    } finally {
      db.disconnect();
    }
  }

  /** Field snapshots (EXPECTED / DISCOVERED) for one subject in one harvest run. */
  public static List<HarvestedField> listFieldsForSubject(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String harvestRunId,
      String subjectKey,
      IVariables variables)
      throws HopException {
    if (databaseMeta == null || Utils.isEmpty(harvestRunId) || Utils.isEmpty(subjectKey)) {
      return List.of();
    }
    String schema = SchemaHarvestHistoryDdlSupport.resolveSchema(operationsSchema);
    LoggingObject loggingObject = new LoggingObject(SchemaHarvestHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD)) {
        return List.of();
      }
      String fieldTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD);
      String sql =
          "SELECT field_role, field_name, hop_type, field_length, field_precision,"
              + " primary_key_position, source_data_type FROM "
              + fieldTable
              + " WHERE harvest_run_id = "
              + SchemaHarvestHistoryPublisher.sqlLiteral(harvestRunId.trim())
              + " AND subject_key = "
              + SchemaHarvestHistoryPublisher.sqlLiteral(subjectKey.trim())
              + " ORDER BY field_role, field_name";
      List<Object[]> rows = db.getRows(sql, 100_000);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return List.of();
      }
      List<HarvestedField> fields = new ArrayList<>(rows.size());
      for (Object[] row : rows) {
        Long pk = longValue(rowMeta, row, "primary_key_position");
        fields.add(
            HarvestedField.builder()
                .role(parseRole(stringValue(rowMeta, row, "field_role")))
                .fieldName(stringValue(rowMeta, row, "field_name"))
                .hopType(stringValue(rowMeta, row, "hop_type"))
                .length(stringValue(rowMeta, row, "field_length"))
                .precision(stringValue(rowMeta, row, "field_precision"))
                .primaryKeyPosition(pk != null ? pk.intValue() : 0)
                .sourceDataType(stringValue(rowMeta, row, "source_data_type"))
                .build());
      }
      return fields;
    } finally {
      db.disconnect();
    }
  }

  private static String booleanFalseLiteral(DatabaseMeta databaseMeta) {
    if (databaseMeta != null && !Utils.isEmpty(databaseMeta.getPluginId())) {
      String pluginId = databaseMeta.getPluginId().toUpperCase();
      if ("MYSQL".equals(pluginId) || "SINGLESTORE".equals(pluginId)) {
        return "0";
      }
    }
    return "FALSE";
  }

  private static HarvestResult loadRunHeader(
      Database db, DatabaseMeta databaseMeta, String schema, String runId) throws HopException {
    String runTable =
        databaseMeta.getQuotedSchemaTableCombination(
            db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN);
    String sql =
        "SELECT harvest_run_id, started_at, finished_at, resource_group_name, catalog_connection,"
            + " expected_baseline, status, workflow_name, workflow_execution_id, scope_summary"
            + " FROM "
            + runTable
            + " WHERE harvest_run_id = "
            + SchemaHarvestHistoryPublisher.sqlLiteral(runId);
    List<Object[]> rows = db.getRows(sql, 1);
    IRowMeta rowMeta = db.getReturnRowMeta();
    if (rows == null || rows.isEmpty() || rowMeta == null) {
      return null;
    }
    Object[] row = rows.get(0);
    return HarvestResult.builder()
        .harvestRunId(stringValue(rowMeta, row, "harvest_run_id"))
        .startedAt(instantValue(rowMeta, row, "started_at"))
        .finishedAt(instantValue(rowMeta, row, "finished_at"))
        .resourceGroupName(stringValue(rowMeta, row, "resource_group_name"))
        .catalogConnection(stringValue(rowMeta, row, "catalog_connection"))
        .expectedBaseline(stringValue(rowMeta, row, "expected_baseline"))
        .status(parseStatus(stringValue(rowMeta, row, "status")))
        .workflowName(stringValue(rowMeta, row, "workflow_name"))
        .workflowExecutionId(stringValue(rowMeta, row, "workflow_execution_id"))
        .scopeSummary(stringValue(rowMeta, row, "scope_summary"))
        .build();
  }

  private static Map<String, HarvestSubjectResult> loadSubjects(
      Database db, DatabaseMeta databaseMeta, String schema, String runId) throws HopException {
    String subjectTable =
        databaseMeta.getQuotedSchemaTableCombination(
            db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_SUBJECT);
    String sql =
        "SELECT subject_key, catalog_connection, source_type, database_meta_name, schema_name,"
            + " table_name, discovery_status, in_sync, message FROM "
            + subjectTable
            + " WHERE harvest_run_id = "
            + SchemaHarvestHistoryPublisher.sqlLiteral(runId)
            + " ORDER BY subject_key";
    List<Object[]> rows = db.getRows(sql, 100_000);
    IRowMeta rowMeta = db.getReturnRowMeta();
    Map<String, HarvestSubjectResult> map = new LinkedHashMap<>();
    if (rows == null || rowMeta == null) {
      return map;
    }
    for (Object[] row : rows) {
      String subjectKey = stringValue(rowMeta, row, "subject_key");
      if (Utils.isEmpty(subjectKey)) {
        continue;
      }
      Boolean inSync = booleanValue(rowMeta, row, "in_sync");
      map.put(
          subjectKey,
          HarvestSubjectResult.builder()
              .subjectKey(subjectKey)
              .catalogConnection(stringValue(rowMeta, row, "catalog_connection"))
              .sourceType(stringValue(rowMeta, row, "source_type"))
              .databaseMetaName(stringValue(rowMeta, row, "database_meta_name"))
              .schemaName(stringValue(rowMeta, row, "schema_name"))
              .tableName(stringValue(rowMeta, row, "table_name"))
              .discoveryStatus(parseDiscovery(stringValue(rowMeta, row, "discovery_status")))
              .inSync(inSync != null && inSync)
              .message(stringValue(rowMeta, row, "message"))
              .build());
    }
    return map;
  }

  private static Map<String, List<HarvestedField>> loadFields(
      Database db, DatabaseMeta databaseMeta, String schema, String runId) throws HopException {
    String fieldTable =
        databaseMeta.getQuotedSchemaTableCombination(
            db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD);
    if (!db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FIELD)) {
      return Map.of();
    }
    String sql =
        "SELECT subject_key, field_role, field_name, hop_type, field_length, field_precision,"
            + " primary_key_position, source_data_type FROM "
            + fieldTable
            + " WHERE harvest_run_id = "
            + SchemaHarvestHistoryPublisher.sqlLiteral(runId)
            + " ORDER BY subject_key, field_role, field_name";
    List<Object[]> rows = db.getRows(sql, 500_000);
    IRowMeta rowMeta = db.getReturnRowMeta();
    Map<String, List<HarvestedField>> map = new LinkedHashMap<>();
    if (rows == null || rowMeta == null) {
      return map;
    }
    for (Object[] row : rows) {
      String subjectKey = stringValue(rowMeta, row, "subject_key");
      if (Utils.isEmpty(subjectKey)) {
        continue;
      }
      Long pk = longValue(rowMeta, row, "primary_key_position");
      HarvestedField field =
          HarvestedField.builder()
              .role(parseRole(stringValue(rowMeta, row, "field_role")))
              .fieldName(stringValue(rowMeta, row, "field_name"))
              .hopType(stringValue(rowMeta, row, "hop_type"))
              .length(stringValue(rowMeta, row, "field_length"))
              .precision(stringValue(rowMeta, row, "field_precision"))
              .primaryKeyPosition(pk != null ? pk.intValue() : 0)
              .sourceDataType(stringValue(rowMeta, row, "source_data_type"))
              .build();
      map.computeIfAbsent(subjectKey, k -> new ArrayList<>()).add(field);
    }
    return map;
  }

  private static Map<String, List<HarvestChange>> loadChanges(
      Database db, DatabaseMeta databaseMeta, String schema, String runId) throws HopException {
    String changeTable =
        databaseMeta.getQuotedSchemaTableCombination(
            db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE);
    if (!db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_CHANGE)) {
      return Map.of();
    }
    String sql =
        "SELECT subject_key, change_kind, field_name, expected_detail, actual_detail, severity"
            + " FROM "
            + changeTable
            + " WHERE harvest_run_id = "
            + SchemaHarvestHistoryPublisher.sqlLiteral(runId)
            + " ORDER BY change_seq";
    List<Object[]> rows = db.getRows(sql, 100_000);
    IRowMeta rowMeta = db.getReturnRowMeta();
    Map<String, List<HarvestChange>> map = new LinkedHashMap<>();
    if (rows == null || rowMeta == null) {
      return map;
    }
    for (Object[] row : rows) {
      String subjectKey = stringValue(rowMeta, row, "subject_key");
      if (Utils.isEmpty(subjectKey)) {
        continue;
      }
      HarvestChange change =
          HarvestChange.builder()
              .changeKind(stringValue(rowMeta, row, "change_kind"))
              .fieldName(stringValue(rowMeta, row, "field_name"))
              .expectedDetail(stringValue(rowMeta, row, "expected_detail"))
              .actualDetail(stringValue(rowMeta, row, "actual_detail"))
              .severity(stringValue(rowMeta, row, "severity"))
              .build();
      map.computeIfAbsent(subjectKey, k -> new ArrayList<>()).add(change);
    }
    return map;
  }

  private static Map<String, List<HarvestedForeignKey>> loadForeignKeys(
      Database db, DatabaseMeta databaseMeta, String schema, String runId) throws HopException {
    if (!db.checkTableExists(schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK)) {
      return Map.of();
    }
    String fkTable =
        databaseMeta.getQuotedSchemaTableCombination(
            db, schema, SchemaHarvestHistoryPublisher.TABLE_HARVEST_FK);
    String sql =
        "SELECT subject_key, field_role, constraint_name, child_schema, child_table, child_columns,"
            + " parent_schema, parent_table, parent_columns FROM "
            + fkTable
            + " WHERE harvest_run_id = "
            + SchemaHarvestHistoryPublisher.sqlLiteral(runId)
            + " ORDER BY subject_key, field_role, constraint_name";
    List<Object[]> rows = db.getRows(sql, 100_000);
    IRowMeta rowMeta = db.getReturnRowMeta();
    Map<String, List<HarvestedForeignKey>> map = new LinkedHashMap<>();
    if (rows == null || rowMeta == null) {
      return map;
    }
    for (Object[] row : rows) {
      String subjectKey = stringValue(rowMeta, row, "subject_key");
      if (Utils.isEmpty(subjectKey)) {
        continue;
      }
      HarvestedForeignKey fk =
          HarvestedForeignKey.builder()
              .role(parseRole(stringValue(rowMeta, row, "field_role")))
              .constraintName(stringValue(rowMeta, row, "constraint_name"))
              .childSchema(stringValue(rowMeta, row, "child_schema"))
              .childTable(stringValue(rowMeta, row, "child_table"))
              .childColumns(stringValue(rowMeta, row, "child_columns"))
              .parentSchema(stringValue(rowMeta, row, "parent_schema"))
              .parentTable(stringValue(rowMeta, row, "parent_table"))
              .parentColumns(stringValue(rowMeta, row, "parent_columns"))
              .build();
      map.computeIfAbsent(subjectKey, k -> new ArrayList<>()).add(fk);
    }
    return map;
  }

  private static HistoryConnection connectionFromCatalogDefinition(
      String catalogConnectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName) || metadataProvider == null) {
      return null;
    }
    String namespace = SchemaHarvestHistoryPublisher.operationsNamespace(variables);
    RecordDefinition definition =
        RecordDefinitionRegistry.getInstance()
            .read(
                catalogConnectionName,
                new RecordDefinitionKey(namespace, SchemaHarvestHistoryPublisher.TABLE_HARVEST_RUN),
                variables,
                metadataProvider);
    if (definition == null || definition.getPhysicalTable() == null) {
      return null;
    }
    PhysicalTableRef table = definition.getPhysicalTable();
    if (Utils.isEmpty(table.getDatabaseMetaName())) {
      return null;
    }
    String schema =
        Utils.isEmpty(table.getSchemaName())
            ? SchemaHarvestHistoryPublisher.DEFAULT_SCHEMA_NAME
            : table.getSchemaName();
    return new HistoryConnection(table.getDatabaseMetaName(), schema, RESOLVED_FROM_CATALOG);
  }

  private static String variableValue(IVariables variables, String name) {
    if (variables == null || Utils.isEmpty(name)) {
      return null;
    }
    String raw = variables.getVariable(name);
    if (Utils.isEmpty(raw)) {
      // also try resolve form
      String resolved = variables.resolve("${" + name + "}");
      if (!Utils.isEmpty(resolved) && !resolved.contains("${")) {
        return resolved;
      }
      return null;
    }
    return raw;
  }

  private static HarvestStatus parseStatus(String value) {
    if (Utils.isEmpty(value)) {
      return HarvestStatus.SUCCESS;
    }
    try {
      return HarvestStatus.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return HarvestStatus.SUCCESS;
    }
  }

  private static DiscoveryStatus parseDiscovery(String value) {
    if (Utils.isEmpty(value)) {
      return DiscoveryStatus.OK;
    }
    try {
      return DiscoveryStatus.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return DiscoveryStatus.ERROR;
    }
  }

  private static FieldRole parseRole(String value) {
    if (Utils.isEmpty(value)) {
      return FieldRole.DISCOVERED;
    }
    try {
      return FieldRole.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return FieldRole.DISCOVERED;
    }
  }

  private static String stringValue(IRowMeta rowMeta, Object[] row, String name) {
    try {
      int idx = rowMeta.indexOfValue(name);
      if (idx < 0) {
        return null;
      }
      Object v = row[idx];
      return v != null ? v.toString() : null;
    } catch (Exception e) {
      return null;
    }
  }

  private static Long longValue(IRowMeta rowMeta, Object[] row, String name) {
    try {
      int idx = rowMeta.indexOfValue(name);
      if (idx < 0 || row[idx] == null) {
        return null;
      }
      Object v = row[idx];
      if (v instanceof Number n) {
        return n.longValue();
      }
      return Long.parseLong(v.toString());
    } catch (Exception e) {
      return null;
    }
  }

  private static Boolean booleanValue(IRowMeta rowMeta, Object[] row, String name) {
    try {
      int idx = rowMeta.indexOfValue(name);
      if (idx < 0 || row[idx] == null) {
        return null;
      }
      Object v = row[idx];
      if (v instanceof Boolean b) {
        return b;
      }
      if (v instanceof Number n) {
        return n.intValue() != 0;
      }
      String s = v.toString();
      return "Y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s) || "1".equals(s);
    } catch (Exception e) {
      return null;
    }
  }

  private static Instant instantValue(IRowMeta rowMeta, Object[] row, String name) {
    try {
      int idx = rowMeta.indexOfValue(name);
      if (idx < 0 || row[idx] == null) {
        return null;
      }
      Object v = row[idx];
      if (v instanceof Date d) {
        return d.toInstant();
      }
      if (v instanceof Instant i) {
        return i;
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }
}
