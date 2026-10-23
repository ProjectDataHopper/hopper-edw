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
package org.hopper.edw.datavault.virtualization.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.service.DimensionalMaskingPolicy;
import org.hopper.edw.datavault.metadata.dimensional.service.DimensionalModelService;

/** Pushes a dimensional SELECT to the warehouse, masks flagged cells, then writes the audit row. */
public final class DimensionalQueryExecutor {

  private DimensionalQueryExecutor() {}

  public record QueryResult(List<RowMetaAndData> rows, boolean truncated, DimensionalSqlPlan plan) {}

  /**
   * Runs a statement for the modeler preview. No audit row is written. Masking uses the Safe
   * policy unless {@code policy} is set.
   */
  public static QueryResult preview(
      DimensionalModel model,
      String sql,
      DimensionalMaskingPolicy policy,
      int rowLimit,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    DimensionalJdbcCatalog catalog =
        DimensionalJdbcCatalog.build(model, variables, metadataProvider, model != null ? model.getName() : null);
    return executePlan(catalog, sql, policy, rowLimit, 60, List.of(), variables, metadataProvider);
  }

  public static QueryResult query(
      DimensionalModelService service,
      DimensionalModel model,
      String sql,
      int requestedRowLimit,
      List<DimensionalQueryParameter> parameters,
      String principal,
      String clientInfo,
      String remoteAddr,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    long started = System.currentTimeMillis();
    DimensionalJdbcCatalog catalog =
        DimensionalJdbcCatalog.build(model, variables, metadataProvider, service.getName());
    int limit = service.resolveRowLimit(requestedRowLimit);
    String status = DimensionalAuditEvent.ERROR;
    String message = null;
    String rewritten = "";
    long rowCount = 0;
    String maskedColumns = "";
    DimensionalSqlPlan plan = null;
    QueryResult result = null;
    boolean audited = false;
    try {
      result =
          executePlan(
              catalog,
              sql,
              service.getMaskingPolicyOrDefault(),
              limit,
              service.getQueryTimeoutSeconds(),
              parameters,
              variables,
              metadataProvider);
      plan = result.plan();
      rewritten = plan.sql();
      rowCount = result.rows().size();
      maskedColumns = maskedColumnList(result.rows(), plan);
      status = DimensionalAuditEvent.OK;
      writeAudit(
          service,
          variables,
          metadataProvider,
          event(
              service,
              principal,
              clientInfo,
              remoteAddr,
              sql,
              rewritten,
              parameters,
              status,
              null,
              rowCount,
              started,
              maskedColumns));
      audited = true;
      return result;
    } catch (Exception e) {
      DimensionalSqlException rejected = rejectionOf(e);
      if (rejected != null) {
        status = DimensionalAuditEvent.REJECTED;
        message = rejected.getMessage();
      } else {
        message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      }
      if (!audited && service.isAuditEnabled()) {
        try {
          writeAudit(
              service,
              variables,
              metadataProvider,
              event(
                  service,
                  principal,
                  clientInfo,
                  remoteAddr,
                  sql,
                  rewritten,
                  parameters,
                  status,
                  message,
                  0,
                  started,
                  maskedColumns));
        } catch (HopException auditFailure) {
          throw auditFailure;
        }
      }
      if (e instanceof HopException hop) {
        throw hop;
      }
      throw new HopException(message, e);
    }
  }

