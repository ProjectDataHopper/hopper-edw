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
package org.apache.hop.hsm.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal DatabaseMetaData backed by hop-hsm servlet metadata actions. */
public class HopHsmJdbcDatabaseMetaData implements DatabaseMetaData {

  private final HopHsmJdbcConnection connection;

  HopHsmJdbcDatabaseMetaData(HopHsmJdbcConnection connection) {
    this.connection = connection;
  }

  @Override
  public boolean allProceduresAreCallable() {
    return false;
  }

  @Override
  public boolean allTablesAreSelectable() {
    return true;
  }

  @Override
  public String getURL() {
    String base = connection.config().endpointUrl();
    String schema = connection.getSchema();
    if (schema != null && !schema.isEmpty()) {
      return base + "?" + HsmProtocol.PARAM_SCHEMA + "=" + schema;
    }
    return base;
  }

  @Override
  public String getUserName() {
    return connection.config().user() != null ? connection.config().user() : "";
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public boolean nullsAreSortedHigh() {
    return false;
  }

  @Override
  public boolean nullsAreSortedLow() {
    return true;
  }

  @Override
  public boolean nullsAreSortedAtStart() {
    return false;
  }

  @Override
  public boolean nullsAreSortedAtEnd() {
    return false;
  }

  @Override
  public String getDatabaseProductName() {
    return "Hop Source Model (hop-hsm remote)";
  }

  @Override
  public String getDatabaseProductVersion() {
    return "1.0";
  }

  @Override
  public String getDriverName() {
    return HopHsmJdbcDriver.class.getName();
  }

  @Override
  public String getDriverVersion() {
    return "1.0";
  }

  @Override
  public int getDriverMajorVersion() {
    return 1;
  }

  @Override
  public int getDriverMinorVersion() {
    return 0;
  }

  @Override
  public boolean usesLocalFiles() {
    return false;
  }

  @Override
  public boolean usesLocalFilePerTable() {
    return false;
  }

  @Override
  public boolean supportsMixedCaseIdentifiers() {
    return true;
  }

  @Override
  public boolean storesUpperCaseIdentifiers() {
    return false;
  }

  @Override
  public boolean storesLowerCaseIdentifiers() {
    return false;
  }

  @Override
  public boolean storesMixedCaseIdentifiers() {
    return true;
  }

  @Override
  public boolean supportsMixedCaseQuotedIdentifiers() {
    return true;
  }

  @Override
  public boolean storesUpperCaseQuotedIdentifiers() {
    return false;
  }

  @Override
  public boolean storesLowerCaseQuotedIdentifiers() {
    return false;
  }

  @Override
  public boolean storesMixedCaseQuotedIdentifiers() {
    return true;
  }

  @Override
  public String getIdentifierQuoteString() {
    return "\"";
  }

  @Override
  public String getSQLKeywords() {
    return "";
  }

  @Override
  public String getNumericFunctions() {
    return "";
  }

  @Override
  public String getStringFunctions() {
    return "";
  }

  @Override
  public String getSystemFunctions() {
    return "";
  }

  @Override
  public String getTimeDateFunctions() {
    return "";
  }

  @Override
  public String getSearchStringEscape() {
    return "\\";
  }

  @Override
  public String getExtraNameCharacters() {
    return "";
  }

  @Override
  public boolean supportsAlterTableWithAddColumn() {
    return false;
  }

  @Override
  public boolean supportsAlterTableWithDropColumn() {
    return false;
  }

  @Override
  public boolean supportsColumnAliasing() {
    return true;
  }

  @Override
  public boolean nullPlusNonNullIsNull() {
    return true;
  }

  @Override
  public boolean supportsConvert() {
    return false;
  }

  @Override
  public boolean supportsConvert(int fromType, int toType) {
    return false;
  }

  @Override
  public boolean supportsTableCorrelationNames() {
    return true;
  }

  @Override
  public boolean supportsDifferentTableCorrelationNames() {
    return true;
  }

  @Override
  public boolean supportsExpressionsInOrderBy() {
    return true;
  }

  @Override
  public boolean supportsOrderByUnrelated() {
    return true;
  }

  @Override
  public boolean supportsGroupBy() {
    return true;
  }

  @Override
  public boolean supportsGroupByUnrelated() {
    return true;
  }

  @Override
  public boolean supportsGroupByBeyondSelect() {
    return true;
  }

  @Override
  public boolean supportsLikeEscapeClause() {
    return true;
  }

  @Override
  public boolean supportsMultipleResultSets() {
    return false;
  }

  @Override
  public boolean supportsMultipleTransactions() {
    return false;
  }

  @Override
  public boolean supportsNonNullableColumns() {
    return true;
  }

  @Override
  public boolean supportsMinimumSQLGrammar() {
    return true;
  }

  @Override
  public boolean supportsCoreSQLGrammar() {
    return true;
  }

  @Override
  public boolean supportsExtendedSQLGrammar() {
    return false;
  }

  @Override
  public boolean supportsANSI92EntryLevelSQL() {
    return true;
  }

  @Override
  public boolean supportsANSI92IntermediateSQL() {
    return false;
  }

  @Override
  public boolean supportsANSI92FullSQL() {
    return false;
  }

  @Override
  public boolean supportsIntegrityEnhancementFacility() {
    return false;
  }

  @Override
  public boolean supportsOuterJoins() {
    return true;
  }

  @Override
  public boolean supportsFullOuterJoins() {
    return true;
  }

  @Override
  public boolean supportsLimitedOuterJoins() {
    return true;
  }

  @Override
  public String getSchemaTerm() {
    return "schema";
  }

  @Override
  public String getProcedureTerm() {
    return "procedure";
  }

  @Override
  public String getCatalogTerm() {
    return "catalog";
  }

  @Override
  public boolean isCatalogAtStart() {
    return true;
  }

  @Override
  public String getCatalogSeparator() {
    return ".";
  }

  @Override
  public boolean supportsSchemasInDataManipulation() {
    // Schema = Source model service; DBeaver uses active schema for bare table names.
    return true;
  }

  @Override
  public boolean supportsSchemasInProcedureCalls() {
    return false;
  }

  @Override
  public boolean supportsSchemasInTableDefinitions() {
    return false;
  }

  @Override
  public boolean supportsSchemasInIndexDefinitions() {
    return false;
  }

  @Override
  public boolean supportsSchemasInPrivilegeDefinitions() {
    return false;
  }

  @Override
  public boolean supportsCatalogsInDataManipulation() {
    return false;
  }

  @Override
  public boolean supportsCatalogsInProcedureCalls() {
    return false;
  }

  @Override
  public boolean supportsCatalogsInTableDefinitions() {
    return false;
  }

  @Override
  public boolean supportsCatalogsInIndexDefinitions() {
    return false;
  }

  @Override
  public boolean supportsCatalogsInPrivilegeDefinitions() {
    return false;
  }

  @Override
  public boolean supportsPositionedDelete() {
    return false;
  }

  @Override
  public boolean supportsPositionedUpdate() {
    return false;
  }

  @Override
  public boolean supportsSelectForUpdate() {
    return false;
  }

  @Override
  public boolean supportsStoredProcedures() {
    return false;
  }

  @Override
  public boolean supportsSubqueriesInComparisons() {
    return true;
  }

  @Override
  public boolean supportsSubqueriesInExists() {
    return true;
  }

  @Override
  public boolean supportsSubqueriesInIns() {
    return true;
  }

  @Override
  public boolean supportsSubqueriesInQuantifieds() {
    return true;
  }

  @Override
  public boolean supportsCorrelatedSubqueries() {
    return true;
  }

  @Override
  public boolean supportsUnion() {
    return false;
  }

  @Override
  public boolean supportsUnionAll() {
    return false;
  }

  @Override
  public boolean supportsOpenCursorsAcrossCommit() {
    return false;
  }

  @Override
  public boolean supportsOpenCursorsAcrossRollback() {
    return false;
  }

  @Override
  public boolean supportsOpenStatementsAcrossCommit() {
    return false;
  }

  @Override
  public boolean supportsOpenStatementsAcrossRollback() {
    return false;
  }

  @Override
  public int getMaxBinaryLiteralLength() {
    return 0;
  }

  @Override
  public int getMaxCharLiteralLength() {
    return 0;
  }

  @Override
  public int getMaxColumnNameLength() {
    return 128;
  }

  @Override
  public int getMaxColumnsInGroupBy() {
    return 0;
  }

  @Override
  public int getMaxColumnsInIndex() {
    return 0;
  }

  @Override
  public int getMaxColumnsInOrderBy() {
    return 0;
  }

  @Override
  public int getMaxColumnsInSelect() {
    return 0;
  }

  @Override
  public int getMaxColumnsInTable() {
    return 0;
  }

  @Override
  public int getMaxConnections() {
    return 0;
  }

  @Override
  public int getMaxCursorNameLength() {
    return 0;
  }

  @Override
  public int getMaxIndexLength() {
    return 0;
  }

  @Override
  public int getMaxSchemaNameLength() {
    return 128;
  }

  @Override
  public int getMaxProcedureNameLength() {
    return 0;
  }

  @Override
  public int getMaxCatalogNameLength() {
    return 128;
  }

  @Override
  public int getMaxRowSize() {
    return 0;
  }

  @Override
  public boolean doesMaxRowSizeIncludeBlobs() {
    return false;
  }

  @Override
  public int getMaxStatementLength() {
    return 0;
  }

  @Override
  public int getMaxStatements() {
    return 0;
  }

  @Override
  public int getMaxTableNameLength() {
    return 128;
  }

  @Override
  public int getMaxTablesInSelect() {
    return 0;
  }

  @Override
  public int getMaxUserNameLength() {
    return 0;
  }

  @Override
  public int getDefaultTransactionIsolation() {
    return Connection.TRANSACTION_NONE;
  }

  @Override
  public boolean supportsTransactions() {
    return false;
  }

  @Override
  public boolean supportsTransactionIsolationLevel(int level) {
    return level == Connection.TRANSACTION_NONE;
  }

  @Override
  public boolean supportsDataDefinitionAndDataManipulationTransactions() {
    return false;
  }

  @Override
  public boolean supportsDataManipulationTransactionsOnly() {
    return false;
  }

  @Override
  public boolean dataDefinitionCausesTransactionCommit() {
    return false;
  }

  @Override
  public boolean dataDefinitionIgnoredInTransactions() {
    return true;
  }

  @Override
  public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getProcedureColumns(
      String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getTables(
      String catalog, String schemaPattern, String tableNamePattern, String[] types)
      throws SQLException {
    if (!matchCatalogPattern(catalog)) {
      return emptyTables();
    }
    // Prefer exact schema from pattern; null/% → all services (or current schema if set).
    String schemaArg = schemaForRequest(schemaPattern);
    Map<String, Object> resp = connection.http().tables(schemaArg);
    List<Object> tables = HsmJson.asArray(resp.get("tables"));
    List<Object[]> rows = new ArrayList<>();
    if (tables != null) {
      for (Object o : tables) {
        Map<String, Object> m = HsmJson.asObject(o);
        if (m == null) {
          continue;
        }
        String schema = HsmJson.str(m, "schema");
        if (schema == null || schema.isEmpty()) {
          schema = schemaArg; // legacy single-service response
        }
        if (!match(schemaPattern, schema)) {
          continue;
        }
        String name = HsmJson.str(m, "n");
        String type = normalizeTableType(HsmJson.str(m, "type"));
        if (!match(tableNamePattern, name)) {
          continue;
        }
        if (types != null && types.length > 0) {
          boolean ok = false;
          for (String t : types) {
            if (t != null && t.equalsIgnoreCase(type)) {
              ok = true;
              break;
            }
          }
          if (!ok) {
            continue;
          }
        }
        rows.add(
            new Object[] {
              null, schema, name, type, HsmJson.str(m, "remarks"), null, null, null, null, null
            });
      }
    }
    return HopHsmJdbcResultSet.of(
        null,
        new String[] {
          "TABLE_CAT",
          "TABLE_SCHEM",
          "TABLE_NAME",
          "TABLE_TYPE",
          "REMARKS",
          "TYPE_CAT",
          "TYPE_SCHEM",
          "TYPE_NAME",
          "SELF_REFERENCING_COL_NAME",
          "REF_GENERATION"
        },
        new int[] {
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR
        },
        rows);
  }

  @Override
  public ResultSet getSchemas() throws SQLException {
    Map<String, Object> resp = connection.http().schemas();
    List<Object> schemas = HsmJson.asArray(resp.get("schemas"));
    List<Object[]> rows = new ArrayList<>();
    if (schemas != null) {
      for (Object o : schemas) {
        // New format: {n, remarks}; legacy: plain string
        if (o instanceof String s) {
          rows.add(new Object[] {s, null});
          continue;
        }
        Map<String, Object> m = HsmJson.asObject(o);
        if (m == null) {
          continue;
        }
        String name = HsmJson.str(m, "n");
        if (name != null) {
          rows.add(new Object[] {name, null});
        }
      }
    }
    return HopHsmJdbcResultSet.of(
        null,
        new String[] {"TABLE_SCHEM", "TABLE_CATALOG"},
        new int[] {Types.VARCHAR, Types.VARCHAR},
        rows);
  }

  @Override
  public ResultSet getCatalogs() throws SQLException {
    return HopHsmJdbcResultSet.of(
        null, new String[] {"TABLE_CAT"}, new int[] {Types.VARCHAR}, List.of());
  }

  @Override
  public ResultSet getTableTypes() throws SQLException {
    List<Object[]> rows = new ArrayList<>();
    for (String t : new String[] {"TABLE", "VIEW"}) {
      rows.add(new Object[] {t});
    }
    return HopHsmJdbcResultSet.of(
        null, new String[] {"TABLE_TYPE"}, new int[] {Types.VARCHAR}, rows);
  }

  @Override
  public ResultSet getColumns(
      String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
      throws SQLException {
    if (!matchCatalogPattern(catalog)) {
      return empty();
    }
    String schemaArg = schemaForRequest(schemaPattern);
    Map<String, Object> resp =
        connection.http().columns(schemaArg, tableNamePattern != null ? tableNamePattern : "%");
    List<Object> cols = HsmJson.asArray(resp.get("columns"));
    List<Object[]> rows = new ArrayList<>();
    if (cols != null) {
      for (Object o : cols) {
        Map<String, Object> m = HsmJson.asObject(o);
        if (m == null) {
          continue;
        }
        String schema = HsmJson.str(m, "schema");
        if (schema == null || schema.isEmpty()) {
          schema = schemaArg;
        }
        if (!match(schemaPattern, schema)) {
          continue;
        }
        String table = HsmJson.str(m, "table");
        String name = HsmJson.str(m, "n");
        if (!match(tableNamePattern, table) || !match(columnNamePattern, name)) {
          continue;
        }
        int sqlType = HsmJson.integer(m, "j", Types.VARCHAR);
        String typeName = HsmJson.str(m, "t");
        int pos = HsmJson.integer(m, "pos", 0);
        rows.add(
            new Object[] {
              null,
              schema,
              table,
              name,
              (long) sqlType,
              typeName,
              0L,
              null,
              0L,
              10L,
              (long) columnNullable,
              null,
              null,
              0L,
              null,
              0L,
              (long) pos,
              "YES",
              null,
              null,
              null,
              null,
              "NO",
              "NO"
            });
      }
    }
    return HopHsmJdbcResultSet.of(
        null,
        new String[] {
          "TABLE_CAT",
          "TABLE_SCHEM",
          "TABLE_NAME",
          "COLUMN_NAME",
          "DATA_TYPE",
          "TYPE_NAME",
          "COLUMN_SIZE",
          "BUFFER_LENGTH",
          "DECIMAL_DIGITS",
          "NUM_PREC_RADIX",
          "NULLABLE",
          "REMARKS",
          "COLUMN_DEF",
          "SQL_DATA_TYPE",
          "SQL_DATETIME_SUB",
          "CHAR_OCTET_LENGTH",
          "ORDINAL_POSITION",
          "IS_NULLABLE",
          "SCOPE_CATALOG",
          "SCOPE_SCHEMA",
          "SCOPE_TABLE",
          "SOURCE_DATA_TYPE",
          "IS_AUTOINCREMENT",
          "IS_GENERATEDCOLUMN"
        },
        intArray(
            Types.VARCHAR,
            Types.VARCHAR,
            Types.VARCHAR,
            Types.VARCHAR,
            Types.BIGINT,
            Types.VARCHAR,
            Types.BIGINT,
            Types.BIGINT,
            Types.BIGINT,
            Types.BIGINT,
            Types.BIGINT,
            Types.VARCHAR,
            Types.VARCHAR,
            Types.BIGINT,
            Types.BIGINT,
            Types.BIGINT,
            Types.BIGINT,
            Types.VARCHAR,
            Types.VARCHAR,
            Types.VARCHAR,
            Types.VARCHAR,
            Types.BIGINT,
            Types.VARCHAR,
            Types.VARCHAR),
        rows);
  }

  private static int[] intArray(int... v) {
    return v;
  }

  @Override
  public ResultSet getColumnPrivileges(
      String catalog, String schema, String table, String columnNamePattern) throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getBestRowIdentifier(
      String catalog, String schema, String table, int scope, boolean nullable)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getVersionColumns(String catalog, String schema, String table)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
    String schemaArg = schema != null && !schema.isEmpty() ? schema : connection.getSchema();
    Map<String, Object> resp = connection.http().columns(schemaArg, table);
    List<Object> cols = HsmJson.asArray(resp.get("columns"));
    List<Object[]> rows = new ArrayList<>();
    short seq = 1;
    if (cols != null) {
      for (Object o : cols) {
        Map<String, Object> m = HsmJson.asObject(o);
        if (m == null) {
          continue;
        }
        if (!HsmJson.bool(m, "pk", false)) {
          continue;
        }
        String sch = HsmJson.str(m, "schema");
        if (sch == null || sch.isEmpty()) {
          sch = schemaArg;
        }
        rows.add(
            new Object[] {
              null, sch, HsmJson.str(m, "table"), HsmJson.str(m, "n"), (long) seq++, "PRIMARY"
            });
      }
    }
    return HopHsmJdbcResultSet.of(
        null,
        new String[] {
          "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME"
        },
        new int[] {
          Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.VARCHAR
        },
        rows);
  }

  @Override
  public ResultSet getImportedKeys(String catalog, String schema, String table)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getExportedKeys(String catalog, String schema, String table)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getCrossReference(
      String parentCatalog,
      String parentSchema,
      String parentTable,
      String foreignCatalog,
      String foreignSchema,
      String foreignTable)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getTypeInfo() throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getIndexInfo(
      String catalog, String schema, String table, boolean unique, boolean approximate)
      throws SQLException {
    return empty();
  }

  @Override
  public boolean supportsResultSetType(int type) {
    return type == ResultSet.TYPE_FORWARD_ONLY || type == ResultSet.TYPE_SCROLL_INSENSITIVE;
  }

  @Override
  public boolean supportsResultSetConcurrency(int type, int concurrency) {
    return concurrency == ResultSet.CONCUR_READ_ONLY;
  }

  @Override
  public boolean ownUpdatesAreVisible(int type) {
    return false;
  }

  @Override
  public boolean ownDeletesAreVisible(int type) {
    return false;
  }

  @Override
  public boolean ownInsertsAreVisible(int type) {
    return false;
  }

  @Override
  public boolean othersUpdatesAreVisible(int type) {
    return false;
  }

  @Override
  public boolean othersDeletesAreVisible(int type) {
    return false;
  }

  @Override
  public boolean othersInsertsAreVisible(int type) {
    return false;
  }

  @Override
  public boolean updatesAreDetected(int type) {
    return false;
  }

  @Override
  public boolean deletesAreDetected(int type) {
    return false;
  }

  @Override
  public boolean insertsAreDetected(int type) {
    return false;
  }

  @Override
  public boolean supportsBatchUpdates() {
    return false;
  }

  @Override
  public ResultSet getUDTs(
      String catalog, String schemaPattern, String typeNamePattern, int[] types)
      throws SQLException {
    return empty();
  }

  @Override
  public Connection getConnection() {
    return connection;
  }

  @Override
  public boolean supportsSavepoints() {
    return false;
  }

  @Override
  public boolean supportsNamedParameters() {
    return false;
  }

  @Override
  public boolean supportsMultipleOpenResults() {
    return false;
  }

  @Override
  public boolean supportsGetGeneratedKeys() {
    return false;
  }

  @Override
  public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getAttributes(
      String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public boolean supportsResultSetHoldability(int holdability) {
    return holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT;
  }

  @Override
  public int getResultSetHoldability() {
    return ResultSet.HOLD_CURSORS_OVER_COMMIT;
  }

  @Override
  public int getDatabaseMajorVersion() {
    return 1;
  }

  @Override
  public int getDatabaseMinorVersion() {
    return 0;
  }

  @Override
  public int getJDBCMajorVersion() {
    return 4;
  }

  @Override
  public int getJDBCMinorVersion() {
    return 2;
  }

  @Override
  public int getSQLStateType() {
    return sqlStateSQL;
  }

  @Override
  public boolean locatorsUpdateCopy() {
    return false;
  }

  @Override
  public boolean supportsStatementPooling() {
    return false;
  }

  @Override
  public RowIdLifetime getRowIdLifetime() {
    return RowIdLifetime.ROWID_UNSUPPORTED;
  }

  @Override
  public boolean supportsStoredFunctionsUsingCallSyntax() {
    return false;
  }

  @Override
  public boolean autoCommitFailureClosesAllResultSets() {
    return false;
  }

  @Override
  public ResultSet getClientInfoProperties() throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getFunctionColumns(
      String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public ResultSet getPseudoColumns(
      String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
      throws SQLException {
    return empty();
  }

  @Override
  public boolean generatedKeyAlwaysReturned() {
    return false;
  }

  @Override
  public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
    return getSchemas();
  }

  @Override
  public boolean supportsRefCursors() {
    return false;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    throw new SQLException("Not a wrapper for " + iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return iface.isInstance(this);
  }

  private static ResultSet empty() {
    return HopHsmJdbcResultSet.of(null, new String[0], new int[0], List.of());
  }

  private static ResultSet emptyTables() {
    return HopHsmJdbcResultSet.of(
        null,
        new String[] {
          "TABLE_CAT",
          "TABLE_SCHEM",
          "TABLE_NAME",
          "TABLE_TYPE",
          "REMARKS",
          "TYPE_CAT",
          "TYPE_SCHEM",
          "TYPE_NAME",
          "SELF_REFERENCING_COL_NAME",
          "REF_GENERATION"
        },
        new int[] {
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR
        },
        List.of());
  }

  /**
   * JDBC-standard type for tools that only load TABLE/VIEW into the SQL dictionary (DBeaver).
   * Legacy servers may still send JSON/PIPELINE.
   */
  static String normalizeTableType(String type) {
    if (type == null || type.isEmpty()) {
      return "TABLE";
    }
    if ("JSON".equalsIgnoreCase(type) || "PIPELINE".equalsIgnoreCase(type)) {
      return "VIEW";
    }
    return type;
  }

  /**
   * Schema argument for the HTTP API: exact service name, or {@code null} to list all services.
   * JDBC {@code null} / {@code %} schema pattern means “do not narrow by schema”.
   */
  private String schemaForRequest(String schemaPattern) {
    if (schemaPattern == null || "%".equals(schemaPattern) || "*".equals(schemaPattern)) {
      return null; // all Source model services
    }
    if (schemaPattern.isEmpty()) {
      // JDBC: tables without a schema — fall back to connection default
      return connection.getSchema();
    }
    if (schemaPattern.indexOf('%') < 0
        && schemaPattern.indexOf('*') < 0
        && schemaPattern.indexOf('_') < 0) {
      return schemaPattern;
    }
    return null;
  }

  static boolean matchCatalogPattern(String catalog) {
    return catalog == null || catalog.isEmpty() || "%".equals(catalog) || "*".equals(catalog);
  }

  private static boolean match(String pattern, String value) {
    if (pattern == null || pattern.isEmpty() || "%".equals(pattern) || "*".equals(pattern)) {
      return true;
    }
    if (value == null) {
      return false;
    }
    boolean hasWildcard = pattern.indexOf('%') >= 0 || pattern.indexOf('*') >= 0;
    if (!hasWildcard && pattern.indexOf('_') < 0) {
      return value.equalsIgnoreCase(pattern);
    }
    String regex =
        pattern.replace(".", "\\.").replace("%", ".*").replace("_", ".").replace("*", ".*");
    return value.toLowerCase(Locale.ROOT).matches(regex.toLowerCase(Locale.ROOT));
  }
}
