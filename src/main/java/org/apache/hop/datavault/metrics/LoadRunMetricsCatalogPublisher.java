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
package org.apache.hop.datavault.metrics;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
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
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DvBulkLoadPluginSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes load-run metrics record definitions and persists run rows to the target database. */
public final class LoadRunMetricsCatalogPublisher {

  /**
   * Default operations schema: empty = connection default database/schema. Users may set an
   * explicit schema (e.g. {@code ops}) on the execution metrics profile when they want isolation.
   */
  public static final String DEFAULT_SCHEMA_NAME = "";

  /**
   * Historical product default schema name. Not applied implicitly; only used when a user
   * explicitly configures it.
   */
  public static final String LEGACY_OPS_SCHEMA_NAME = "dv_ops";

  /**
   * @deprecated use {@link #DEFAULT_SCHEMA_NAME}
   */
  @Deprecated public static final String SCHEMA_NAME = DEFAULT_SCHEMA_NAME;

  public static final String TABLE_LOAD_RUN = "load_run";
  public static final String TABLE_LOAD_PIPELINE_METRIC = "load_pipeline_metric";
  public static final String TABLE_LOAD_TRANSFORM_METRIC = "load_transform_metric";
  public static final String TABLE_LOAD_INSIGHT = "load_insight";

  private LoadRunMetricsCatalogPublisher() {}

  public static void publish(
      ILogChannel log,
      DvUpdateMetricsCollector.LoadRunPublishContext publishContext,
      String runId,
      String modelName,
      String modelType,
      String workflowName,
      String logChannelId,
      boolean success,
      long errorCount,
      Date startedAt,
      List<DvUpdateTableMetrics> pipelines,
      List<LoadRunInsight> insights,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(runId)
        || pipelines == null
        || pipelines.isEmpty()
        || publishContext == null) {
      return;
    }
    String targetDatabaseName = publishContext.targetDatabaseName();
    if (Utils.isEmpty(targetDatabaseName)) {
      throw new HopException(
          "Target database connection is required for load-run metrics publishing");
    }

    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(targetDatabaseName);
    if (databaseMeta == null) {
      throw new HopException("Target database connection not found: " + targetDatabaseName);
    }

    String operationsSchema = resolveOperationsSchema(publishContext);
    String namespace = operationsNamespace(variables);
    Date finishedAt = new Date();
    String catalogConnectionName = publishContext.catalogConnectionName();

    if (publishContext.publishCatalogDefinitions() && !Utils.isEmpty(catalogConnectionName)) {
      publishRecordDefinitions(
          catalogConnectionName,
          namespace,
          targetDatabaseName,
          operationsSchema,
          databaseMeta,
          variables,
          metadataProvider,
          finishedAt);
    }

