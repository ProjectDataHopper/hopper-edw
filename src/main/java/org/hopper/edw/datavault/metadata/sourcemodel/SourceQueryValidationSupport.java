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
package org.hopper.edw.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceQueryGenerationSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceQuerySqlGenerator;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlEngine;

/** Structural and SQL-generation checks for a {@link SourceQuery}. */
public final class SourceQueryValidationSupport {

  private static final Class<?> PKG = SourceModel.class;

  private SourceQueryValidationSupport() {}

  public static List<ICheckResult> check(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (query == null) {
      return remarks;
    }
    String queryName = nvl(query.getName());
    if (Utils.isEmpty(query.getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.QueryMissingName"),
              null));
      queryName = "?";
    }

    SourceQueryGenerationMode mode = query.resolveGenerationMode();
    if (mode == SourceQueryGenerationMode.FREE_SQL) {
      return checkFreeSql(model, query, queryName, variables, metadataProvider, remarks);
    }

    if (model == null || model.findTable(query.getDrivingTableName()) == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "SourceModel.CheckResult.QueryMissingDrivingTable",
                  queryName,
                  nvl(query.getDrivingTableName())),
              null));
    }
    if (query.getColumns().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.QueryEmptyProjection", queryName),
              null));
    }

    Set<Integer> keyPositions = new HashSet<>();
    boolean hasLogicalKey = false;
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null) {
        continue;
      }
      if (Utils.isEmpty(column.getColumnName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "SourceModel.CheckResult.QueryColumnMissingName", queryName),
                null));
        continue;
      }
      String tableName = column.getTableName();
      if (Utils.isEmpty(tableName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.QueryColumnMissingTable",
                    queryName,
                    column.getColumnName()),
                null));
      } else if (model != null) {
        SourceTable table = model.findTable(tableName);
        if (table == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.QueryColumnUnknownTable",
                      queryName,
                      column.getColumnName(),
                      tableName),
                  null));
        } else if (!table.getColumns().isEmpty() && !hasColumn(table, column.getColumnName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.QueryColumnNotOnTable",
                      queryName,
                      column.getColumnName(),
                      tableName),
                  null));
        }
      }
      if (column.isPrimaryKey()) {
        hasLogicalKey = true;
        int position = column.getPrimaryKeyPosition();
        if (!keyPositions.add(position)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.QueryDuplicateKeyPosition",
                      queryName,
                      Integer.toString(position)),
                  null));
        }
      }
    }
    if (!query.getColumns().isEmpty() && !hasLogicalKey) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.QueryMissingLogicalKey", queryName),
              null));
    }

    for (SourceQueryJoin join : query.getJoins()) {
      if (join == null) {
        continue;
      }
      if (model == null || model.findTable(join.getTableName()) == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.QueryJoinMissingTable",
                    queryName,
                    nvl(join.getTableName())),
                null));
      }
    }

    // SQL generation (structural) when single-connection SQL is expected.
    if (model != null
        && SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, query)
        && !query.getColumns().isEmpty()) {
      try {
        DatabaseMeta databaseMeta = resolveDatabaseMeta(model, query, variables, metadataProvider);
        SourceQuerySqlGenerator.generate(model, query, databaseMeta, variables);
      } catch (Exception e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "SourceModel.CheckResult.QuerySqlGenerationFailed",
                    queryName,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                null));
      }
    } else if (model != null
        && !query.getColumns().isEmpty()
        && query.resolveGenerationMode() != SourceQueryGenerationMode.PIPELINE
        && !SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, query)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.QueryCannotGenerateSql", queryName),
              null));
    }
    return remarks;
  }

  private static List<ICheckResult> checkFreeSql(
      SourceModel model,
      SourceQuery query,
      String queryName,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      List<ICheckResult> remarks) {
    if (Utils.isEmpty(query.getFreeSql())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Query '" + queryName + "' is Free SQL mode but free SQL text is empty",
              null));
      return remarks;
    }
    if (model == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Query '" + queryName + "' Free SQL requires a source model",
              null));
      return remarks;
    }
    try {
      // Parse / Calcite validate against the model schema (no RDBMS metadata required).
      SourceModelSqlEngine.validate(model, query.getFreeSql(), variables);
      if (metadataProvider != null) {
        try {
          // Full pipeline generation needs named connections in the metadata provider.
          SourceModelSqlEngine.plan(model, query.getFreeSql(), variables, metadataProvider);
        } catch (Exception genEx) {
          String msg = genEx.getMessage() != null ? genEx.getMessage() : genEx.toString();
          if (msg != null
              && msg.toLowerCase(Locale.ROOT).contains("connection")
              && msg.contains("not found")) {
            // Offline / incomplete project metadata: SQL is still structurally valid.
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_WARNING,
                    "Free SQL for query '"
                        + queryName
                        + "' parsed successfully, but pipeline generation needs database metadata: "
                        + msg,
                    null));
            return remarks;
          }
          throw genEx;
        }
      }
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              "Free SQL for query '" + queryName + "' validated successfully",
              null));
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Free SQL validation failed for query '"
                  + queryName
                  + "': "
                  + (e.getMessage() != null ? e.getMessage() : e.toString()),
              null));
    }
    return remarks;
  }

  private static boolean hasColumn(SourceTable table, String columnName) {
    if (table == null || Utils.isEmpty(columnName)) {
      return false;
    }
    String want = columnName.trim().toLowerCase(Locale.ROOT);
    for (SourceColumn column : table.getColumns()) {
      if (column != null
          && !Utils.isEmpty(column.getName())
          && want.equals(column.getName().trim().toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private static DatabaseMeta resolveDatabaseMeta(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (metadataProvider == null || model == null || query == null) {
      return null;
    }
    try {
      SourceTable driving = model.findTable(query.getDrivingTableName());
      if (driving == null) {
        return null;
      }
      String connection =
          org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceTablePreviewSupport
              .resolveConnectionName(model, driving, variables);
      if (Utils.isEmpty(connection)) {
        return null;
      }
      return metadataProvider.getSerializer(DatabaseMeta.class).load(connection);
    } catch (Exception e) {
      return null;
    }
  }

  private static String nvl(String value) {
    return Utils.isEmpty(value) ? "?" : value;
  }
}
