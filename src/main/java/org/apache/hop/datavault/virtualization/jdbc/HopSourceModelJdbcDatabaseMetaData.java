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
package org.apache.hop.datavault.virtualization.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/**
 * Minimal read-only {@link DatabaseMetaData} for hop-hsm. Exposes tables / queries / JSON /
 * pipelines as SCHEMA {@code source} tables.
 */
public class HopSourceModelJdbcDatabaseMetaData implements DatabaseMetaData {

  private final HopSourceModelJdbcConnection connection;

  public HopSourceModelJdbcDatabaseMetaData(HopSourceModelJdbcConnection connection) {
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
    SourceModel model = connection.model();
    String path = model != null ? model.getFilename() : null;
    return path != null
        ? HopSourceModelJdbcDriver.URL_PREFIX + "file=" + path
        : HopSourceModelJdbcDriver.URL_PREFIX;
  }

  @Override
  public String getUserName() {
    return "";
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
    return "Hop Source Model (hop-hsm)";
  }

  @Override
  public String getDatabaseProductVersion() {
    return "1.0";
  }

  @Override
  public String getDriverName() {
    return HopSourceModelJdbcDriver.class.getName();
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
    return true;
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
    // Flat model: bare table names (helps DBeaver SQL editor resolution).
    return false;
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
    return emptyResult(procedureMeta());
  }

  @Override
  public ResultSet getProcedureColumns(
      String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern)
      throws SQLException {
    return emptyResult(procedureColumnMeta());
  }

  @Override
  public ResultSet getTables(
      String catalog, String schemaPattern, String tableNamePattern, String[] types)
      throws SQLException {
    IRowMeta meta = tableMeta();
    List<RowMetaAndData> rows = new ArrayList<>();
    SourceModel model = connection.model();
    if (model != null) {
      for (TableEntry entry : listTables(model)) {
        if (!matchesPattern(tableNamePattern, entry.name())) {
          continue;
        }
        if (types != null && types.length > 0) {
          boolean ok = false;
          for (String t : types) {
            if (t != null && t.equalsIgnoreCase(entry.type())) {
              ok = true;
              break;
            }
          }
          if (!ok) {
            continue;
          }
        }
        if (!matchesSchemaPattern(schemaPattern)) {
          continue;
        }
        // Flat namespace (null catalog/schema) for SQL editor bare-name resolution.
        rows.add(
            row(
                meta,
                null,
                null,
                entry.name(),
                entry.type(),
                entry.remarks(),
                null,
                null,
                null,
                null,
                null));
      }
    }
    return new HopSourceModelJdbcResultSet(null, rows);
  }

  @Override
  public ResultSet getSchemas() throws SQLException {
    // Empty: tables have null TABLE_SCHEM (flat model for DBeaver / generic tools).
    return new HopSourceModelJdbcResultSet(null, List.of());
  }

  @Override
  public ResultSet getCatalogs() throws SQLException {
    return new HopSourceModelJdbcResultSet(null, List.of());
  }

