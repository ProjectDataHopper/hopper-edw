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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.GeneratedPipelineMetadataSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.sql.ExecSqlMeta;

/**
 * Generates build pipelines that materialize SQL business tables as views or tables via Execute
 * SQL, with dialect-aware CREATE statements.
 */
public final class BvSqlViewPipelineSupport {

  private static final Class<?> PKG = BvSqlViewPipelineSupport.class;

  private static final Point LOCATION_EXEC_SQL = new Point(160, 160);

  private BvSqlViewPipelineSupport() {}

  /**
   * Result of building materialization SQL for a business table.
   *
   * @param sql full script to run (may contain multiple statements)
   * @param singleStatement when true, Exec SQL should run the script as one statement
   */
  public record CreateScript(String sql, boolean singleStatement) {}

  public static List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvBusinessTable businessTable)
      throws HopException {
    List<PipelineMeta> pipelines = new ArrayList<>();
    if (businessTable == null || bvModel == null) {
      return pipelines;
    }
    if (Utils.isEmpty(businessTable.getSqlQuery())) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvSqlViewPipelineSupport.Error.MissingSql", businessTable.getName()));
    }

    BusinessVaultConfiguration bvConfig = bvModel.getConfigurationOrDefault();
    DatabaseMeta targetDatabaseMeta =
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
    if (targetDatabaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvSqlViewPipelineSupport.Error.MissingBvTargetDatabase",
              businessTable.getName()));
    }

    BvSqlRefResolver.syncRefsFromSql(businessTable, bvModel, dvModel, variables, metadataProvider);
    String resolvedQuery =
        BvSqlRefResolver.resolveSql(
            businessTable, bvModel, dvModel, metadataProvider, variables, targetDatabaseMeta);
    CreateScript createScript =
        buildCreateScript(businessTable, targetDatabaseMeta, variables, resolvedQuery);

    String targetTableName =
        !Utils.isEmpty(businessTable.getTableName())
            ? businessTable.getTableName()
            : businessTable.getName();
    String pipelineName = bvConfig.buildBusinessTablePipelineName(variables, targetTableName);

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(pipelineName);
    pipelineMeta.setMetadataProvider(metadataProvider);

    ExecSqlMeta execSqlMeta = new ExecSqlMeta();
    execSqlMeta.setConnection(targetDatabaseMeta.getName());
    execSqlMeta.setSql(createScript.sql());
    execSqlMeta.setExecutedEachInputRow(false);
    execSqlMeta.setSingleStatement(createScript.singleStatement());
    execSqlMeta.setReplaceVariables(true);

    TransformMeta transformMeta = new TransformMeta("create_" + targetTableName, execSqlMeta);
    transformMeta.setLocation(LOCATION_EXEC_SQL.x, LOCATION_EXEC_SQL.y);
    pipelineMeta.addTransform(transformMeta);

    GeneratedPipelineMetadataSupport.stampPipeline(
        pipelineMeta,
        new GeneratedPipelineMetadataSupport.PipelineContext(
            "BUSINESS_VAULT_MODEL",
            bvModel.getName(),
            bvModel.getFilename(),
            "BUSINESS_TABLE",
            businessTable.getName(),
            targetTableName,
            null,
            pipelineName));

    BvGeneratedPipelineSupport.applyLayout(pipelineMeta);
    pipelines.add(pipelineMeta);
    return pipelines;
  }

  /**
   * Builds dialect-aware materialization SQL for a business table.
   *
   * <p>Convenience wrapper that returns only the SQL text (used by the Generated SQL dialog).
   */
  public static String buildCreateStatement(
      BvBusinessTable businessTable,
      DatabaseMeta targetDatabaseMeta,
      IVariables variables,
      String resolvedQuery)
      throws HopException {
    return buildCreateScript(businessTable, targetDatabaseMeta, variables, resolvedQuery).sql();
  }

  /**
   * Builds dialect-aware materialization SQL.
   *
   * <ul>
   *   <li><b>VIEW / Postgres, MySQL, generic</b> — {@code CREATE OR REPLACE VIEW t AS q}
   *   <li><b>VIEW / SQL Server</b> — {@code CREATE OR ALTER VIEW t AS q}
   *   <li><b>VIEW / SingleStore</b> — {@code DROP VIEW IF EXISTS t;} + {@code CREATE VIEW t AS q}
   *       (SingleStore does not support {@code CREATE OR REPLACE VIEW})
   *   <li><b>TABLE / MySQL, SingleStore</b> — {@code CREATE OR REPLACE TABLE t AS q}
   *   <li><b>TABLE / Postgres</b> — {@code DROP TABLE IF EXISTS t;} + {@code CREATE TABLE t AS q}
   *   <li><b>TABLE / SQL Server</b> — {@code DROP TABLE IF EXISTS t;} + {@code SELECT * INTO t FROM
   *       (q) AS _src}
   * </ul>
   */
  public static CreateScript buildCreateScript(
      BvBusinessTable businessTable,
      DatabaseMeta targetDatabaseMeta,
      IVariables variables,
      String resolvedQuery)
      throws HopException {
    if (businessTable == null) {
      throw new HopException("Business table is required");
    }
    if (Utils.isEmpty(resolvedQuery)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvSqlViewPipelineSupport.Error.MissingSql", businessTable.getName()));
    }
    String targetTableName =
        !Utils.isEmpty(businessTable.getTableName())
            ? businessTable.getTableName()
            : businessTable.getName();
    if (Utils.isEmpty(targetTableName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvSqlViewPipelineSupport.Error.MissingTableName", businessTable.getName()));
    }

    String quotedTarget =
        BvSqlRefResolver.quoteTable(targetDatabaseMeta, variables, null, targetTableName);
    BvSqlMaterialization materialization = businessTable.getMaterializationOrDefault();
    String query = stripTrailingSemicolon(resolvedQuery.trim());
    // Portable authoring SQL often uses ANSI TIMESTAMP '…'; rewrite for SQL Server / MySQL.
    query = BvPitSnapshotSpineSupport.normalizeAnsiTimestampLiterals(targetDatabaseMeta, query);
    BvPitSnapshotSpineSupport.PitSqlDialect dialect =
        BvPitSnapshotSpineSupport.resolveDialect(targetDatabaseMeta);

    if (materialization == BvSqlMaterialization.VIEW) {
      return buildViewScript(dialect, quotedTarget, query);
    }
    return buildTableScript(dialect, quotedTarget, query);
  }

  private static CreateScript buildViewScript(
      BvPitSnapshotSpineSupport.PitSqlDialect dialect, String quotedTarget, String query) {
    return switch (dialect) {
      case SQL_SERVER ->
          new CreateScript("CREATE OR ALTER VIEW " + quotedTarget + " AS\n" + query, true);
      case SINGLESTORE ->
          // SingleStore rejects CREATE OR REPLACE VIEW; drop then create (multi-statement).
          new CreateScript(
              "DROP VIEW IF EXISTS "
                  + quotedTarget
                  + ";\n"
                  + "CREATE VIEW "
                  + quotedTarget
                  + " AS\n"
                  + query,
              false);
      case POSTGRES, MYSQL ->
          new CreateScript("CREATE OR REPLACE VIEW " + quotedTarget + " AS\n" + query, true);
    };
  }

  private static CreateScript buildTableScript(
      BvPitSnapshotSpineSupport.PitSqlDialect dialect, String quotedTarget, String query) {
    return switch (dialect) {
      case SQL_SERVER ->
          new CreateScript(
              "DROP TABLE IF EXISTS "
                  + quotedTarget
                  + ";\n"
                  + "SELECT * INTO "
                  + quotedTarget
                  + " FROM (\n"
                  + query
                  + "\n) AS _bv_sql_src",
              false);
      case MYSQL, SINGLESTORE ->
          new CreateScript("CREATE OR REPLACE TABLE " + quotedTarget + " AS\n" + query, true);
      case POSTGRES ->
          // Postgres has no CREATE OR REPLACE TABLE; drop then CTAS.
          new CreateScript(
              "DROP TABLE IF EXISTS "
                  + quotedTarget
                  + ";\n"
                  + "CREATE TABLE "
                  + quotedTarget
                  + " AS\n"
                  + query,
              false);
    };
  }

  private static String stripTrailingSemicolon(String sql) {
    if (sql == null) {
      return null;
    }
    String trimmed = sql.trim();
    while (trimmed.endsWith(";")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    return trimmed;
  }
}
