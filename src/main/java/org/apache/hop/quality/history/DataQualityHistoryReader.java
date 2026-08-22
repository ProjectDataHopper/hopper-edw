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
package org.apache.hop.quality.history;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
 * Resolves the quality history ops connection and queries profile-subject history / findings for
 * the catalog Quality tab History browser (design §6).
 *
 * <p>Stays free of {@code datavault} packages; project key resolution copies {@code
 * DvCatalogNamespaces.resolveProjectKey} (PROJECT_HOME basename, else {@code "project"}).
 */
public final class DataQualityHistoryReader {

  public static final int DEFAULT_HISTORY_LIMIT = 50;

  public static final String RESOLVED_FROM_VARIABLE = "variable";
  public static final String RESOLVED_FROM_QUALITY_RUN = "catalog:quality_run";
  public static final String RESOLVED_FROM_LOAD_RUN = "catalog:load_run";

  public static final String MSG_NOT_CONFIGURED =
      "No quality history connection configured. Set QUALITY_HISTORY_DATABASE or publish"
          + " operations quality_run / load_run catalog definitions.";

  public static final String MSG_TABLES_MISSING =
      "No quality history tables (enable persistHistory on Measure Data Quality).";

  public static final String MSG_NO_HISTORY = "No history for this subject yet.";

  private DataQualityHistoryReader() {}

  /** Resolved ops database + schema for quality history reads. */
  public record HistoryConnection(
      String databaseMetaName, String schemaName, String resolvedFrom) {}

  /** One row from the subject history browse query. */
  public record SubjectHistoryEntry(
      String qualityRunId,
      Date capturedAt,
      String lifecycle,
      Long rowCount,
      Long findingCount,
      Long blockingCount,
      String loadId) {}

  /** One quality_run header (newest-first browse). */
  public record QualityRunSummary(
      String qualityRunId,
      Date measuredAt,
      String lifecycle,
      Long subjectCount,
      Long findingCount,
      Long blockingCount,
      Long warningCount,
      Boolean success,
      String workflowName) {}

  /** One finding row for a quality run (double-click drill-down). */
  public record FindingEntry(
      long findingSeq,
      String subjectKey,
      String ruleId,
      String ruleName,
      String ruleType,
      String severity,
      String fieldName,
      String message,
      String actualSummary,
      String expectedSummary) {}

  /**
   * Connection resolution order (design §6):
   *
   * <ol>
   *   <li>{@code QUALITY_HISTORY_DATABASE} / {@code QUALITY_HISTORY_SCHEMA} (schema default {@code
   *       dv_ops})
   *   <li>Catalog {@code hop/{projectKey}/operations/quality_run}
   *   <li>Catalog {@code hop/{projectKey}/operations/load_run}
   *   <li>Unresolved
   * </ol>
   *
   * @return connection when resolved; {@code null} when not configured
   */
  public static HistoryConnection resolveConnection(
      String catalogConnectionName, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    String fromVar =
        variableValue(variables, DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_DATABASE);
    if (!Utils.isEmpty(fromVar)) {
      String schema =
          variableValue(variables, DataQualityHistoryPublisher.VAR_QUALITY_HISTORY_SCHEMA);
      if (Utils.isEmpty(schema)) {
        schema = DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME;
      }
      return new HistoryConnection(fromVar.trim(), schema.trim(), RESOLVED_FROM_VARIABLE);
    }

    String opsNamespace = operationsNamespace(variables);
    HistoryConnection fromQualityRun =
        connectionFromCatalogDefinition(
            catalogConnectionName,
            new RecordDefinitionKey(opsNamespace, DataQualityHistoryPublisher.TABLE_QUALITY_RUN),
            RESOLVED_FROM_QUALITY_RUN,
            variables,
            metadataProvider);
    if (fromQualityRun != null) {
      return fromQualityRun;
    }

    return connectionFromCatalogDefinition(
        catalogConnectionName,
        new RecordDefinitionKey(opsNamespace, LoadRunTableName.LOAD_RUN),
        RESOLVED_FROM_LOAD_RUN,
        variables,
        metadataProvider);
  }

  /**
   * Ops namespace: {@code hop/{projectKey}/operations}. Project key matches {@link
   * DataQualityHistoryPublisher#operationsNamespace} / DvCatalogNamespaces.
   */
  public static String operationsNamespace(IVariables variables) {
    return DataQualityHistoryPublisher.operationsNamespace(variables);
  }