  @Override
  public ResultSet getTableTypes() throws SQLException {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("TABLE_TYPE"));
    List<RowMetaAndData> rows = List.of(row(meta, "TABLE"), row(meta, "VIEW"));
    return new HopSourceModelJdbcResultSet(null, rows);
  }

  @Override
  public ResultSet getColumns(
      String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
      throws SQLException {
    IRowMeta meta = columnMeta();
    List<RowMetaAndData> rows = new ArrayList<>();
    SourceModel model = connection.model();
    if (model == null) {
      return new HopSourceModelJdbcResultSet(null, rows);
    }
    for (TableEntry entry : listTables(model)) {
      if (!matchesPattern(tableNamePattern, entry.name())) {
        continue;
      }
      if (!matchesSchemaPattern(schemaPattern)) {
        continue;
      }
      int pos = 1;
      for (ColumnEntry col : entry.columns()) {
        if (!matchesPattern(columnNamePattern, col.name())) {
          continue;
        }
        int sqlType = HopSourceModelJdbcResultSet.hopToSqlType(col.hopType());
        String typeName = HopSourceModelJdbcResultSet.hopToTypeName(col.hopType());
        rows.add(
            row(
                meta,
                null, // TABLE_CAT
                null, // TABLE_SCHEM (flat)
                entry.name(), // TABLE_NAME
                col.name(), // COLUMN_NAME
                (long) sqlType, // DATA_TYPE
                typeName, // TYPE_NAME
                0L, // COLUMN_SIZE
                null, // BUFFER_LENGTH
                0L, // DECIMAL_DIGITS
                10L, // NUM_PREC_RADIX
                (long) DatabaseMetaData.columnNullable, // NULLABLE
                null, // REMARKS
                null, // COLUMN_DEF
                0L, // SQL_DATA_TYPE
                null, // SQL_DATETIME_SUB
                0L, // CHAR_OCTET_LENGTH
                (long) pos, // ORDINAL_POSITION
                "YES", // IS_NULLABLE
                null, // SCOPE_CATALOG
                null, // SCOPE_SCHEMA
                null, // SCOPE_TABLE
                null, // SOURCE_DATA_TYPE
                "NO", // IS_AUTOINCREMENT
                "NO")); // IS_GENERATEDCOLUMN
        pos++;
      }
    }
    return new HopSourceModelJdbcResultSet(null, rows);
  }

  @Override
  public ResultSet getColumnPrivileges(
      String catalog, String schema, String table, String columnNamePattern) throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getBestRowIdentifier(
      String catalog, String schema, String table, int scope, boolean nullable)
      throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getVersionColumns(String catalog, String schema, String table)
      throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("TABLE_CAT"));
    meta.addValueMeta(new ValueMetaString("TABLE_SCHEM"));
    meta.addValueMeta(new ValueMetaString("TABLE_NAME"));
    meta.addValueMeta(new ValueMetaString("COLUMN_NAME"));
    meta.addValueMeta(new ValueMetaInteger("KEY_SEQ"));
    meta.addValueMeta(new ValueMetaString("PK_NAME"));
    List<RowMetaAndData> rows = new ArrayList<>();
    SourceModel model = connection.model();
    if (model != null && !Utils.isEmpty(table)) {
      for (SourceTable st : model.getTables()) {
        if (st == null || !table.equalsIgnoreCase(st.getName())) {
          continue;
        }
        short seq = 1;
        for (SourceColumn col : st.getColumns()) {
          if (col != null && col.isPrimaryKey()) {
            rows.add(row(meta, null, null, st.getName(), col.getName(), (long) seq, "PRIMARY"));
            seq++;
          }
        }
      }
    }
    return new HopSourceModelJdbcResultSet(null, rows);
  }

  @Override
  public ResultSet getImportedKeys(String catalog, String schema, String table)
      throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getExportedKeys(String catalog, String schema, String table)
      throws SQLException {
    return emptyResult(new RowMeta());
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
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getTypeInfo() throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getIndexInfo(
      String catalog, String schema, String table, boolean unique, boolean approximate)
      throws SQLException {
    return emptyResult(new RowMeta());
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
    return emptyResult(new RowMeta());
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
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getAttributes(
      String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern)
      throws SQLException {
    return emptyResult(new RowMeta());
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
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
      throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getFunctionColumns(
      String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern)
      throws SQLException {
    return emptyResult(new RowMeta());
  }

  @Override
  public ResultSet getPseudoColumns(
      String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
      throws SQLException {
    return emptyResult(new RowMeta());
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

  // --- helpers ---

  private static ResultSet emptyResult(IRowMeta meta) {
    return new HopSourceModelJdbcResultSet(null, List.of());
  }

  private static boolean matchesPattern(String pattern, String value) {
    if (pattern == null || pattern.isEmpty() || "%".equals(pattern) || "*".equals(pattern)) {
      return true;
    }
    if (value == null) {
      return false;
    }
    // Simple JDBC pattern: % and _
    String regex =
        pattern.replace(".", "\\.").replace("%", ".*").replace("_", ".").replace("*", ".*");
    return value.toLowerCase(Locale.ROOT).matches(regex.toLowerCase(Locale.ROOT));
  }

  /** Flat model: tables have null schema; accept any/empty/legacy "source". */
  private static boolean matchesSchemaPattern(String pattern) {
    if (pattern == null || "%".equals(pattern) || "*".equals(pattern) || pattern.isEmpty()) {
      return true;
    }
    return "source".equalsIgnoreCase(pattern);
  }

  private static List<TableEntry> listTables(SourceModel model) {
    List<TableEntry> list = new ArrayList<>();
    for (SourceTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      List<ColumnEntry> cols = new ArrayList<>();
      for (SourceColumn c : table.getColumns()) {
        if (c != null && !Utils.isEmpty(c.getName())) {
          cols.add(
              new ColumnEntry(
                  c.getName(), c.getHopType() > 0 ? c.getHopType() : IValueMeta.TYPE_STRING));
        }
      }
      // Standard JDBC types only (TABLE/VIEW) so tools like DBeaver SQL editor include them.
      list.add(new TableEntry(table.getName(), "TABLE", "Source table", cols));
    }
    for (SourceQuery query : model.getQueries()) {
      if (query == null || Utils.isEmpty(query.getName())) {
        continue;
      }
      List<ColumnEntry> cols = new ArrayList<>();
      for (SourceQueryColumn c : query.getColumns()) {
        if (c != null && !Utils.isEmpty(c.getColumnName())) {
          cols.add(new ColumnEntry(c.resolveAlias(), IValueMeta.TYPE_STRING));
        }
      }
      list.add(new TableEntry(query.getName(), "VIEW", "Source query (virtual table)", cols));
    }
    for (SourceJson json : model.getJsonSources()) {
      if (json == null || Utils.isEmpty(json.getName())) {
        continue;
      }
      List<ColumnEntry> cols = new ArrayList<>();
      // JSON field names if available via getFields()
      try {
        var fields = json.getFields();
        if (fields != null) {
          for (var f : fields) {
            if (f != null && !Utils.isEmpty(f.getName())) {
              cols.add(new ColumnEntry(f.getName(), IValueMeta.TYPE_STRING));
            }
          }
        }
      } catch (Throwable ignored) {
        // optional structure
      }
      list.add(
          new TableEntry(json.getName(), "VIEW", "Source JSON extraction (logical table)", cols));
    }
    for (SourcePipeline pipeline : model.getPipelineSources()) {
      if (pipeline == null || Utils.isEmpty(pipeline.getName())) {
        continue;
      }
      List<ColumnEntry> cols = new ArrayList<>();
      try {
        var fields = pipeline.getFields();
        if (fields != null) {
          for (var f : fields) {
            if (f != null && !Utils.isEmpty(f.getName())) {
              cols.add(new ColumnEntry(f.getName(), IValueMeta.TYPE_STRING));
            }
          }
        }
      } catch (Throwable ignored) {
        // optional
      }
      list.add(
          new TableEntry(pipeline.getName(), "VIEW", "Source pipeline feed (logical table)", cols));
    }
    return list;
  }

  private static IRowMeta tableMeta() {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("TABLE_CAT"));
    meta.addValueMeta(new ValueMetaString("TABLE_SCHEM"));
    meta.addValueMeta(new ValueMetaString("TABLE_NAME"));
    meta.addValueMeta(new ValueMetaString("TABLE_TYPE"));
    meta.addValueMeta(new ValueMetaString("REMARKS"));
    meta.addValueMeta(new ValueMetaString("TYPE_CAT"));
    meta.addValueMeta(new ValueMetaString("TYPE_SCHEM"));
    meta.addValueMeta(new ValueMetaString("TYPE_NAME"));
    meta.addValueMeta(new ValueMetaString("SELF_REFERENCING_COL_NAME"));
    meta.addValueMeta(new ValueMetaString("REF_GENERATION"));
    return meta;
  }

  private static IRowMeta schemaMeta() {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("TABLE_SCHEM"));
    meta.addValueMeta(new ValueMetaString("TABLE_CATALOG"));
    return meta;
  }

  private static IRowMeta catalogMeta() {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("TABLE_CAT"));
    return meta;
  }

  private static IRowMeta columnMeta() {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString("TABLE_CAT"));
    meta.addValueMeta(new ValueMetaString("TABLE_SCHEM"));
    meta.addValueMeta(new ValueMetaString("TABLE_NAME"));
    meta.addValueMeta(new ValueMetaString("COLUMN_NAME"));
    meta.addValueMeta(new ValueMetaInteger("DATA_TYPE"));
    meta.addValueMeta(new ValueMetaString("TYPE_NAME"));
    meta.addValueMeta(new ValueMetaInteger("COLUMN_SIZE"));
    meta.addValueMeta(new ValueMetaInteger("BUFFER_LENGTH"));
    meta.addValueMeta(new ValueMetaInteger("DECIMAL_DIGITS"));
    meta.addValueMeta(new ValueMetaInteger("NUM_PREC_RADIX"));
    meta.addValueMeta(new ValueMetaInteger("NULLABLE"));
    meta.addValueMeta(new ValueMetaString("REMARKS"));
    meta.addValueMeta(new ValueMetaString("COLUMN_DEF"));
    meta.addValueMeta(new ValueMetaInteger("SQL_DATA_TYPE"));
    meta.addValueMeta(new ValueMetaInteger("SQL_DATETIME_SUB"));
    meta.addValueMeta(new ValueMetaInteger("CHAR_OCTET_LENGTH"));
    meta.addValueMeta(new ValueMetaInteger("ORDINAL_POSITION"));
    meta.addValueMeta(new ValueMetaString("IS_NULLABLE"));
    meta.addValueMeta(new ValueMetaString("SCOPE_CATALOG"));
    meta.addValueMeta(new ValueMetaString("SCOPE_SCHEMA"));
    meta.addValueMeta(new ValueMetaString("SCOPE_TABLE"));
    meta.addValueMeta(new ValueMetaInteger("SOURCE_DATA_TYPE"));
    meta.addValueMeta(new ValueMetaString("IS_AUTOINCREMENT"));
    meta.addValueMeta(new ValueMetaString("IS_GENERATEDCOLUMN"));
    return meta;
  }

  private static IRowMeta procedureMeta() {
    return new RowMeta();
  }

  private static IRowMeta procedureColumnMeta() {
    return new RowMeta();
  }

  private static RowMetaAndData row(IRowMeta meta, Object... values) {
    Object[] data = new Object[meta.size()];
    for (int i = 0; i < meta.size() && i < values.length; i++) {
      data[i] = values[i];
    }
    return new RowMetaAndData(meta, data);
  }

  private record TableEntry(String name, String type, String remarks, List<ColumnEntry> columns) {}

  private record ColumnEntry(String name, int hopType) {}
}
