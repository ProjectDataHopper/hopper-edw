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
package org.apache.hop.quality.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.disposition.DispositionResult;
import org.apache.hop.quality.disposition.QualityDispositionMode;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityReport;
import org.apache.hop.quality.model.QualitySeverity;
import org.apache.hop.quality.profile.DataProfileSnapshot;
import org.apache.hop.quality.profile.FieldProfile;

/**
 * Persists data quality measure results to ops tables ({@code quality_run}, profile snapshots,
 * findings). Immutable: existing {@code quality_run_id} is skipped.
 */
public final class DataQualityHistoryPublisher {

  /**
   * Default history schema: empty = connection default database/schema. Set explicitly only when
   * isolation is desired.
   */
  public static final String DEFAULT_SCHEMA_NAME = "";

  /** Historical name; not applied implicitly. */
  public static final String LEGACY_OPS_SCHEMA_NAME = "dv_ops";

  public static final String TABLE_QUALITY_RUN = "quality_run";
  public static final String TABLE_QUALITY_FINDING = "quality_finding";
  public static final String TABLE_QUALITY_PROFILE_SUBJECT = "quality_profile_subject";
  public static final String TABLE_QUALITY_PROFILE_FIELD = "quality_profile_field";
  public static final String TABLE_QUALITY_ALERT = "quality_alert";

  public static final String VAR_QUALITY_HISTORY_DATABASE = "QUALITY_HISTORY_DATABASE";
  public static final String VAR_QUALITY_HISTORY_SCHEMA = "QUALITY_HISTORY_SCHEMA";

  private static final int TOP_VALUES_LIMIT = 10;
  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Package-visible test seam: when set, invoked after {@code quality_run} insert (before child
   * rows). Used to exercise partial-failure cleanup.
   */
  static Runnable afterQualityRunInsertedForTest;

  public enum PublishStatus {
    INSERTED,
    SKIPPED,
    FAILED
  }

  public record PublishResult(PublishStatus status, String message) {}

  public record PublishContext(
      String targetDatabaseName,
      String operationsSchema,
      String catalogConnectionName,
      boolean publishCatalogDefinitions,
      boolean publishDatabaseRows,
      boolean autoCreateTables) {}

  private DataQualityHistoryPublisher() {}

  public static PublishResult publish(
      ILogChannel log,
      DataQualityReport report,
      PublishContext context,
      String loadId,
      String workflowName,
      String workflowExecutionId,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (report == null) {
      return new PublishResult(PublishStatus.FAILED, "Data quality report is null");
    }
    if (context == null) {
      return new PublishResult(PublishStatus.FAILED, "Publish context is null");
    }
    if (Utils.isEmpty(context.targetDatabaseName())) {
      return new PublishResult(
          PublishStatus.FAILED, "Target database connection is required for quality history");
    }
    if (Utils.isEmpty(report.getRunId())) {
      return new PublishResult(PublishStatus.FAILED, "Report runId is required");
    }

    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(context.targetDatabaseName());
    if (databaseMeta == null) {
      return new PublishResult(
          PublishStatus.FAILED,
          "Target database connection not found: " + context.targetDatabaseName());
    }

    String operationsSchema =
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(
            resolveOperationsSchema(context), databaseMeta);
    String namespace = operationsNamespace(variables);
    Date updatedAt = new Date();

    if (context.publishCatalogDefinitions() && !Utils.isEmpty(context.catalogConnectionName())) {
      publishRecordDefinitions(
          context.catalogConnectionName(),
          namespace,
          context.targetDatabaseName(),
          operationsSchema,
          databaseMeta,
          variables,
          metadataProvider,
          updatedAt);
    }

    if (!context.publishDatabaseRows()) {
      return new PublishResult(PublishStatus.INSERTED, "Catalog definitions published only");
    }

    return insertRunRows(
        log,
        databaseMeta,
        operationsSchema,
        context.autoCreateTables(),
        variables,
        report,
        loadId,
        workflowName,
        workflowExecutionId);
  }