  /**
   * Project key: PROJECT_HOME basename when resolvable, else {@code "project"}. Same algorithm as
   * {@code DvCatalogNamespaces.resolveProjectKey}.
   */
  public static String resolveProjectKey(IVariables variables) {
    if (variables != null) {
      String projectHome = variables.resolve("${PROJECT_HOME}");
      if (!Utils.isEmpty(projectHome) && !projectHome.contains("${")) {
        Path path = Path.of(projectHome).getFileName();
        if (path != null) {
          String key = path.toString();
          if (!Utils.isEmpty(key)) {
            return key;
          }
        }
      }
    }
    return "project";
  }

  /**
   * Lists recent profile-subject history for {@code subjectKey}, newest first, up to {@code limit}.
   *
   * @throws HopException if connection/SQL fails
   * @throws QualityHistoryTablesMissingException if required tables are absent
   */
  public static List<SubjectHistoryEntry> listSubjectHistory(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String subjectKey,
      IVariables variables,
      int limit)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Quality history database meta is null");
    }
    if (Utils.isEmpty(subjectKey)) {
      return Collections.emptyList();
    }
    String schema =
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(operationsSchema, databaseMeta);
    int rowLimit = limit > 0 ? limit : DEFAULT_HISTORY_LIMIT;

    LoggingObject loggingObject = new LoggingObject(DataQualityHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!historyTablesExist(db, schema)) {
        throw new QualityHistoryTablesMissingException(MSG_TABLES_MISSING);
      }

      String subjectTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT);
      String runTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN);

      String sql =
          "SELECT s.quality_run_id, s.captured_at, s.lifecycle, s.row_count,"
              + " r.finding_count, r.blocking_count, r.load_id"
              + " FROM "
              + subjectTable
              + " s JOIN "
              + runTable
              + " r ON r.quality_run_id = s.quality_run_id"
              + " WHERE s.subject_key = "
              + DataQualityHistoryPublisher.sqlLiteral(subjectKey)
              + " ORDER BY s.captured_at DESC";

      db.setQueryLimit(rowLimit);
      List<Object[]> rows = db.getRows(sql, rowLimit);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return Collections.emptyList();
      }

      List<SubjectHistoryEntry> entries = new ArrayList<>(rows.size());
      for (Object[] row : rows) {
        entries.add(
            new SubjectHistoryEntry(
                stringValue(rowMeta, row, "quality_run_id"),
                dateValue(rowMeta, row, "captured_at"),
                stringValue(rowMeta, row, "lifecycle"),
                longValue(rowMeta, row, "row_count"),
                longValue(rowMeta, row, "finding_count"),
                longValue(rowMeta, row, "blocking_count"),
                stringValue(rowMeta, row, "load_id")));
      }
      return entries;
    } finally {
      db.disconnect();
    }
  }

  /**
   * Recent {@code quality_run} rows, newest first. Optional lifecycle filter ({@code PRE_UPDATE},
   * {@code POST_UPDATE}, {@code AD_HOC}); empty means all.
   */
  public static List<QualityRunSummary> listRecentRuns(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String lifecycle,
      IVariables variables,
      int limit)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Quality history database meta is null");
    }
    String schema =
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(operationsSchema, databaseMeta);
    int rowLimit = limit > 0 ? limit : DEFAULT_HISTORY_LIMIT;

    LoggingObject loggingObject = new LoggingObject(DataQualityHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!historyTablesExist(db, schema)) {
        throw new QualityHistoryTablesMissingException(MSG_TABLES_MISSING);
      }
      String runTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN);
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT quality_run_id, measured_at, lifecycle, subject_count, finding_count,")
          .append(" blocking_count, warning_count, success, workflow_name FROM ")
          .append(runTable);
      if (!Utils.isEmpty(lifecycle)) {
        sql.append(" WHERE lifecycle = ")
            .append(DataQualityHistoryPublisher.sqlLiteral(lifecycle.trim()));
      }
      sql.append(" ORDER BY measured_at DESC");
      db.setQueryLimit(rowLimit);
      List<Object[]> rows = db.getRows(sql.toString(), rowLimit);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return Collections.emptyList();
      }
      List<QualityRunSummary> entries = new ArrayList<>(rows.size());
      for (Object[] row : rows) {
        entries.add(
            new QualityRunSummary(
                stringValue(rowMeta, row, "quality_run_id"),
                dateValue(rowMeta, row, "measured_at"),
                stringValue(rowMeta, row, "lifecycle"),
                longValue(rowMeta, row, "subject_count"),
                longValue(rowMeta, row, "finding_count"),
                longValue(rowMeta, row, "blocking_count"),
                longValue(rowMeta, row, "warning_count"),
                booleanValue(rowMeta, row, "success"),
                stringValue(rowMeta, row, "workflow_name")));
      }
      return entries;
    } finally {
      db.disconnect();
    }
  }

  /**
   * Findings for one quality run, optionally filtered to a subject. Ordered by {@code finding_seq}.
   */
  public static List<FindingEntry> listFindings(
      DatabaseMeta databaseMeta,
      String operationsSchema,
      String qualityRunId,
      String subjectKey,
      IVariables variables)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Quality history database meta is null");
    }
    if (Utils.isEmpty(qualityRunId)) {
      return Collections.emptyList();
    }
    String schema =
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(operationsSchema, databaseMeta);

    LoggingObject loggingObject = new LoggingObject(DataQualityHistoryReader.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (!db.checkTableExists(schema, DataQualityHistoryPublisher.TABLE_QUALITY_FINDING)) {
        throw new QualityHistoryTablesMissingException(MSG_TABLES_MISSING);
      }

      String findingTable =
          databaseMeta.getQuotedSchemaTableCombination(
              db, schema, DataQualityHistoryPublisher.TABLE_QUALITY_FINDING);

      StringBuilder sql = new StringBuilder();
      sql.append(
              "SELECT finding_seq, subject_key, rule_id, rule_name, rule_type, severity, field_name,")
          .append(" message, actual_summary, expected_summary FROM ")
          .append(findingTable)
          .append(" WHERE quality_run_id = ")
          .append(DataQualityHistoryPublisher.sqlLiteral(qualityRunId));
      if (!Utils.isEmpty(subjectKey)) {
        sql.append(" AND subject_key = ")
            .append(DataQualityHistoryPublisher.sqlLiteral(subjectKey));
      }
      sql.append(" ORDER BY finding_seq");

      List<Object[]> rows = db.getRows(sql.toString(), 10_000);
      IRowMeta rowMeta = db.getReturnRowMeta();
      if (rows == null || rows.isEmpty() || rowMeta == null) {
        return Collections.emptyList();
      }

      List<FindingEntry> entries = new ArrayList<>(rows.size());
      for (Object[] row : rows) {
        Long seq = longValue(rowMeta, row, "finding_seq");
        entries.add(
            new FindingEntry(
                seq != null ? seq : 0L,
                stringValue(rowMeta, row, "subject_key"),
                stringValue(rowMeta, row, "rule_id"),
                stringValue(rowMeta, row, "rule_name"),
                stringValue(rowMeta, row, "rule_type"),
                stringValue(rowMeta, row, "severity"),
                stringValue(rowMeta, row, "field_name"),
                stringValue(rowMeta, row, "message"),
                stringValue(rowMeta, row, "actual_summary"),
                stringValue(rowMeta, row, "expected_summary")));
      }
      return entries;
    } finally {
      db.disconnect();
    }
  }

  public static boolean historyTablesExist(Database db, String operationsSchema)
      throws HopException {
    if (db == null) {
      return false;
    }
    String schema =
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(operationsSchema, db.getDatabaseMeta());
    return db.checkTableExists(schema, DataQualityHistoryPublisher.TABLE_QUALITY_RUN)
        && db.checkTableExists(schema, DataQualityHistoryPublisher.TABLE_QUALITY_PROFILE_SUBJECT);
  }

  public static DatabaseMeta loadDatabaseMeta(
      String databaseMetaName, IHopMetadataProvider metadataProvider) throws HopException {
    if (Utils.isEmpty(databaseMetaName) || metadataProvider == null) {
      return null;
    }
    return metadataProvider.getSerializer(DatabaseMeta.class).load(databaseMetaName.trim());
  }

  private static HistoryConnection connectionFromCatalogDefinition(
      String catalogConnectionName,
      RecordDefinitionKey key,
      String resolvedFrom,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName) || metadataProvider == null) {
      return null;
    }
    RecordDefinition definition =
        RecordDefinitionRegistry.getInstance()
            .read(catalogConnectionName, key, variables, metadataProvider);
    if (definition == null || definition.getPhysicalTable() == null) {
      return null;
    }
    PhysicalTableRef table = definition.getPhysicalTable();
    if (Utils.isEmpty(table.getDatabaseMetaName())) {
      return null;
    }
    String schema = table.getSchemaName();
    if (Utils.isEmpty(schema)) {
      schema = DataQualityHistoryPublisher.DEFAULT_SCHEMA_NAME;
    }
    return new HistoryConnection(table.getDatabaseMetaName().trim(), schema.trim(), resolvedFrom);
  }

  private static String variableValue(IVariables variables, String name) {
    if (variables == null || Utils.isEmpty(name)) {
      return null;
    }
    String value = variables.getVariable(name);
    if (Utils.isEmpty(value)) {
      // Some contexts only resolve via ${NAME}
      String resolved = variables.resolve("${" + name + "}");
      if (!Utils.isEmpty(resolved) && !resolved.contains("${")) {
        value = resolved;
      }
    }
    if (Utils.isEmpty(value) || value.contains("${")) {
      return null;
    }
    return value.trim();
  }

  private static int indexOf(IRowMeta rowMeta, String fieldName) {
    if (rowMeta == null || Utils.isEmpty(fieldName)) {
      return -1;
    }
    int index = rowMeta.indexOfValue(fieldName);
    if (index >= 0) {
      return index;
    }
    index = rowMeta.indexOfValue(fieldName.toUpperCase());
    if (index >= 0) {
      return index;
    }
    index = rowMeta.indexOfValue(fieldName.toLowerCase());
    if (index >= 0) {
      return index;
    }
    // Match ignoring case across all fields (H2/Postgres dialect quirks).
    for (int i = 0; i < rowMeta.size(); i++) {
      String name = rowMeta.getValueMeta(i).getName();
      if (name != null && name.equalsIgnoreCase(fieldName)) {
        return i;
      }
    }
    return -1;
  }

  private static String stringValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    try {
      int index = indexOf(rowMeta, fieldName);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value == null) {
        return null;
      }
      return rowMeta.getValueMeta(index).getString(value);
    } catch (Exception e) {
      return null;
    }
  }

  private static Long longValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    try {
      int index = indexOf(rowMeta, fieldName);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value == null) {
        return null;
      }
      if (value instanceof Number n) {
        return n.longValue();
      }
      return rowMeta.getValueMeta(index).getInteger(value);
    } catch (Exception e) {
      return null;
    }
  }

  private static Boolean booleanValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    try {
      int index = indexOf(rowMeta, fieldName);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value == null) {
        return null;
      }
      if (value instanceof Boolean b) {
        return b;
      }
      if (value instanceof Number n) {
        return n.longValue() != 0L;
      }
      String text = rowMeta.getValueMeta(index).getString(value);
      if (Utils.isEmpty(text)) {
        return null;
      }
      return "Y".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text);
    } catch (Exception e) {
      return null;
    }
  }

  private static Date dateValue(IRowMeta rowMeta, Object[] row, String fieldName) {
    try {
      int index = indexOf(rowMeta, fieldName);
      if (index < 0) {
        return null;
      }
      Object value = row[index];
      if (value == null) {
        return null;
      }
      if (value instanceof Date date) {
        return date;
      }
      if (value instanceof java.time.Instant instant) {
        return Date.from(instant);
      }
      if (value instanceof java.time.LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
      }
      if (value instanceof Number n) {
        return new Date(n.longValue());
      }
      Date fromMeta = rowMeta.getValueMeta(index).getDate(value);
      if (fromMeta != null) {
        return fromMeta;
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  /** Marker when quality_run / quality_profile_subject (or finding) tables are missing. */
  public static final class QualityHistoryTablesMissingException extends HopException {
    public QualityHistoryTablesMissingException(String message) {
      super(message);
    }
  }

  /** Avoid hard dependency on load-run metrics constants from datavault package. */
  private static final class LoadRunTableName {
    static final String LOAD_RUN = "load_run";
  }
}
