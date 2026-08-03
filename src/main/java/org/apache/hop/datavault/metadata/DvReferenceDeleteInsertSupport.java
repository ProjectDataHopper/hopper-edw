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
package org.apache.hop.datavault.metadata;

import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.actions.sql.ActionSql;
import org.apache.hop.workflow.actions.start.ActionStart;

/**
 * DELETE_INSERT helpers for {@link DvReferenceTable}: same-database delete-by-natural-key SQL and
 * orchestration workflow (SQL then insert pipeline).
 *
 * <p>Normative semantics: keys present in the delta are deleted from the target then re-inserted;
 * keys absent from the delta are left unchanged.
 */
public final class DvReferenceDeleteInsertSupport {

  private DvReferenceDeleteInsertSupport() {}

  /**
   * True when the record source is a database source on the same metadata connection as the vault
   * target (VaultSpeed-style DFV-on-warehouse path).
   */
  public static boolean isSameDatabaseAsTarget(
      DataVaultSource recordSource,
      String targetDatabaseName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (recordSource == null || Utils.isEmpty(targetDatabaseName) || metadataProvider == null) {
      return false;
    }
    IDvSource dvSource = recordSource.getDvSource(metadataProvider);
    if (!(dvSource instanceof DvDatabaseSource databaseSource)) {
      return false;
    }
    String sourceDbName = databaseSource.getDatabaseName();
    if (Utils.isEmpty(sourceDbName)) {
      return false;
    }
    String resolvedSource = variables != null ? variables.resolve(sourceDbName) : sourceDbName;
    String resolvedTarget =
        variables != null ? variables.resolve(targetDatabaseName) : targetDatabaseName;
    return !Utils.isEmpty(resolvedSource) && resolvedSource.equalsIgnoreCase(resolvedTarget);
  }

