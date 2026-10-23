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

import java.sql.Timestamp;
import java.util.UUID;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.dimensional.service.DimensionalModelService;

/** Creates and writes the dimensional query audit table. Result cells are never stored. */
public final class DimensionalAuditSupport {

  private static final int MESSAGE_LIMIT = 2000;
  private static final int TEXT_LIMIT = 100_000;

  private DimensionalAuditSupport() {}

  public static void createTable(
      IVariables variables, IHopMetadataProvider metadataProvider, DimensionalModelService service)
      throws HopException {
    DatabaseMeta databaseMeta = auditDatabase(variables, metadataProvider, service);
    String schema = resolvedSchema(variables, service);
    String table = resolvedTable(variables, service);
    String tableName = databaseMeta.getQuotedSchemaTableCombination(variables, schema, table);
    try (Database database = open(variables, databaseMeta)) {
      if (database.checkTableExists(schema, table)) {
        throw new HopException("Audit table " + tableName + " already exists.");
      }
      String ddl =
          database.getCreateTableStatement(tableName, auditRowMeta(), null, false, "audit_id", true);
      database.execStatement(ddl);
    }
  }

  public static boolean tableExists(
      IVariables variables, IHopMetadataProvider metadataProvider, DimensionalModelService service)
      throws HopException {
    DatabaseMeta databaseMeta = auditDatabase(variables, metadataProvider, service);
    try (Database database = open(variables, databaseMeta)) {
      return database.checkTableExists(resolvedSchema(variables, service), resolvedTable(variables, service));
    }
  }

  public static void insert(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DimensionalModelService service,
      DimensionalAuditEvent event)
      throws HopException {
    if (service == null || !service.isAuditEnabled()) {
      return;
    }
    DatabaseMeta databaseMeta = auditDatabase(variables, metadataProvider, service);
    String tableName = qualifiedTable(databaseMeta, variables, service);
    IRowMeta rowMeta = auditRowMeta();
    Object[] data = new Object[rowMeta.size()];
    data[0] = UUID.randomUUID().toString();
    data[1] = new Timestamp(System.currentTimeMillis());
    data[2] = limit(event.principal(), 200);
    data[3] = limit(event.serviceName(), 200);
    data[4] = limit(event.clientInfo(), 500);
    data[5] = limit(event.remoteAddr(), 200);
    data[6] = limit(loggedSql(service, event.sqlText()), TEXT_LIMIT);
    data[7] = limit(loggedSql(service, event.sqlRewritten()), TEXT_LIMIT);
    data[8] = (long) event.parameterCount();
    data[9] = (long) event.literalCount();
    data[10] = limit(event.status(), 32);
    data[11] = limit(event.statusMessage(), MESSAGE_LIMIT);
    data[12] = event.rowCount();
    data[13] = event.durationMs();
    data[14] = limit(event.maskedColumns(), 4000);
    StringBuilder sql = new StringBuilder();
    sql.append("INSERT INTO ").append(tableName).append(" (");
    for (int i = 0; i < rowMeta.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append(databaseMeta.quoteField(rowMeta.getValueMeta(i).getName()));
    }
    sql.append(") VALUES (");
    for (int i = 0; i < rowMeta.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append('?');
    }
    sql.append(')');
    try (Database database = open(variables, databaseMeta)) {
      database.execStatement(sql.toString(), rowMeta, data);
    } catch (HopException e) {
      throw new HopException(
          "Dimensional query audit insert failed. The result was not returned.", e);
    }
  }

  public static String loggedSql(DimensionalModelService service, String sql) {
    if (sql == null) {
      return "";
    }
    if (service != null && service.isStoreSqlVerbatim()) {
      return sql;
    }
    return DimensionalSqlLiteralRedactor.redact(sql).sql();
  }

  public static int literalCount(String sql) {
    return DimensionalSqlLiteralRedactor.redact(sql).literalCount();
  }

  private static DatabaseMeta auditDatabase(
      IVariables variables, IHopMetadataProvider metadataProvider, DimensionalModelService service)
      throws HopException {
    if (service == null || Utils.isEmpty(service.getAuditConnection())) {
      throw new HopException("The dimensional model service has no audit connection.");
    }
    if (metadataProvider == null) {
      throw new HopException("Metadata provider is required to open the audit connection.");
    }
    DatabaseMeta databaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(service.getAuditConnection());
    if (databaseMeta == null) {
      throw new HopException(
          "Audit connection '" + service.getAuditConnection() + "' was not found.");
    }
    return databaseMeta;
  }

  static String qualifiedTable(
      DatabaseMeta databaseMeta, IVariables variables, DimensionalModelService service) {
    return databaseMeta.getQuotedSchemaTableCombination(
        variables, resolvedSchema(variables, service), resolvedTable(variables, service));
  }

  private static String resolvedSchema(IVariables variables, DimensionalModelService service) {
    if (service == null || Utils.isEmpty(service.getAuditSchema())) {
      return null;
    }
    return variables != null ? variables.resolve(service.getAuditSchema()) : service.getAuditSchema();
  }

  private static String resolvedTable(IVariables variables, DimensionalModelService service) {
    String table =
        service != null ? service.getAuditTableOrDefault() : DimensionalModelService.DEFAULT_AUDIT_TABLE;
    return variables != null ? variables.resolve(table) : table;
  }

  public static IRowMeta auditRowMeta() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(string("audit_id", 36));
    rowMeta.addValueMeta(new ValueMetaTimestamp("event_time"));
    rowMeta.addValueMeta(string("principal", 200));
    rowMeta.addValueMeta(string("service_name", 200));
    rowMeta.addValueMeta(string("client_info", 500));
    rowMeta.addValueMeta(string("remote_addr", 200));
    rowMeta.addValueMeta(string("sql_text", TEXT_LIMIT));
    rowMeta.addValueMeta(string("sql_rewritten", TEXT_LIMIT));
    rowMeta.addValueMeta(new ValueMetaInteger("parameter_count"));
    rowMeta.addValueMeta(new ValueMetaInteger("literal_count"));
    rowMeta.addValueMeta(string("status", 32));
    rowMeta.addValueMeta(string("status_message", MESSAGE_LIMIT));
    rowMeta.addValueMeta(new ValueMetaInteger("row_count"));
    rowMeta.addValueMeta(new ValueMetaInteger("duration_ms"));
    rowMeta.addValueMeta(string("masked_columns", 4000));
    return rowMeta;
  }

  private static ValueMetaString string(String name, int length) {
    ValueMetaString value = new ValueMetaString(name);
    value.setLength(length);
    return value;
  }

  private static Database open(IVariables variables, DatabaseMeta databaseMeta) throws HopException {
    Database database =
        new Database(
            new SimpleLoggingObject(
                "dimensional-query-audit", LoggingObjectType.GENERAL, null),
            variables,
            databaseMeta);
    database.connect();
    return database;
  }

  private static String limit(String value, int max) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.length() <= max) {
      return trimmed;
    }
    return trimmed.substring(0, max);
  }
}
