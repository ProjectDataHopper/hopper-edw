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
package org.hopper.edw.catalog.harvest.history;

import java.nio.file.Path;
import java.util.Date;
import org.apache.hop.core.Const;
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
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestChange;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestSubjectResult;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestedField;
import org.hopper.edw.catalog.harvest.SchemaHarvestModels.HarvestedForeignKey;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;

/**
 * Persists schema harvest results to OPS tables. Immutable: existing {@code harvest_run_id} is
 * skipped.
 */
public final class SchemaHarvestHistoryPublisher {

  public static final String DEFAULT_SCHEMA_NAME = "";

  public static final String TABLE_HARVEST_RUN = "schema_harvest_run";
  public static final String TABLE_HARVEST_SUBJECT = "schema_harvest_subject";
  public static final String TABLE_HARVEST_FIELD = "schema_harvest_field";
  public static final String TABLE_HARVEST_CHANGE = "schema_harvest_change";
  public static final String TABLE_HARVEST_FK = "schema_harvest_fk";

  public static final String VAR_SCHEMA_HARVEST_DATABASE = "SCHEMA_HARVEST_DATABASE";
  public static final String VAR_SCHEMA_HARVEST_SCHEMA = "SCHEMA_HARVEST_SCHEMA";

  /** Workflow variable set by Harvest source metadata for the schema gate. */
  public static final String VAR_SCHEMA_HARVEST_RUN_ID = "DV_SCHEMA_HARVEST_RUN_ID";

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

  private SchemaHarvestHistoryPublisher() {}