  /**
   * Builds {@code DELETE FROM target WHERE EXISTS (SELECT 1 FROM source WHERE keys match)} for the
   * given natural-key mappings.
   */
  public static String buildDeleteByNaturalKeysSql(
      DatabaseMeta databaseMeta,
      String targetTableName,
      DvDatabaseSource source,
      List<BusinessKey> naturalKeysForSource,
      IVariables variables)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Target database metadata is required for DELETE_INSERT");
    }
    if (source == null) {
      throw new HopException("Database source is required for DELETE_INSERT");
    }
    if (Utils.isEmpty(naturalKeysForSource)) {
      throw new HopException("Natural keys are required for DELETE_INSERT");
    }

    String targetQuoted =
        databaseMeta.getQuotedSchemaTableCombination(variables, null, targetTableName);
    String sourceQuoted =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, source.getSchemaName(), source.getTableName());

    StringBuilder sql = new StringBuilder();
    String pluginId =
        databaseMeta.getPluginId() != null
            ? databaseMeta.getPluginId().toUpperCase(Locale.ROOT)
            : "";

    // MySQL / SingleStore require DELETE alias form for correlated EXISTS.
    boolean mysqlStyle =
        pluginId.contains("MYSQL")
            || pluginId.contains("SINGLESTORE")
            || pluginId.contains("MARIADB");

    if (mysqlStyle) {
      sql.append("DELETE t FROM ").append(targetQuoted).append(" t WHERE EXISTS (SELECT 1 FROM ");
      sql.append(sourceQuoted).append(" s WHERE ");
      appendKeyEquals(sql, naturalKeysForSource, variables, databaseMeta, "t", "s");
      sql.append(')');
    } else {
      // PostgreSQL, SQL Server, and most others: DELETE FROM target WHERE EXISTS (...)
      sql.append("DELETE FROM ").append(targetQuoted).append(" WHERE EXISTS (SELECT 1 FROM ");
      sql.append(sourceQuoted).append(" s WHERE ");
      appendKeyEquals(sql, naturalKeysForSource, variables, databaseMeta, targetQuoted, "s");
      sql.append(')');
    }
    return sql.toString();
  }

  private static void appendKeyEquals(
      StringBuilder sql,
      List<BusinessKey> keys,
      IVariables variables,
      DatabaseMeta databaseMeta,
      String targetAliasOrTable,
      String sourceAlias)
      throws HopException {
    boolean first = true;
    for (BusinessKey key : keys) {
      if (key == null || Utils.isEmpty(key.getName())) {
        continue;
      }
      String targetCol = variables != null ? variables.resolve(key.getName()) : key.getName();
      String sourceCol =
          !Utils.isEmpty(key.getSourceFieldName())
              ? (variables != null
                  ? variables.resolve(key.getSourceFieldName())
                  : key.getSourceFieldName())
              : targetCol;
      if (Utils.isEmpty(targetCol) || Utils.isEmpty(sourceCol)) {
        continue;
      }
      if (!first) {
        sql.append(" AND ");
      }
      first = false;
      sql.append(targetAliasOrTable)
          .append('.')
          .append(databaseMeta.quoteField(targetCol))
          .append(" = ")
          .append(sourceAlias)
          .append('.')
          .append(databaseMeta.quoteField(sourceCol));
    }
    if (first) {
      throw new HopException("No usable natural key columns for DELETE_INSERT SQL");
    }
  }

  /**
   * Builds {@code Start → (SQL delete → insert pipeline)*} for DELETE_INSERT same-database sources.
   *
   * <p>Pipeline action filenames are {@code pipelineName.hpl} placeholders (same as multi-source
   * hub workflows) so the update action can rewrite staged paths.
   */
  public static WorkflowMeta buildDeleteInsertWorkflow(
      String workflowName,
      String targetConnectionName,
      List<DeleteInsertStep> steps,
      DvMultiSourceUpdateWorkflowSupport.PipelineActionFactory pipelineActionFactory)
      throws HopException {
    if (steps == null || steps.isEmpty()) {
      throw new HopException("At least one DELETE_INSERT step is required");
    }
    if (Utils.isEmpty(targetConnectionName)) {
      throw new HopException("Target connection is required for DELETE_INSERT workflow");
    }

    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName(Utils.isEmpty(workflowName) ? "ref-delete-insert" : workflowName);

    ActionStart startAction = new ActionStart("Start");
    ActionMeta previous = new ActionMeta(startAction);
    previous.setLocation(50, 50);
    workflowMeta.addAction(previous);

    int x = 250;
    int y = 50;
    int index = 0;
    for (DeleteInsertStep step : steps) {
      if (step == null || Utils.isEmpty(step.deleteSql()) || step.pipelineMeta() == null) {
        continue;
      }
      String pipelineName = step.pipelineMeta().getName();
      if (Utils.isEmpty(pipelineName)) {
        throw new HopException("DELETE_INSERT insert pipeline has no name");
      }

      String sqlActionName = "delete_keys_" + sanitize(pipelineName);
      ActionSql sqlAction = new ActionSql(sqlActionName);
      sqlAction.setConnection(targetConnectionName);
      sqlAction.setSqlFromFile(false);
      sqlAction.setSql(step.deleteSql());
      sqlAction.setSendOneStatement(true);
      sqlAction.setUseVariableSubstitution(true);
      ActionMeta sqlMeta = new ActionMeta(sqlAction);
      sqlMeta.setLocation(x, y);
      workflowMeta.addAction(sqlMeta);
      workflowMeta.addWorkflowHop(new WorkflowHopMeta(previous, sqlMeta));
      previous = sqlMeta;
      x += 200;

      String placeholderFilename = pipelineName + PipelineMeta.PIPELINE_EXTENSION;
      ActionMeta pipelineAction =
          DvMultiSourceUpdateWorkflowSupport.newPipelineActionMeta(
              "insert_" + sanitize(pipelineName), placeholderFilename, null, pipelineActionFactory);
      pipelineAction.setLocation(x, y + (index % 2) * 40);
      workflowMeta.addAction(pipelineAction);
      workflowMeta.addWorkflowHop(new WorkflowHopMeta(previous, pipelineAction));
      previous = pipelineAction;
      x += 200;
      index++;
    }

    if (index == 0) {
      throw new HopException("No valid DELETE_INSERT steps to orchestrate");
    }
    return workflowMeta;
  }

  /** One same-DB source: pre-delete SQL + insert pipeline. */
  public record DeleteInsertStep(String deleteSql, PipelineMeta pipelineMeta) {}

  private static String sanitize(String name) {
    if (Utils.isEmpty(name)) {
      return "step";
    }
    return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
  }

  /** Convenience: true when load mode is DELETE_INSERT (or synonym). */
  public static boolean isDeleteInsertMode(DvReferenceLoadMode mode) {
    return mode == DvReferenceLoadMode.DELETE_INSERT;
  }

  public static boolean sourceLooksDatabase(DataVaultSource source, IHopMetadataProvider provider)
      throws HopException {
    if (source == null) {
      return false;
    }
    IDvSource dv = source.getDvSource(provider);
    return dv instanceof DvDatabaseSource;
  }

  public static String describeFallbackReason(DataVaultSource source, IHopMetadataProvider provider)
      throws HopException {
    if (source == null) {
      return "missing record source";
    }
    IDvSource dv = source.getDvSource(provider);
    if (!(dv instanceof DvDatabaseSource)) {
      return "source type is not DATABASE (DELETE_INSERT same-DB path unavailable)";
    }
    return "source database connection differs from vault target";
  }

  /** Expose for tests: builds key equality fragment count. */
  public static int countMappedKeys(List<BusinessKey> keys) {
    if (keys == null) {
      return 0;
    }
    int n = 0;
    for (BusinessKey key : keys) {
      if (key != null && StringUtils.isNotEmpty(key.getName())) {
        n++;
      }
    }
    return n;
  }
}