  static String resolveOperationsSchema(PublishContext context) {
    if (context == null || Utils.isEmpty(context.operationsSchema())) {
      return DEFAULT_SCHEMA_NAME;
    }
    return context.operationsSchema().trim();
  }

  /** Blank → connection default; explicit values kept as-is. */
  public static String resolvePhysicalOperationsSchema(String operationsSchema) {
    if (Utils.isEmpty(operationsSchema)) {
      return DEFAULT_SCHEMA_NAME;
    }
    return operationsSchema.trim();
  }

  /**
   * Ops namespace: {@code hop/{projectKey}/operations}. Project key matches {@code
   * DvCatalogNamespaces.resolveProjectKey} (PROJECT_HOME basename, else {@code "project"}).
   */
  public static String operationsNamespace(IVariables variables) {
    return "hop/" + resolveProjectKey(variables) + "/operations";
  }

  static String resolveProjectKey(IVariables variables) {
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

  private static void publishRecordDefinitions(
      String catalogConnectionName,
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt)
      throws HopException {
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildQualityRunDefinition(namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildProfileSubjectDefinition(
            namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildProfileFieldDefinition(namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildFindingDefinition(namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildAlertDefinition(namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
  }

  private static void upsertDefinition(
      RecordDefinitionRegistry registry,
      String catalogConnectionName,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt)
      throws HopException {
    definition.validate();
    RecordDefinition existing =
        registry.read(catalogConnectionName, definition.getKey(), variables, metadataProvider);
    if (existing != null
        && existing.getOrigin() != null
        && definition.getOrigin() != null
        && existing.getOrigin().getCreatedAt() != null) {
      definition.getOrigin().setCreatedAt(existing.getOrigin().getCreatedAt());
    } else if (definition.getOrigin() != null) {
      definition.getOrigin().setCreatedAt(updatedAt);
    }
    registry.upsert(catalogConnectionName, definition, variables, metadataProvider);
  }

  static RecordDefinition buildQualityRunDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta) throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("quality_run_id", 64));
    fields.addValueMeta(new ValueMetaDate("measured_at"));
    fields.addValueMeta(stringMeta("lifecycle", 16));
    fields.addValueMeta(stringMeta("evaluation_mode", 32));
    fields.addValueMeta(stringMeta("load_id", 64));
    fields.addValueMeta(stringMeta("workflow_name", 255));
    fields.addValueMeta(stringMeta("workflow_execution_id", 64));
    fields.addValueMeta(new ValueMetaInteger("subject_count"));
    fields.addValueMeta(new ValueMetaInteger("finding_count"));
    fields.addValueMeta(new ValueMetaInteger("blocking_count"));
    fields.addValueMeta(new ValueMetaInteger("warning_count"));
    fields.addValueMeta(new ValueMetaInteger("info_count"));
    fields.addValueMeta(new ValueMetaInteger("infra_error_count"));
    fields.addValueMeta(new ValueMetaBoolean("success"));
    fields.addValueMeta(stringMeta("subjects_json", 4000));
    fields.addValueMeta(stringMeta("infra_errors_json", 4000));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_QUALITY_RUN));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Data quality measure run header and aggregate counters");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_QUALITY_RUN));
    org.apache.hop.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("data-quality");
    return definition;
  }

  static RecordDefinition buildProfileSubjectDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta) throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("quality_run_id", 64));
    fields.addValueMeta(stringMeta("subject_key", 512));
    fields.addValueMeta(new ValueMetaInteger("row_count"));
    fields.addValueMeta(new ValueMetaBoolean("row_count_exact"));
    fields.addValueMeta(stringMeta("evaluation_mode", 32));
    fields.addValueMeta(stringMeta("lifecycle", 16));
    fields.addValueMeta(new ValueMetaDate("captured_at"));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_QUALITY_PROFILE_SUBJECT));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Per-subject profile snapshot for one quality run");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_QUALITY_PROFILE_SUBJECT));
    org.apache.hop.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("data-quality");
    return definition;
  }

  static RecordDefinition buildProfileFieldDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta) throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("quality_run_id", 64));
    fields.addValueMeta(stringMeta("subject_key", 512));
    fields.addValueMeta(stringMeta("field_name", 255));
    fields.addValueMeta(new ValueMetaInteger("null_count"));
    fields.addValueMeta(new ValueMetaInteger("empty_string_count"));
    fields.addValueMeta(new ValueMetaInteger("non_null_count"));
    fields.addValueMeta(new ValueMetaInteger("exact_distinct_count"));
    fields.addValueMeta(new ValueMetaBoolean("distinct_truncated"));
    fields.addValueMeta(stringMeta("min_value", 500));
    fields.addValueMeta(stringMeta("max_value", 500));
    fields.addValueMeta(new ValueMetaInteger("min_string_length"));
    fields.addValueMeta(new ValueMetaInteger("max_string_length"));
    fields.addValueMeta(stringMeta("top_values_json", 4000));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_QUALITY_PROFILE_FIELD));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Per-field profile metrics for one quality run subject");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_QUALITY_PROFILE_FIELD));
    org.apache.hop.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("data-quality");
    return definition;
  }

  static RecordDefinition buildFindingDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta) throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("quality_run_id", 64));
    fields.addValueMeta(new ValueMetaInteger("finding_seq"));
    fields.addValueMeta(stringMeta("subject_key", 512));
    fields.addValueMeta(stringMeta("rule_id", 128));
    fields.addValueMeta(stringMeta("rule_name", 255));
    fields.addValueMeta(stringMeta("rule_type", 64));
    fields.addValueMeta(stringMeta("severity", 16));
    fields.addValueMeta(stringMeta("field_name", 255));
    fields.addValueMeta(stringMeta("message", 2000));
    fields.addValueMeta(stringMeta("actual_summary", 1000));
    fields.addValueMeta(stringMeta("expected_summary", 1000));
    fields.addValueMeta(stringMeta("metrics_json", 4000));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_QUALITY_FINDING));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Rule findings for one quality measure run");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_QUALITY_FINDING));
    org.apache.hop.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("data-quality");
    return definition;
  }

  static RecordDefinition buildAlertDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta) throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("quality_run_id", 64));
    fields.addValueMeta(new ValueMetaDate("alerted_at"));
    fields.addValueMeta(stringMeta("disposition_mode", 32));
    fields.addValueMeta(new ValueMetaBoolean("disposition_failed"));
    fields.addValueMeta(stringMeta("summary", 2000));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_QUALITY_ALERT));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Gate disposition alert header for a quality run");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_QUALITY_ALERT));
    org.apache.hop.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("data-quality");
    return definition;
  }

  private static PhysicalTableRef physicalTableRef(
      String targetDatabaseName, String operationsSchema, String tableName) {
    PhysicalTableRef ref = new PhysicalTableRef();
    ref.setDatabaseMetaName(targetDatabaseName);
    ref.setSchemaName(operationsSchema);
    ref.setTableName(tableName);
    return ref;
  }

  private static ValueMetaString stringMeta(String name, int length) {
    ValueMetaString meta = new ValueMetaString(name);
    meta.setLength(length);
    return meta;
  }

  private static PublishResult insertRunRows(
      ILogChannel log,
      DatabaseMeta databaseMeta,
      String operationsSchema,
      boolean autoCreateTables,
      IVariables variables,
      DataQualityReport report,
      String loadId,
      String workflowName,
      String workflowExecutionId) {
    LoggingObject loggingObject = new LoggingObject(DataQualityHistoryPublisher.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    String runId = report.getRunId();
    boolean wroteAny = false;
    try {
      db.connect();
      if (autoCreateTables) {
        DataQualityHistoryDdlSupport.ensureTables(db, databaseMeta, operationsSchema, log);
      }

      if (qualityRunExists(db, operationsSchema, runId)) {
        String msg = "Quality run already exists (immutable skip): " + runId;
        if (log != null) {
          log.logBasic(msg);
        }
        return new PublishResult(PublishStatus.SKIPPED, msg);
      }

      insertQualityRun(db, operationsSchema, report, loadId, workflowName, workflowExecutionId);
      wroteAny = true;
      if (afterQualityRunInsertedForTest != null) {
        afterQualityRunInsertedForTest.run();
      }

      for (Map.Entry<String, DataProfileSnapshot> entry :
          report.getProfilesBySubject().entrySet()) {
        insertProfileSubject(db, operationsSchema, runId, entry.getKey(), entry.getValue());
        insertProfileFields(db, operationsSchema, runId, entry.getKey(), entry.getValue());
      }

      long seq = 0L;
      for (DataQualityFinding finding : report.getFindings()) {
        if (finding == null) {
          continue;
        }
        insertFinding(db, operationsSchema, runId, seq++, finding);
      }

      String msg =
          "Published quality history to "
              + (Utils.isEmpty(operationsSchema) ? "connection default database" : operationsSchema)
              + " for run "
              + runId
              + " ("
              + report.getSubjectKeys().size()
              + " subjects, "
              + report.getFindingCount()
              + " findings)";
      if (log != null) {
        log.logBasic(msg);
      }
      return new PublishResult(PublishStatus.INSERTED, msg);
    } catch (Exception e) {
      if (wroteAny) {
        bestEffortDeleteRun(db, operationsSchema, runId, log);
      }
      String msg = "Unable to publish quality history: " + e.getMessage();
      if (log != null) {
        log.logError(msg, e);
      }
      return new PublishResult(PublishStatus.FAILED, msg);
    } finally {
      db.disconnect();
    }
  }

  static boolean qualityRunExists(Database db, String operationsSchema, String runId)
      throws HopException {
    String qualified =
        db.getDatabaseMeta()
            .getQuotedSchemaTableCombination(db, operationsSchema, TABLE_QUALITY_RUN);
    String sql = "SELECT 1 FROM " + qualified + " WHERE quality_run_id = " + sqlLiteral(runId);
    RowMetaAndData row = db.getOneRow(sql);
    return row != null && row.getData() != null && row.getData().length > 0;
  }

  /**
   * Insert a {@code quality_alert} disposition header for a quality run. Skips when an alert row
   * already exists for the run (immutable). Ensures tables when {@code autoCreateTables} is true.
   */
  public static PublishResult insertQualityAlert(
      ILogChannel log,
      DataQualityReport report,
      DispositionResult disposition,
      QualityDispositionMode mode,
      PublishContext context,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (report == null) {
      return new PublishResult(PublishStatus.FAILED, "Data quality report is null");
    }
    if (context == null) {
      return new PublishResult(PublishStatus.FAILED, "Publish context is null");
    }
    if (Utils.isEmpty(context.targetDatabaseName())) {
      return new PublishResult(
          PublishStatus.FAILED, "Target database connection is required for quality_alert");
    }
    if (Utils.isEmpty(report.getRunId())) {
      return new PublishResult(PublishStatus.FAILED, "Report runId is required");
    }

    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(context.targetDatabaseName());
    if (databaseMeta == null) {
      return new PublishResult(
          PublishStatus.FAILED,
          "Target database connection not found: " + context.targetDatabaseName());
    }

    String operationsSchema =
        DataQualityHistoryDdlSupport.resolvePhysicalSchema(
            resolveOperationsSchema(context), databaseMeta);
    LoggingObject loggingObject = new LoggingObject(DataQualityHistoryPublisher.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    String runId = report.getRunId();
    try {
      db.connect();
      if (context.autoCreateTables()) {
        DataQualityHistoryDdlSupport.ensureTables(db, databaseMeta, operationsSchema, log);
      }

      if (qualityAlertExists(db, operationsSchema, runId)) {
        String msg = "Quality alert already exists (immutable skip): " + runId;
        if (log != null) {
          log.logBasic(msg);
        }
        return new PublishResult(PublishStatus.SKIPPED, msg);
      }

      insertQualityAlertRow(db, operationsSchema, report, disposition, mode);

      String msg =
          "Inserted quality_alert for run "
              + runId
              + " mode="
              + (mode != null ? mode.name() : "")
              + " failed="
              + (disposition != null && disposition.isFailed());
      if (log != null) {
        log.logBasic(msg);
      }
      return new PublishResult(PublishStatus.INSERTED, msg);
    } catch (Exception e) {
      String msg = "Unable to insert quality_alert: " + e.getMessage();
      if (log != null) {
        log.logError(msg, e);
      }
      return new PublishResult(PublishStatus.FAILED, msg);
    } finally {
      db.disconnect();
    }
  }

  static boolean qualityAlertExists(Database db, String operationsSchema, String runId)
      throws HopException {
    String qualified =
        db.getDatabaseMeta()
            .getQuotedSchemaTableCombination(db, operationsSchema, TABLE_QUALITY_ALERT);
    String sql = "SELECT 1 FROM " + qualified + " WHERE quality_run_id = " + sqlLiteral(runId);
    RowMetaAndData row = db.getOneRow(sql);
    return row != null && row.getData() != null && row.getData().length > 0;
  }

  private static void insertQualityAlertRow(
      Database db,
      String operationsSchema,
      DataQualityReport report,
      DispositionResult disposition,
      QualityDispositionMode mode)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("quality_run_id", 64));
    layout.addValueMeta(new ValueMetaDate("alerted_at"));
    layout.addValueMeta(stringMeta("disposition_mode", 32));
    layout.addValueMeta(new ValueMetaBoolean("disposition_failed"));
    layout.addValueMeta(stringMeta("summary", 2000));

    String summary =
        disposition != null && disposition.getSummary() != null ? disposition.getSummary() : "";
    Object[] row =
        new Object[] {
          report.getRunId(),
          new Date(),
          mode != null ? mode.name() : null,
          disposition != null && disposition.isFailed(),
          truncate(summary, 2000)
        };
    db.insertRow(operationsSchema, TABLE_QUALITY_ALERT, layout, row);
  }

  private static void bestEffortDeleteRun(
      Database db, String operationsSchema, String runId, ILogChannel log) {
    if (db == null || Utils.isEmpty(runId)) {
      return;
    }
    String[] tables = {
      TABLE_QUALITY_FINDING,
      TABLE_QUALITY_PROFILE_FIELD,
      TABLE_QUALITY_PROFILE_SUBJECT,
      TABLE_QUALITY_RUN
    };
    for (String table : tables) {
      try {
        String qualified =
            db.getDatabaseMeta().getQuotedSchemaTableCombination(db, operationsSchema, table);
        db.execStatement(
            "DELETE FROM " + qualified + " WHERE quality_run_id = " + sqlLiteral(runId));
      } catch (Exception e) {
        if (log != null) {
          log.logError(
              "Best-effort cleanup failed for "
                  + operationsSchema
                  + "."
                  + table
                  + ": "
                  + e.getMessage());
        }
      }
    }
  }

  private static void insertQualityRun(
      Database db,
      String operationsSchema,
      DataQualityReport report,
      String loadId,
      String workflowName,
      String workflowExecutionId)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("quality_run_id", 64));
    layout.addValueMeta(new ValueMetaDate("measured_at"));
    layout.addValueMeta(stringMeta("lifecycle", 16));
    layout.addValueMeta(stringMeta("evaluation_mode", 32));
    layout.addValueMeta(stringMeta("load_id", 64));
    layout.addValueMeta(stringMeta("workflow_name", 255));
    layout.addValueMeta(stringMeta("workflow_execution_id", 64));
    layout.addValueMeta(new ValueMetaInteger("subject_count"));
    layout.addValueMeta(new ValueMetaInteger("finding_count"));
    layout.addValueMeta(new ValueMetaInteger("blocking_count"));
    layout.addValueMeta(new ValueMetaInteger("warning_count"));
    layout.addValueMeta(new ValueMetaInteger("info_count"));
    layout.addValueMeta(new ValueMetaInteger("infra_error_count"));
    layout.addValueMeta(new ValueMetaBoolean("success"));
    layout.addValueMeta(stringMeta("subjects_json", 4000));
    layout.addValueMeta(stringMeta("infra_errors_json", 4000));

    Date measuredAt =
        report.getMeasuredAt() != null ? Date.from(report.getMeasuredAt()) : new Date();
    Object[] row =
        new Object[] {
          report.getRunId(),
          measuredAt,
          report.getLifecycle() != null ? report.getLifecycle().name() : null,
          resolvePrimaryEvaluationMode(report),
          truncate(loadId, 64),
          truncate(workflowName, 255),
          truncate(workflowExecutionId, 64),
          (long) report.getSubjectKeys().size(),
          (long) report.getFindingCount(),
          report.countBySeverity(QualitySeverity.BLOCKING),
          report.countBySeverity(QualitySeverity.WARNING),
          report.countBySeverity(QualitySeverity.INFO),
          (long) report.getInfraErrors().size(),
          !report.hasInfraErrors(),
          truncate(toJsonArray(report.getSubjectKeys()), 4000),
          report.getInfraErrors().isEmpty()
              ? null
              : truncate(toJsonArray(report.getInfraErrors()), 4000)
        };
    db.insertRow(operationsSchema, TABLE_QUALITY_RUN, layout, row);
  }

  private static void insertProfileSubject(
      Database db,
      String operationsSchema,
      String runId,
      String subjectKey,
      DataProfileSnapshot profile)
      throws HopException {
    if (profile == null) {
      return;
    }
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("quality_run_id", 64));
    layout.addValueMeta(stringMeta("subject_key", 512));
    layout.addValueMeta(new ValueMetaInteger("row_count"));
    layout.addValueMeta(new ValueMetaBoolean("row_count_exact"));
    layout.addValueMeta(stringMeta("evaluation_mode", 32));
    layout.addValueMeta(stringMeta("lifecycle", 16));
    layout.addValueMeta(new ValueMetaDate("captured_at"));

    Date capturedAt =
        profile.getCapturedAt() != null ? Date.from(profile.getCapturedAt()) : new Date();
    Object[] row =
        new Object[] {
          runId,
          truncate(subjectKey, 512),
          profile.getRowCount(),
          profile.isRowCountExact(),
          profile.getEvaluationMode() != null ? profile.getEvaluationMode().name() : null,
          profile.getLifecycle() != null ? profile.getLifecycle().name() : null,
          capturedAt
        };
    db.insertRow(operationsSchema, TABLE_QUALITY_PROFILE_SUBJECT, layout, row);
  }

  private static void insertProfileFields(
      Database db,
      String operationsSchema,
      String runId,
      String subjectKey,
      DataProfileSnapshot profile)
      throws HopException {
    if (profile == null || profile.getFields() == null || profile.getFields().isEmpty()) {
      return;
    }
    for (FieldProfile field : profile.getFields().values()) {
      if (field == null || Utils.isEmpty(field.getFieldName())) {
        continue;
      }
      insertProfileField(db, operationsSchema, runId, subjectKey, field);
    }
  }

  private static void insertProfileField(
      Database db, String operationsSchema, String runId, String subjectKey, FieldProfile field)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("quality_run_id", 64));
    layout.addValueMeta(stringMeta("subject_key", 512));
    layout.addValueMeta(stringMeta("field_name", 255));
    layout.addValueMeta(new ValueMetaInteger("null_count"));
    layout.addValueMeta(new ValueMetaInteger("empty_string_count"));
    layout.addValueMeta(new ValueMetaInteger("non_null_count"));
    layout.addValueMeta(new ValueMetaInteger("exact_distinct_count"));
    layout.addValueMeta(new ValueMetaBoolean("distinct_truncated"));
    layout.addValueMeta(stringMeta("min_value", 500));
    layout.addValueMeta(stringMeta("max_value", 500));
    layout.addValueMeta(new ValueMetaInteger("min_string_length"));
    layout.addValueMeta(new ValueMetaInteger("max_string_length"));
    layout.addValueMeta(stringMeta("top_values_json", 4000));

    Long exactDistinct = field.getExactDistinctCount();
    Integer minLen = field.getMinStringLength();
    Integer maxLen = field.getMaxStringLength();
    Object[] row =
        new Object[] {
          runId,
          truncate(subjectKey, 512),
          truncate(field.getFieldName(), 255),
          field.getNullCount(),
          field.getEmptyStringCount(),
          field.getNonNullCount(),
          exactDistinct,
          field.isDistinctTruncated(),
          stringify(field.getMinValue(), 500),
          stringify(field.getMaxValue(), 500),
          minLen != null ? minLen.longValue() : null,
          maxLen != null ? maxLen.longValue() : null,
          truncate(topValuesJson(field.getValueCounts()), 4000)
        };
    db.insertRow(operationsSchema, TABLE_QUALITY_PROFILE_FIELD, layout, row);
  }

  private static void insertFinding(
      Database db,
      String operationsSchema,
      String runId,
      long findingSeq,
      DataQualityFinding finding)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("quality_run_id", 64));
    layout.addValueMeta(new ValueMetaInteger("finding_seq"));
    layout.addValueMeta(stringMeta("subject_key", 512));
    layout.addValueMeta(stringMeta("rule_id", 128));
    layout.addValueMeta(stringMeta("rule_name", 255));
    layout.addValueMeta(stringMeta("rule_type", 64));
    layout.addValueMeta(stringMeta("severity", 16));
    layout.addValueMeta(stringMeta("field_name", 255));
    layout.addValueMeta(stringMeta("message", 2000));
    layout.addValueMeta(stringMeta("actual_summary", 1000));
    layout.addValueMeta(stringMeta("expected_summary", 1000));
    layout.addValueMeta(stringMeta("metrics_json", 4000));

    Object[] row =
        new Object[] {
          runId,
          findingSeq,
          truncate(finding.getSubjectKey(), 512),
          truncate(finding.getRuleId(), 128),
          truncate(finding.getRuleName(), 255),
          finding.getType() != null ? finding.getType().name() : null,
          finding.getSeverity() != null ? finding.getSeverity().name() : null,
          truncate(finding.getFieldName(), 255),
          truncate(finding.getMessage(), 2000),
          truncate(finding.getActualSummary(), 1000),
          truncate(finding.getExpectedSummary(), 1000),
          truncate(mapToJson(finding.getMetrics()), 4000)
        };
    db.insertRow(operationsSchema, TABLE_QUALITY_FINDING, layout, row);
  }

  private static String resolvePrimaryEvaluationMode(DataQualityReport report) {
    for (DataProfileSnapshot profile : report.getProfilesBySubject().values()) {
      if (profile != null && profile.getEvaluationMode() != null) {
        return profile.getEvaluationMode().name();
      }
    }
    return null;
  }

  static String sqlLiteral(String value) {
    if (value == null) {
      return "NULL";
    }
    return "'" + value.replace("'", "''") + "'";
  }

  static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    if (value.length() <= max) {
      return value;
    }
    return value.substring(0, max);
  }

  static String stringify(Object value, int max) {
    if (value == null) {
      return null;
    }
    return truncate(String.valueOf(value), max);
  }

  static String toJsonArray(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "[]";
    }
    try {
      return JSON.writeValueAsString(values);
    } catch (Exception e) {
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < values.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append('"').append(escapeJson(values.get(i))).append('"');
      }
      sb.append(']');
      return sb.toString();
    }
  }

  static String mapToJson(Map<String, String> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    try {
      return JSON.writeValueAsString(map);
    } catch (Exception e) {
      return null;
    }
  }

  static String topValuesJson(Map<String, Long> valueCounts) {
    if (valueCounts == null || valueCounts.isEmpty()) {
      return null;
    }
    List<Map.Entry<String, Long>> entries = new ArrayList<>(valueCounts.entrySet());
    entries.sort(
        (a, b) -> {
          int cmp =
              Long.compare(
                  b.getValue() != null ? b.getValue() : 0L,
                  a.getValue() != null ? a.getValue() : 0L);
          if (cmp != 0) {
            return cmp;
          }
          return String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey()));
        });
    Map<String, Long> top = new LinkedHashMap<>();
    int n = Math.min(TOP_VALUES_LIMIT, entries.size());
    for (int i = 0; i < n; i++) {
      Map.Entry<String, Long> e = entries.get(i);
      top.put(e.getKey(), e.getValue());
    }
    try {
      return JSON.writeValueAsString(top);
    } catch (Exception e) {
      return null;
    }
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
