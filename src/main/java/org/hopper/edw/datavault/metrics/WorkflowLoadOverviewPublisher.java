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
package org.hopper.edw.datavault.metrics;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.hopper.edw.catalog.model.PhysicalTableRef;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
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
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes workflow load overview rows and catalog record definitions. */
public final class WorkflowLoadOverviewPublisher {

  private WorkflowLoadOverviewPublisher() {}

  public static void publish(
      ILogChannel log,
      WorkflowOverviewMetricsResolver.ResolvedOverviewMetrics settings,
      WorkflowLoadOverviewReport report,
      boolean publishCatalogDefinitions,
      boolean publishDatabaseRows,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (report == null || settings == null) {
      return;
    }
    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(settings.targetDatabaseName());
    if (databaseMeta == null) {
      throw new HopException(
          "Target database connection not found: " + settings.targetDatabaseName());
    }
    String operationsSchema =
        LoadRunMetricsCatalogPublisher.resolvePhysicalOperationsSchema(
            settings.operationsSchema(), databaseMeta);
    String namespace = operationsNamespace(variables);
    Date finishedAt = report.getFinishedAt() != null ? report.getFinishedAt() : new Date();

    if (publishCatalogDefinitions
        && settings.publishCatalogDefinitions()
        && !Utils.isEmpty(settings.catalogConnectionName())) {
      publishRecordDefinitions(
          settings.catalogConnectionName(),
          namespace,
          settings.targetDatabaseName(),
          operationsSchema,
          databaseMeta,
          variables,
          metadataProvider,
          finishedAt);
    }

    if (publishDatabaseRows && settings.publishDatabaseRows()) {
      insertOverviewRows(
          log, databaseMeta, operationsSchema, settings.autoCreateTables(), report, variables);
    }
  }