    if (publishContext.publishDatabaseRows()) {
      insertRunRows(
          log,
          databaseMeta,
          operationsSchema,
          publishContext.autoCreateTables(),
          variables,
          runId,
          modelName,
          modelType,
          workflowName,
          logChannelId,
          publishContext.pipelineRunConfiguration(),
          success,
          errorCount,
          resolveRunStartedAt(startedAt, finishedAt, pipelines),
          finishedAt,
          pipelines,
          insights);
    }
  }

  /**
   * @deprecated use {@link #publish(ILogChannel, DvUpdateMetricsCollector.LoadRunPublishContext,
   *     String, String, String, String, String, boolean, long, List, List, IVariables,
   *     IHopMetadataProvider)}
   */
  @Deprecated
  public static void publish(
      ILogChannel log,
      DvUpdateMetricsCollector.LoadRunPublishContext publishContext,
      String runId,
      String modelName,
      String modelType,
      String workflowName,
      String logChannelId,
      boolean success,
      long errorCount,
      List<DvUpdateTableMetrics> pipelines,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    publish(
        log,
        publishContext,
        runId,
        modelName,
        modelType,
        workflowName,
        logChannelId,
        success,
        errorCount,
        null,
        pipelines,
        List.of(),
        variables,
        metadataProvider);
  }

  private static Date resolveRunStartedAt(
      Date startedAt, Date finishedAt, List<DvUpdateTableMetrics> pipelines) {
    if (startedAt != null && finishedAt != null && startedAt.before(finishedAt)) {
      return startedAt;
    }
    if (finishedAt == null) {
      return startedAt;
    }
    long durationMs = sumPipelineDurationMs(pipelines);
    if (durationMs > 0) {
      return new Date(finishedAt.getTime() - durationMs);
    }
    return startedAt != null ? startedAt : finishedAt;
  }

  private static long sumPipelineDurationMs(List<DvUpdateTableMetrics> pipelines) {
    if (pipelines == null || pipelines.isEmpty()) {
      return 0L;
    }
    long total = 0L;
    for (DvUpdateTableMetrics pipeline : pipelines) {
      if (pipeline == null || pipeline.getTransforms() == null) {
        continue;
      }
      for (TransformRunMetrics transform : pipeline.getTransforms()) {
        if (transform != null) {
          total += Math.max(0L, transform.getDurationMs());
        }
      }
    }
    return total;
  }

  /**
   * Resolves the configured operations schema. Blank means connection default (no schema
   * qualifier). Explicit values such as {@code ops} or legacy {@code dv_ops} are kept as-is.
   */
  static String resolveOperationsSchema(DvUpdateMetricsCollector.LoadRunPublishContext context) {
    if (context == null || Utils.isEmpty(context.operationsSchema())) {
      return DEFAULT_SCHEMA_NAME;
    }
    return context.operationsSchema().trim();
  }

  /**
   * Physical schema/database qualifier for SQL. Blank remains blank (connection default). Explicit
   * names are unchanged so opt-in isolation keeps working.
   */
  public static String resolvePhysicalOperationsSchema(
      String operationsSchema, DatabaseMeta databaseMeta) {
    if (Utils.isEmpty(operationsSchema)) {
      return DEFAULT_SCHEMA_NAME;
    }
    return operationsSchema.trim();
  }

  /** True for MySQL and SingleStore (MemSQL) database plugin ids. */
  public static boolean isMysqlFamily(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return false;
    }
    String pluginId = databaseMeta.getPluginId();
    return DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId);
  }

  /** Qualifies {@code schema.table}, or returns {@code table} when schema is blank. */
  public static String qualifyOperationsTable(String schema, String table) {
    if (Utils.isEmpty(schema)) {
      return table;
    }
    return schema + "." + table;
  }

  /** Human-readable location for logs when the physical schema may be the connection default. */
  public static String describeOperationsLocation(String physicalSchema) {
    if (Utils.isEmpty(physicalSchema)) {
      return "connection default database";
    }
    return physicalSchema;
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
        buildLoadRunDefinition(namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildPipelineMetricDefinition(
            namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildTransformMetricDefinition(
            namespace, targetDatabaseName, operationsSchema, databaseMeta),
        variables,
        metadataProvider,
        updatedAt);
    upsertDefinition(
        registry,
        catalogConnectionName,
        buildInsightDefinition(namespace, targetDatabaseName, operationsSchema, databaseMeta),
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

  private static RecordDefinition buildLoadRunDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta)
      throws HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("run_id", 64));
    fields.addValueMeta(new ValueMetaDate("started_at"));
    fields.addValueMeta(new ValueMetaDate("finished_at"));
    fields.addValueMeta(stringMeta("model_type", 16));
    fields.addValueMeta(stringMeta("model_name", 255));
    fields.addValueMeta(stringMeta("workflow_name", 255));
    fields.addValueMeta(stringMeta("workflow_execution_id", 64));
    fields.addValueMeta(stringMeta("log_channel_id", 64));
    fields.addValueMeta(stringMeta("pipeline_run_configuration", 255));
    fields.addValueMeta(new ValueMetaBoolean("success"));
    fields.addValueMeta(new ValueMetaInteger("error_count"));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_LOAD_RUN));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Load run summary for generated DV/BV/DM update orchestrations");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, databaseMeta, TABLE_LOAD_RUN));
    DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("load-metrics");
    return definition;
  }

  private static RecordDefinition buildPipelineMetricDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta)
      throws HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("run_id", 64));
    fields.addValueMeta(stringMeta("pipeline_name", 255));
    fields.addValueMeta(stringMeta("element_type", 64));
    fields.addValueMeta(stringMeta("element_name", 255));
    fields.addValueMeta(stringMeta("source_name", 255));
    fields.addValueMeta(new ValueMetaInteger("source_rows_read"));
    fields.addValueMeta(new ValueMetaInteger("target_rows_read"));
    fields.addValueMeta(new ValueMetaInteger("target_rows_inserted"));
    fields.addValueMeta(new ValueMetaInteger("errors"));
    fields.addValueMeta(new ValueMetaDate("execution_start_date"));
    fields.addValueMeta(new ValueMetaDate("execution_end_date"));
    fields.addValueMeta(new ValueMetaInteger("duration_ms"));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_LOAD_PIPELINE_METRIC));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Per-pipeline metrics for one load run");
    definition.setPhysicalTable(
        physicalTableRef(
            targetDatabaseName, operationsSchema, databaseMeta, TABLE_LOAD_PIPELINE_METRIC));
    DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("load-metrics");
    return definition;
  }

  private static RecordDefinition buildTransformMetricDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta)
      throws HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("run_id", 64));
    fields.addValueMeta(stringMeta("pipeline_name", 255));
    fields.addValueMeta(stringMeta("transform_name", 255));
    fields.addValueMeta(stringMeta("logical_role", 64));
    fields.addValueMeta(stringMeta("element_type", 64));
    fields.addValueMeta(stringMeta("element_name", 255));
    fields.addValueMeta(stringMeta("parent_element_name", 255));
    fields.addValueMeta(new ValueMetaInteger("rows_read"));
    fields.addValueMeta(new ValueMetaInteger("rows_written"));
    fields.addValueMeta(new ValueMetaInteger("rows_updated"));
    fields.addValueMeta(new ValueMetaInteger("rows_rejected"));
    fields.addValueMeta(new ValueMetaInteger("errors"));
    fields.addValueMeta(new ValueMetaInteger("duration_ms"));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_LOAD_TRANSFORM_METRIC));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Per-transform metrics for one load run");
    definition.setPhysicalTable(
        physicalTableRef(
            targetDatabaseName, operationsSchema, databaseMeta, TABLE_LOAD_TRANSFORM_METRIC));
    DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("load-metrics");
    return definition;
  }

  private static RecordDefinition buildInsightDefinition(
      String namespace,
      String targetDatabaseName,
      String operationsSchema,
      DatabaseMeta databaseMeta)
      throws HopException {
    IRowMeta fields = new RowMeta();
    fields.addValueMeta(stringMeta("run_id", 64));
    fields.addValueMeta(new ValueMetaInteger("insight_seq"));
    fields.addValueMeta(stringMeta("severity", 16));
    fields.addValueMeta(stringMeta("code", 64));
    fields.addValueMeta(stringMeta("message", 2000));
    fields.addValueMeta(stringMeta("element_name", 255));
    fields.addValueMeta(stringMeta("related_element_name", 255));
    fields.addValueMeta(stringMeta("metric_json", 4000));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, TABLE_LOAD_INSIGHT));
    definition.setType(RecordDefinitionType.PHYSICAL_TABLE);
    definition.setDescription("Rule-based tuning insights for one load run");
    definition.setPhysicalTable(
        physicalTableRef(targetDatabaseName, operationsSchema, databaseMeta, TABLE_LOAD_INSIGHT));
    DvSourceFieldSupport.applyRowMetaLayoutToDefinition(definition, fields, null);
    definition.getTags().add("operations");
    definition.getTags().add("load-metrics");
    definition.getTags().add("insights");
    return definition;
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
    if (databaseMeta != null && !Utils.isEmpty(databaseMeta.getPreferredSchemaName())) {
      // Keep dv_ops as the metrics schema; model default schema stays on the connection.
    }
    return ref;
  }

  private static ValueMetaString stringMeta(String name, int length) {
    ValueMetaString meta = new ValueMetaString(name);
    meta.setLength(length);
    return meta;
  }

  private static void insertRunRows(
      ILogChannel log,
      DatabaseMeta databaseMeta,
      String operationsSchema,
      boolean autoCreateTables,
      IVariables variables,
      String runId,
      String modelName,
      String modelType,
      String workflowName,
      String logChannelId,
      String pipelineRunConfiguration,
      boolean success,
      long errorCount,
      Date startedAt,
      Date finishedAt,
      List<DvUpdateTableMetrics> pipelines,
      List<LoadRunInsight> insights)
      throws HopException {
    LoggingObject loggingObject = new LoggingObject(LoadRunMetricsCatalogPublisher.class);
    Database db = new Database(loggingObject, variables, databaseMeta);
    try {
      db.connect();
      if (autoCreateTables) {
        LoadRunMetricsDdlSupport.ensureMetricsTables(db, databaseMeta, operationsSchema, log);
      }
      insertLoadRun(
          db,
          operationsSchema,
          runId,
          modelName,
          modelType,
          workflowName,
          VaultUpdateExecutionSupport.resolveExecutionId(
              variables, VaultUpdateExecutionSupport.defaultExecutionIdVariableName()),
          logChannelId,
          pipelineRunConfiguration,
          success,
          errorCount,
          startedAt,
          finishedAt);
      for (DvUpdateTableMetrics pipeline : pipelines) {
        insertPipelineMetric(db, operationsSchema, runId, pipeline);
        for (TransformRunMetrics transform : pipeline.getTransforms()) {
          insertTransformMetric(db, operationsSchema, runId, pipeline.getPipelineName(), transform);
        }
      }
      insertInsights(db, operationsSchema, runId, insights);
      if (log != null) {
        int insightCount = insights != null ? insights.size() : 0;
        log.logBasic(
            "Published load-run metrics to "
                + describeOperationsLocation(operationsSchema)
                + " for run "
                + runId
                + " ("
                + pipelines.size()
                + " pipelines, "
                + insightCount
                + " insights)");
      }
    } catch (Exception e) {
      throw new HopException("Unable to publish load-run metrics to database", e);
    } finally {
      db.disconnect();
    }
  }

  private static void insertLoadRun(
      Database db,
      String operationsSchema,
      String runId,
      String modelName,
      String modelType,
      String workflowName,
      String workflowExecutionId,
      String logChannelId,
      String pipelineRunConfiguration,
      boolean success,
      long errorCount,
      Date startedAt,
      Date finishedAt)
      throws HopException {
    String qualifiedTable =
        db.getDatabaseMeta().getQuotedSchemaTableCombination(db, operationsSchema, TABLE_LOAD_RUN);
    String sql =
        "INSERT INTO "
            + qualifiedTable
            + " (run_id, started_at, finished_at, model_type, model_name, workflow_name, workflow_execution_id, log_channel_id, pipeline_run_configuration, success, error_count) VALUES ("
            + sqlLiteral(runId)
            + ", "
            + sqlTimestampLiteral(db, startedAt)
            + ", "
            + sqlTimestampLiteral(db, finishedAt)
            + ", "
            + sqlLiteral(modelType)
            + ", "
            + sqlLiteral(modelName)
            + ", "
            + sqlLiteral(workflowName)
            + ", "
            + sqlLiteral(workflowExecutionId)
            + ", "
            + sqlLiteral(logChannelId)
            + ", "
            + sqlLiteral(pipelineRunConfiguration)
            + ", "
            + sqlBooleanLiteral(db.getDatabaseMeta(), success)
            + ", "
            + errorCount
            + ")";
    try {
      db.execStatement(sql);
    } catch (Exception e) {
      throw new HopException(
          "Unable to insert load run row into "
              + qualifyOperationsTable(operationsSchema, TABLE_LOAD_RUN),
          e);
    }
  }

  private static String sqlLiteral(String value) {
    if (value == null) {
      return "NULL";
    }
    return "'" + value.replace("'", "''") + "'";
  }

  private static String sqlTimestampLiteral(Date value) {
    return sqlTimestampLiteral(null, value);
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
    // MySQL / SingleStore reject Postgres-style TIMESTAMP '…' literals.
    if (isMysqlFamily(meta)) {
      return "'" + formatted + "'";
    }
    return "TIMESTAMP '" + formatted + "'";
  }

  private static String sqlBooleanLiteral(DatabaseMeta databaseMeta, boolean value) {
    if (isSqlServer(databaseMeta) || isMysqlFamily(databaseMeta)) {
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

  private static void insertPipelineMetric(
      Database db, String operationsSchema, String runId, DvUpdateTableMetrics pipeline)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("run_id", 64));
    layout.addValueMeta(stringMeta("pipeline_name", 255));
    layout.addValueMeta(stringMeta("element_type", 64));
    layout.addValueMeta(stringMeta("element_name", 255));
    layout.addValueMeta(stringMeta("source_name", 255));
    layout.addValueMeta(new ValueMetaInteger("source_rows_read"));
    layout.addValueMeta(new ValueMetaInteger("target_rows_read"));
    layout.addValueMeta(new ValueMetaInteger("target_rows_inserted"));
    layout.addValueMeta(new ValueMetaInteger("errors"));
    layout.addValueMeta(new ValueMetaDate("execution_start_date"));
    layout.addValueMeta(new ValueMetaDate("execution_end_date"));
    layout.addValueMeta(new ValueMetaInteger("duration_ms"));

    Object[] row =
        new Object[] {
          runId,
          pipeline.getPipelineName(),
          pipeline.getTableType(),
          pipeline.getTableName(),
          pipeline.getSourceName(),
          pipeline.getSourceRowsRead(),
          pipeline.getTargetRowsRead(),
          pipeline.getTargetRowsInserted(),
          pipeline.getErrors(),
          pipeline.getExecutionStartDate(),
          pipeline.getExecutionEndDate(),
          pipeline.getDurationMs()
        };
    db.insertRow(operationsSchema, TABLE_LOAD_PIPELINE_METRIC, layout, row);
  }

  private static void insertTransformMetric(
      Database db,
      String operationsSchema,
      String runId,
      String pipelineName,
      TransformRunMetrics transform)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("run_id", 64));
    layout.addValueMeta(stringMeta("pipeline_name", 255));
    layout.addValueMeta(stringMeta("transform_name", 255));
    layout.addValueMeta(stringMeta("logical_role", 64));
    layout.addValueMeta(stringMeta("element_type", 64));
    layout.addValueMeta(stringMeta("element_name", 255));
    layout.addValueMeta(stringMeta("parent_element_name", 255));
    layout.addValueMeta(new ValueMetaInteger("rows_read"));
    layout.addValueMeta(new ValueMetaInteger("rows_written"));
    layout.addValueMeta(new ValueMetaInteger("rows_updated"));
    layout.addValueMeta(new ValueMetaInteger("rows_rejected"));
    layout.addValueMeta(new ValueMetaInteger("errors"));
    layout.addValueMeta(new ValueMetaInteger("duration_ms"));

    Object[] row =
        new Object[] {
          runId,
          pipelineName,
          transform.getTransformName(),
          transform.getLogicalRole(),
          transform.getElementType(),
          transform.getElementName(),
          transform.getParentElementName(),
          transform.getRowsRead(),
          transform.getRowsWritten(),
          transform.getRowsUpdated(),
          transform.getRowsRejected(),
          transform.getErrors(),
          transform.getDurationMs()
        };
    db.insertRow(operationsSchema, TABLE_LOAD_TRANSFORM_METRIC, layout, row);
  }

  private static void insertInsights(
      Database db, String operationsSchema, String runId, List<LoadRunInsight> insights)
      throws HopException {
    if (insights == null || insights.isEmpty()) {
      return;
    }
    long seq = 1L;
    for (LoadRunInsight insight : insights) {
      if (insight == null) {
        continue;
      }
      insertInsight(db, operationsSchema, runId, seq++, insight);
    }
  }

  private static void insertInsight(
      Database db, String operationsSchema, String runId, long insightSeq, LoadRunInsight insight)
      throws HopException {
    IRowMeta layout = new RowMeta();
    layout.addValueMeta(stringMeta("run_id", 64));
    layout.addValueMeta(new ValueMetaInteger("insight_seq"));
    layout.addValueMeta(stringMeta("severity", 16));
    layout.addValueMeta(stringMeta("code", 64));
    layout.addValueMeta(stringMeta("message", 2000));
    layout.addValueMeta(stringMeta("element_name", 255));
    layout.addValueMeta(stringMeta("related_element_name", 255));
    layout.addValueMeta(stringMeta("metric_json", 4000));

    Object[] row =
        new Object[] {
          runId,
          insightSeq,
          insight.getSeverity(),
          insight.getCode(),
          insight.getMessage(),
          insight.getElementName(),
          insight.getRelatedElementName(),
          insight.getMetricJson()
        };
    db.insertRow(operationsSchema, TABLE_LOAD_INSIGHT, layout, row);
  }
}