  private static DimensionalSqlException rejectionOf(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof DimensionalSqlException sql && sql.isRejected()) {
        return sql;
      }
      current = current.getCause();
    }
    return null;
  }

  private static void writeAudit(
      DimensionalModelService service,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DimensionalAuditEvent event)
      throws HopException {
    if (service == null || !service.isAuditEnabled()) {
      return;
    }
    DimensionalAuditSupport.insert(variables, metadataProvider, service, event);
  }

  private static DimensionalAuditEvent event(
      DimensionalModelService service,
      String principal,
      String clientInfo,
      String remoteAddr,
      String sql,
      String rewritten,
      List<DimensionalQueryParameter> parameters,
      String status,
      String message,
      long rowCount,
      long started,
      String maskedColumns) {
    return new DimensionalAuditEvent(
        principal,
        service.getName(),
        clientInfo,
        remoteAddr,
        sql,
        rewritten,
        parameters != null ? parameters.size() : 0,
        DimensionalAuditSupport.literalCount(sql),
        status,
        message,
        rowCount,
        System.currentTimeMillis() - started,
        maskedColumns);
  }

  private static QueryResult executePlan(
      DimensionalJdbcCatalog catalog,
      String sql,
      DimensionalMaskingPolicy policy,
      int rowLimit,
      int timeoutSeconds,
      List<DimensionalQueryParameter> parameters,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    DimensionalSqlPlan plan;
    try {
      plan =
          DimensionalSqlEngine.plan(
              catalog, sql, catalog.schemaName(), policy != null ? policy : DimensionalMaskingPolicy.SAFE);
    } catch (DimensionalSqlException e) {
      throw new HopException(e.getMessage(), e);
    }
    DimensionalResultMasker.requirePatterns(plan.outputPatterns(), metadataProvider);
    DatabaseMeta databaseMeta = catalog.targetDatabase();
    if (databaseMeta == null) {
      throw new HopException(
          "The dimensional model has no target database connection. Set it on the dimensional"
              + " configuration.");
    }
    List<DimensionalQueryParameter> params = parameters != null ? parameters : List.of();
    IRowMeta paramMeta = new RowMeta();
    Object[] data = new Object[params.size()];
    for (int i = 0; i < params.size(); i++) {
      DimensionalQueryParameter parameter = params.get(i);
      try {
        paramMeta.addValueMeta(ValueMetaFactory.createValueMeta("p" + (i + 1), parameter.hopType()));
        data[i] = parameter.hopValue();
      } catch (Exception e) {
        throw new HopException("Could not bind query parameter " + (i + 1), e);
      }
    }
    int fetch = rowLimit > 0 && rowLimit < Integer.MAX_VALUE ? rowLimit + 1 : rowLimit;
    List<RowMetaAndData> rows = new ArrayList<>();
    try (Database database =
        new Database(
            new SimpleLoggingObject("dimensional-model-service", LoggingObjectType.GENERAL, null),
            variables,
            databaseMeta)) {
      database.connect();
      if (fetch > 0) {
        database.setQueryLimit(fetch);
      }
      database.setStatementQueryTimeoutSeconds(Math.max(0, timeoutSeconds));
      java.sql.ResultSet resultSet = null;
      try {
        resultSet =
            database.openQuery(
                plan.sql(),
                paramMeta.isEmpty() ? null : paramMeta,
                paramMeta.isEmpty() ? null : data);
        if (resultSet != null) {
          Object[] row = database.getRow(resultSet);
          while (row != null) {
            rows.add(new RowMetaAndData(database.getReturnRowMeta(), row));
            row = database.getRow(resultSet);
          }
        }
      } finally {
        if (resultSet != null) {
          try {
            resultSet.close();
          } catch (java.sql.SQLException ignored) {
            // The connection close that follows releases the statement.
          }
        }
      }
    }
    boolean truncated = rowLimit > 0 && rows.size() > rowLimit;
    if (truncated) {
      rows = new ArrayList<>(rows.subList(0, rowLimit));
    }
    if (plan.masksAnyColumn()) {
      DimensionalResultMasker.apply(rows, plan.outputPatterns(), variables, metadataProvider);
    }
    return new QueryResult(rows, truncated, plan);
  }

  private static String maskedColumnList(List<RowMetaAndData> rows, DimensionalSqlPlan plan) {
    if (plan == null || plan.outputPatterns() == null || rows == null || rows.isEmpty()) {
      return "";
    }
    var rowMeta = rows.get(0).getRowMeta();
    if (rowMeta == null) {
      return "";
    }
    StringBuilder names = new StringBuilder();
    for (int i = 0; i < plan.outputPatterns().length && i < rowMeta.size(); i++) {
      if (plan.outputPatterns()[i] == null) {
        continue;
      }
      if (names.length() > 0) {
        names.append(',');
      }
      names.append(rowMeta.getValueMeta(i).getName());
    }
    return names.toString();
  }
}