  public static String operationsNamespace(IVariables variables) {
    return "hop/" + DvCatalogNamespaces.resolveProjectKey(variables) + "/operations";
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
        buildOverviewDefinition(namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildOverviewModelDefinition(namespace, targetDatabaseName, operationsSchema, databaseMeta),
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

  static RecordDefinition buildOverviewDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta)
      throws HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("overview_id", 64));
    fields.addValueMeta(stringMeta("workflow_execution_id", 64));
    fields.addValueMeta(stringMeta("root_workflow_name", 255));
    fields.addValueMeta(stringMeta("metrics_workflow_name", 255));
    fields.addValueMeta(new ValueMetaDate("started_at"));
    fields.addValueMeta(new ValueMetaDate("finished_at"));
    fields.addValueMeta(new ValueMetaInteger("duration_ms"));
    fields.addValueMeta(new ValueMetaInteger("model_count"));
    fields.addValueMeta(new ValueMetaInteger("pipeline_count"));
    fields.addValueMeta(new ValueMetaInteger("insight_count"));
    fields.addValueMeta(new ValueMetaInteger("total_source_rows_read"));
    fields.addValueMeta(new ValueMetaInteger("total_target_rows_inserted"));
    fields.addValueMeta(new ValueMetaInteger("total_errors"));
    fields.addValueMeta(new ValueMetaBoolean("success"));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(
        new RecordDefinitionKey(
            namespace, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Workflow-level load overview for one vault update execution");
    definition.setPhysicalTable(
        physicalTableRef(
            targetDatabaseName,
            operationsSchema,
            databaseMeta,
            WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW));
    org.hopper.edw.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(
        definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("load-metrics");
    definition.getTags().add("workflow-overview");
    return definition;
  }

  static RecordDefinition buildOverviewModelDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta)
      throws HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("overview_id", 64));
    fields.addValueMeta(new ValueMetaInteger("sequence_no"));
    fields.addValueMeta(stringMeta("load_run_id", 64));
    fields.addValueMeta(stringMeta("model_type", 16));
    fields.addValueMeta(stringMeta("model_name", 255));
    fields.addValueMeta(new ValueMetaInteger("pipeline_count"));
    fields.addValueMeta(new ValueMetaInteger("source_rows_read"));
    fields.addValueMeta(new ValueMetaInteger("target_rows_read"));
    fields.addValueMeta(new ValueMetaInteger("target_rows_inserted"));
    fields.addValueMeta(new ValueMetaInteger("errors"));
    fields.addValueMeta(new ValueMetaInteger("duration_ms"));
    fields.addValueMeta(new ValueMetaInteger("insight_count"));
    fields.addValueMeta(new ValueMetaBoolean("success"));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(
        new RecordDefinitionKey(
            namespace, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Per-model summary rows for one workflow load overview");
    definition.setPhysicalTable(
        physicalTableRef(
            targetDatabaseName,
            operationsSchema,
            databaseMeta,
            WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL));
    org.hopper.edw.datavault.catalog.DvSourceFieldSupport.applyRowMetaLayoutToDefinition(
        definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("load-metrics");
    definition.getTags().add("workflow-overview");
    return definition;
  }

  private static void insertOverviewRows(
      ILogChannel log,
      DatabaseMeta databaseMeta,
      String operationsSchema,
      boolean autoCreateTables,
      WorkflowLoadOverviewReport report,
      IVariables variables)
      throws HopException {
    LoggingObject loggingObject = new LoggingObject(WorkflowLoadOverviewPublisher.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (autoCreateTables) {
        WorkflowLoadOverviewDdlSupport.ensureOverviewTables(
            db, databaseMeta, operationsSchema, log);
      }
      insertOverviewHeader(db, operationsSchema, report);
      insertOverviewModels(db, operationsSchema, report);
      if (log != null) {
        log.logBasic(
            "Published workflow load overview "
                + report.getOverviewId()
                + " ("
                + report.getModelCount()
                + " models)");
      }
    } catch (Exception e) {
      throw new HopException("Unable to publish workflow load overview to database", e);
    } finally {
      db.disconnect();
    }
  }

  private static void insertOverviewHeader(
      Database db, String operationsSchema, WorkflowLoadOverviewReport report) throws HopException {
    String qualifiedTable =
        db.getDatabaseMeta()
            .getQuotedSchemaTableCombination(
                db, operationsSchema, WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW);
    String sql =
        "INSERT INTO "
            + qualifiedTable
            + " (overview_id, workflow_execution_id, root_workflow_name, metrics_workflow_name, started_at, finished_at, duration_ms, model_count, pipeline_count, insight_count, total_source_rows_read, total_target_rows_inserted, total_errors, success) VALUES ("
            + sqlLiteral(report.getOverviewId())
            + ", "
            + sqlLiteral(report.getWorkflowExecutionId())
            + ", "
            + sqlLiteral(report.getRootWorkflowName())
            + ", "
            + sqlLiteral(report.getMetricsWorkflowName())
            + ", "
            + sqlTimestampLiteral(db, report.getStartedAt())
            + ", "
            + sqlTimestampLiteral(db, report.getFinishedAt())
            + ", "
            + report.getDurationMs()
            + ", "
            + report.getModelCount()
            + ", "
            + report.getPipelineCount()
            + ", "
            + report.getInsightCount()
            + ", "
            + report.getTotalSourceRowsRead()
            + ", "
            + report.getTotalTargetRowsInserted()
            + ", "
            + report.getTotalErrors()
            + ", "
            + sqlBooleanLiteral(db.getDatabaseMeta(), report.isSuccess())
            + ")";
    db.execStatement(sql);
  }

  private static void insertOverviewModels(
      Database db, String operationsSchema, WorkflowLoadOverviewReport report) throws HopException {
    List<WorkflowLoadOverviewReport.ModelEntry> models = report.getModels();
    if (models == null || models.isEmpty()) {
      return;
    }
    String qualifiedTable =
        db.getDatabaseMeta()
            .getQuotedSchemaTableCombination(
                db,
                operationsSchema,
                WorkflowLoadOverviewDdlSupport.TABLE_WORKFLOW_LOAD_OVERVIEW_MODEL);
    for (WorkflowLoadOverviewReport.ModelEntry model : models) {
      String sql =
          "INSERT INTO "
              + qualifiedTable
              + " (overview_id, sequence_no, load_run_id, model_type, model_name, pipeline_count, source_rows_read, target_rows_read, target_rows_inserted, errors, duration_ms, insight_count, success) VALUES ("
              + sqlLiteral(report.getOverviewId())
              + ", "
              + model.getSequenceNo()
              + ", "
              + sqlLiteral(model.getLoadRunId())
              + ", "
              + sqlLiteral(model.getModelType())
              + ", "
              + sqlLiteral(model.getModelName())
              + ", "
              + model.getPipelineCount()
              + ", "
              + model.getSourceRowsRead()
              + ", "
              + model.getTargetRowsRead()
              + ", "
              + model.getTargetRowsInserted()
              + ", "
              + model.getErrors()
              + ", "
              + model.getDurationMs()
              + ", "
              + model.getInsightCount()
              + ", "
              + sqlBooleanLiteral(db.getDatabaseMeta(), model.isSuccess())
              + ")";
      db.execStatement(sql);
    }
  }

  private static PhysicalTableRef physicalTableRef(
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta,
      String tableName) {
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

  private static String sqlLiteral(String value) {
    if (value == null) {
      return "NULL";
    }
    return "'" + value.replace("'", "''") + "'";
  }

  private static String sqlTimestampLiteral(Database db, Date value) {
    if (value == null) {
      return "NULL";
    }
    String formatted = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(value);
    DatabaseMeta meta = db != null ? db.getDatabaseMeta() : null;
    if (isSqlServer(meta)) {
      return "CAST('" + formatted + "' AS datetime2)";
    }
    if (LoadRunMetricsCatalogPublisher.isMysqlFamily(meta)) {
      return "'" + formatted + "'";
    }
    return "TIMESTAMP '" + formatted + "'";
  }

  private static String sqlBooleanLiteral(DatabaseMeta databaseMeta, boolean value) {
    if (isSqlServer(databaseMeta) || LoadRunMetricsCatalogPublisher.isMysqlFamily(databaseMeta)) {
      return value ? "1" : "0";
    }
    return value ? "TRUE" : "FALSE";
  }

  private static boolean isSqlServer(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return false;
    }
    String pluginId = databaseMeta.getPluginId();
    return DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId);
  }
}