  public static PublishResult publish(
      ILogChannel log,
      HarvestResult result,
      PublishContext context,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (result == null) {
      return new PublishResult(PublishStatus.FAILED, "Harvest result is null");
    }
    if (context == null) {
      return new PublishResult(PublishStatus.FAILED, "Publish context is null");
    }
    if (Utils.isEmpty(context.targetDatabaseName())) {
      return new PublishResult(
          PublishStatus.FAILED, "Target database connection is required for harvest history");
    }
    if (Utils.isEmpty(result.getHarvestRunId())) {
      return new PublishResult(PublishStatus.FAILED, "Harvest run id is required");
    }

    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(context.targetDatabaseName());
    if (databaseMeta == null) {
      return new PublishResult(
          PublishStatus.FAILED,
          "Target database connection not found: " + context.targetDatabaseName());
    }

    String operationsSchema =
        SchemaHarvestHistoryDdlSupport.resolveSchema(
            Utils.isEmpty(context.operationsSchema())
                ? DEFAULT_SCHEMA_NAME
                : context.operationsSchema());
    String namespace = operationsNamespace(variables);
    Date updatedAt = new Date();

    if (context.publishCatalogDefinitions() && !Utils.isEmpty(context.catalogConnectionName())) {
      publishRecordDefinitions(
          context.catalogConnectionName(),
          namespace,
          context.targetDatabaseName(),
          operationsSchema,
          variables,
          metadataProvider,
          updatedAt);
    }

    if (!context.publishDatabaseRows()) {
      return new PublishResult(PublishStatus.INSERTED, "Catalog definitions published only");
    }

    return insertRunRows(
        log, databaseMeta, operationsSchema, context.autoCreateTables(), variables, result);
  }

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
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt)
      throws HopException {
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildRunDefinition(namespace, targetDatabaseName, operationsSchema),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildSubjectDefinition(namespace, targetDatabaseName, operationsSchema),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildFieldDefinition(namespace, targetDatabaseName, operationsSchema),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildChangeDefinition(namespace, targetDatabaseName, operationsSchema),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildFkDefinition(namespace, targetDatabaseName, operationsSchema),
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

  static RecordDefinition buildRunDefinition(
      String namespace, String targetDatabaseName, String operationsSchema)
      throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("harvest_run_id", 64));
    fields.addValueMeta(new ValueMetaDate("started_at"));
    fields.addValueMeta(new ValueMetaDate("finished_at"));
    fields.addValueMeta(stringMeta("resource_group_name", 255));
    fields.addValueMeta(stringMeta("catalog_connection", 255));
    fields.addValueMeta(stringMeta("expected_baseline", 128));
    fields.addValueMeta(stringMeta("status", 16));
    fields.addValueMeta(new ValueMetaInteger("subject_count"));
    fields.addValueMeta(new ValueMetaInteger("change_count"));
    fields.addValueMeta(new ValueMetaInteger("error_count"));
    fields.addValueMeta(new ValueMetaInteger("subjects_with_changes"));
    fields.addValueMeta(stringMeta("workflow_name", 255));
    fields.addValueMeta(stringMeta("workflow_execution_id", 64));
    fields.addValueMeta(stringMeta("scope_summary", 2000));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_HARVEST_RUN));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Schema metadata harvest run header");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_HARVEST_RUN));
    org.hopper.edw.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(
        definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("schema-harvest");
    return definition;
  }

  static RecordDefinition buildSubjectDefinition(
      String namespace, String targetDatabaseName, String operationsSchema)
      throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("harvest_run_id", 64));
    fields.addValueMeta(stringMeta("subject_key", 512));
    fields.addValueMeta(stringMeta("catalog_connection", 255));
    fields.addValueMeta(stringMeta("source_type", 32));
    fields.addValueMeta(stringMeta("database_meta_name", 255));
    fields.addValueMeta(stringMeta("schema_name", 255));
    fields.addValueMeta(stringMeta("table_name", 255));
    fields.addValueMeta(stringMeta("discovery_status", 32));
    fields.addValueMeta(new ValueMetaBoolean("in_sync"));
    fields.addValueMeta(new ValueMetaInteger("change_count"));
    fields.addValueMeta(stringMeta("message", 4000));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_HARVEST_SUBJECT));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Per-source subject result for one schema harvest run");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_HARVEST_SUBJECT));
    org.hopper.edw.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(
        definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("schema-harvest");
    return definition;
  }

  static RecordDefinition buildFieldDefinition(
      String namespace, String targetDatabaseName, String operationsSchema)
      throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("harvest_run_id", 64));
    fields.addValueMeta(stringMeta("subject_key", 512));
    fields.addValueMeta(stringMeta("field_role", 16));
    fields.addValueMeta(stringMeta("field_name", 255));
    fields.addValueMeta(stringMeta("hop_type", 64));
    fields.addValueMeta(stringMeta("field_length", 64));
    fields.addValueMeta(stringMeta("field_precision", 64));
    fields.addValueMeta(new ValueMetaInteger("primary_key_position"));
    fields.addValueMeta(stringMeta("source_data_type", 128));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_HARVEST_FIELD));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Expected and discovered field snapshots for a harvest subject");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_HARVEST_FIELD));
    org.hopper.edw.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(
        definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("schema-harvest");
    return definition;
  }

  static RecordDefinition buildChangeDefinition(
      String namespace, String targetDatabaseName, String operationsSchema)
      throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("harvest_run_id", 64));
    fields.addValueMeta(new ValueMetaInteger("change_seq"));
    fields.addValueMeta(stringMeta("subject_key", 512));
    fields.addValueMeta(stringMeta("change_kind", 64));
    fields.addValueMeta(stringMeta("field_name", 255));
    fields.addValueMeta(stringMeta("expected_detail", 2000));
    fields.addValueMeta(stringMeta("actual_detail", 2000));
    fields.addValueMeta(stringMeta("severity", 16));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_HARVEST_CHANGE));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Schema drift change events for a harvest run");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_HARVEST_CHANGE));
    org.hopper.edw.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(
        definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("schema-harvest");
    return definition;
  }

  static RecordDefinition buildFkDefinition(
      String namespace, String targetDatabaseName, String operationsSchema)
      throws org.apache.hop.core.exception.HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("harvest_run_id", 64));
    fields.addValueMeta(stringMeta("subject_key", 512));
    fields.addValueMeta(stringMeta("field_role", 16));
    fields.addValueMeta(stringMeta("constraint_name", 255));
    fields.addValueMeta(stringMeta("child_schema", 255));
    fields.addValueMeta(stringMeta("child_table", 255));
    fields.addValueMeta(stringMeta("child_columns", 2000));
    fields.addValueMeta(stringMeta("parent_schema", 255));
    fields.addValueMeta(stringMeta("parent_table", 255));
    fields.addValueMeta(stringMeta("parent_columns", 2000));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_HARVEST_FK));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription(
        "Expected and discovered foreign-key snapshots for a harvest subject");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, TABLE_HARVEST_FK));
    org.hopper.edw.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(
        definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("schema-harvest");
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
      HarvestResult result) {
    LoggingObject loggingObject = new LoggingObject(SchemaHarvestHistoryPublisher.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    String runId = result.getHarvestRunId();
    boolean wroteAny = false;
    try {
      db.connect();
      if (autoCreateTables) {
        SchemaHarvestHistoryDdlSupport.ensureTables(db, databaseMeta, operationsSchema, log);
      }

      if (harvestRunExists(db, operationsSchema, runId)) {
        String msg = "Harvest run already exists (immutable skip): " + runId;
        if (log != null) {
          log.logBasic(msg);
        }
        return new PublishResult(PublishStatus.SKIPPED, msg);
      }

      insertHarvestRun(db, operationsSchema, result);
      wroteAny = true;

      long changeSeq = 0L;
      for (HarvestSubjectResult subject : result.subjectsView()) {
        if (subject == null) {
          continue;
        }
        insertSubject(db, operationsSchema, runId, subject);
        if (subject.getFields() != null) {
          for (HarvestedField field : subject.getFields()) {
            if (field != null) {
              insertField(db, operationsSchema, runId, subject.getSubjectKey(), field);
            }
          }
        }
        if (subject.getForeignKeys() != null) {
          for (HarvestedForeignKey fk : subject.getForeignKeys()) {
            if (fk != null) {
              insertForeignKey(db, operationsSchema, runId, subject.getSubjectKey(), fk);
            }
          }
        }
        if (subject.getChanges() != null) {
          for (HarvestChange change : subject.getChanges()) {
            if (change != null) {
              insertChange(
                  db, operationsSchema, runId, changeSeq++, subject.getSubjectKey(), change);
            }
          }
        }
      }

      String msg =
          "Published schema harvest history to "
              + (Utils.isEmpty(operationsSchema) ? "connection default database" : operationsSchema)
              + " for run "
              + runId
              + " ("
              + result.subjectCount()
              + " subjects, "
              + result.changeCount()
              + " changes)";
      if (log != null) {
        log.logBasic(msg);
      }
      return new PublishResult(PublishStatus.INSERTED, msg);
    } catch (Exception e) {
      if (wroteAny) {
        bestEffortDeleteRun(db, operationsSchema, runId, log);
      }
      String msg = "Unable to publish schema harvest history: " + e.getMessage();
      if (log != null) {
        log.logError(msg, e);
      }
      return new PublishResult(PublishStatus.FAILED, msg);
    } finally {
      db.disconnect();
    }
  }

  static boolean harvestRunExists(Database db, String operationsSchema, String runId)
      throws HopException {
    String qualified =
        db.getDatabaseMeta()
            .getQuotedSchemaTableCombination(db, operationsSchema, TABLE_HARVEST_RUN);
    String sql = "SELECT 1 FROM " + qualified + " WHERE harvest_run_id = " + sqlLiteral(runId);
    RowMetaAndData row = db.getOneRow(sql);
    return row != null && row.getData() != null && row.getData().length > 0;
  }

  private static void bestEffortDeleteRun(
      Database db, String operationsSchema, String runId, ILogChannel log) {
    try {
      for (String table :
          new String[] {
            TABLE_HARVEST_CHANGE,
            TABLE_HARVEST_FK,
            TABLE_HARVEST_FIELD,
            TABLE_HARVEST_SUBJECT,
            TABLE_HARVEST_RUN
          }) {
        String qualified =
            db.getDatabaseMeta().getQuotedSchemaTableCombination(db, operationsSchema, table);
        db.execStatement(
            "DELETE FROM " + qualified + " WHERE harvest_run_id = " + sqlLiteral(runId));
      }
    } catch (Exception e) {
      if (log != null) {
        log.logError("Failed to clean up partial harvest run " + runId + ": " + e.getMessage());
      }
    }
  }

  private static void insertHarvestRun(Database db, String operationsSchema, HarvestResult result)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("harvest_run_id", 64));
    layout.addValueMeta(new ValueMetaDate("started_at"));
    layout.addValueMeta(new ValueMetaDate("finished_at"));
    layout.addValueMeta(stringMeta("resource_group_name", 255));
    layout.addValueMeta(stringMeta("catalog_connection", 255));
    layout.addValueMeta(stringMeta("expected_baseline", 128));
    layout.addValueMeta(stringMeta("status", 16));
    layout.addValueMeta(new ValueMetaInteger("subject_count"));
    layout.addValueMeta(new ValueMetaInteger("change_count"));
    layout.addValueMeta(new ValueMetaInteger("error_count"));
    layout.addValueMeta(new ValueMetaInteger("subjects_with_changes"));
    layout.addValueMeta(stringMeta("workflow_name", 255));
    layout.addValueMeta(stringMeta("workflow_execution_id", 64));
    layout.addValueMeta(stringMeta("scope_summary", 2000));

    Date started = result.getStartedAt() != null ? Date.from(result.getStartedAt()) : new Date();
    Date finished = result.getFinishedAt() != null ? Date.from(result.getFinishedAt()) : new Date();
    Object[] row =
        new Object[] {
          result.getHarvestRunId(),
          started,
          finished,
          truncate(result.getResourceGroupName(), 255),
          truncate(result.getCatalogConnection(), 255),
          truncate(result.getExpectedBaseline(), 128),
          result.getStatus() != null ? result.getStatus().name() : null,
          (long) result.subjectCount(),
          (long) result.changeCount(),
          (long) result.errorCount(),
          (long) result.subjectsWithChanges(),
          truncate(result.getWorkflowName(), 255),
          truncate(result.getWorkflowExecutionId(), 64),
          truncate(result.getScopeSummary(), 2000)
        };
    db.insertRow(operationsSchema, TABLE_HARVEST_RUN, layout, row);
  }

  private static void insertSubject(
      Database db, String operationsSchema, String runId, HarvestSubjectResult subject)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("harvest_run_id", 64));
    layout.addValueMeta(stringMeta("subject_key", 512));
    layout.addValueMeta(stringMeta("catalog_connection", 255));
    layout.addValueMeta(stringMeta("source_type", 32));
    layout.addValueMeta(stringMeta("database_meta_name", 255));
    layout.addValueMeta(stringMeta("schema_name", 255));
    layout.addValueMeta(stringMeta("table_name", 255));
    layout.addValueMeta(stringMeta("discovery_status", 32));
    layout.addValueMeta(new ValueMetaBoolean("in_sync"));
    layout.addValueMeta(new ValueMetaInteger("change_count"));
    layout.addValueMeta(stringMeta("message", 4000));

    Object[] row =
        new Object[] {
          runId,
          truncate(subject.getSubjectKey(), 512),
          truncate(subject.getCatalogConnection(), 255),
          truncate(subject.getSourceType(), 32),
          truncate(subject.getDatabaseMetaName(), 255),
          truncate(subject.getSchemaName(), 255),
          truncate(subject.getTableName(), 255),
          subject.getDiscoveryStatus() != null ? subject.getDiscoveryStatus().name() : null,
          subject.isInSync(),
          (long) subject.changeCount(),
          truncate(subject.getMessage(), 4000)
        };
    db.insertRow(operationsSchema, TABLE_HARVEST_SUBJECT, layout, row);
  }

  private static void insertForeignKey(
      Database db, String operationsSchema, String runId, String subjectKey, HarvestedForeignKey fk)
      throws HopException {
    String constraint =
        Utils.isEmpty(fk.getConstraintName())
            ? Const.NVL(fk.signatureKey(), "fk")
            : fk.getConstraintName();
    // PK is (run, subject, role, constraint_name) — keep constraint unique-ish per role.
    if (constraint.length() > 255) {
      constraint = constraint.substring(0, 255);
    }
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("harvest_run_id", 64));
    layout.addValueMeta(stringMeta("subject_key", 512));
    layout.addValueMeta(stringMeta("field_role", 16));
    layout.addValueMeta(stringMeta("constraint_name", 255));
    layout.addValueMeta(stringMeta("child_schema", 255));
    layout.addValueMeta(stringMeta("child_table", 255));
    layout.addValueMeta(stringMeta("child_columns", 2000));
    layout.addValueMeta(stringMeta("parent_schema", 255));
    layout.addValueMeta(stringMeta("parent_table", 255));
    layout.addValueMeta(stringMeta("parent_columns", 2000));
    Object[] row =
        new Object[] {
          runId,
          truncate(subjectKey, 512),
          fk.getRole() != null ? fk.getRole().name() : "DISCOVERED",
          truncate(constraint, 255),
          truncate(fk.getChildSchema(), 255),
          truncate(fk.getChildTable(), 255),
          truncate(fk.getChildColumns(), 2000),
          truncate(fk.getParentSchema(), 255),
          truncate(fk.getParentTable(), 255),
          truncate(fk.getParentColumns(), 2000)
        };
    db.insertRow(operationsSchema, TABLE_HARVEST_FK, layout, row);
  }

  private static void insertField(
      Database db, String operationsSchema, String runId, String subjectKey, HarvestedField field)
      throws HopException {
    if (Utils.isEmpty(field.getFieldName())) {
      return;
    }
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("harvest_run_id", 64));
    layout.addValueMeta(stringMeta("subject_key", 512));
    layout.addValueMeta(stringMeta("field_role", 16));
    layout.addValueMeta(stringMeta("field_name", 255));
    layout.addValueMeta(stringMeta("hop_type", 64));
    layout.addValueMeta(stringMeta("field_length", 64));
    layout.addValueMeta(stringMeta("field_precision", 64));
    layout.addValueMeta(new ValueMetaInteger("primary_key_position"));
    layout.addValueMeta(stringMeta("source_data_type", 128));

    Object[] row =
        new Object[] {
          runId,
          truncate(subjectKey, 512),
          field.getRole() != null ? field.getRole().name() : "DISCOVERED",
          truncate(field.getFieldName(), 255),
          truncate(field.getHopType(), 64),
          truncate(field.getLength(), 64),
          truncate(field.getPrecision(), 64),
          (long) field.getPrimaryKeyPosition(),
          truncate(field.getSourceDataType(), 128)
        };
    db.insertRow(operationsSchema, TABLE_HARVEST_FIELD, layout, row);
  }

  private static void insertChange(
      Database db,
      String operationsSchema,
      String runId,
      long changeSeq,
      String subjectKey,
      HarvestChange change)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("harvest_run_id", 64));
    layout.addValueMeta(new ValueMetaInteger("change_seq"));
    layout.addValueMeta(stringMeta("subject_key", 512));
    layout.addValueMeta(stringMeta("change_kind", 64));
    layout.addValueMeta(stringMeta("field_name", 255));
    layout.addValueMeta(stringMeta("expected_detail", 2000));
    layout.addValueMeta(stringMeta("actual_detail", 2000));
    layout.addValueMeta(stringMeta("severity", 16));

    Object[] row =
        new Object[] {
          runId,
          changeSeq,
          truncate(subjectKey, 512),
          truncate(change.getChangeKind(), 64),
          truncate(change.getFieldName(), 255),
          truncate(change.getExpectedDetail(), 2000),
          truncate(change.getActualDetail(), 2000),
          truncate(change.getSeverity(), 16)
        };
    db.insertRow(operationsSchema, TABLE_HARVEST_CHANGE, layout, row);
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
}
