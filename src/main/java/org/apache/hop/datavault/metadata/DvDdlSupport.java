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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.database.IDatabase;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.targettypemapping.TargetTypeMappingContext;
import org.apache.hop.datavault.metadata.targettypemapping.TargetTypeMappingResolver;
import org.apache.hop.datavault.metadata.targettypemapping.TargetTypeSqlSupport;

/** Helpers for generating Data Vault target table DDL. */
public final class DvDdlSupport {

  /**
   * SQL Server 2019+ UTF-8 collation for target {@code VARCHAR}/{@code CHAR} columns so ANSI string
   * types store full Unicode without requiring NVARCHAR.
   */
  public static final String SQL_SERVER_UTF8_COLLATION = "Latin1_General_100_CI_AS_SC_UTF8";

  /**
   * Multiplier applied to Hop string lengths when generating SQL Server <em>vault/EDW</em> {@code
   * VARCHAR}/{@code CHAR} with a UTF-8 collation.
   *
   * <p><b>Contract (do not drift):</b>
   *
   * <ul>
   *   <li>Model / catalog / {@link IValueMeta} lengths are <em>character</em>-oriented (same units
   *       as {@code NVARCHAR(n)}).
   *   <li>Vault SQL Server DDL only: character length {@code n} → physical {@code VARCHAR(n×3)} (or
   *       {@code VARCHAR(MAX)} when over 8000) via {@link #utf8ByteLengthForCharacterLength}.
   *   <li>Model-check overflow must use {@link #effectiveStringCapacity}, not raw model length.
   *   <li>Catalog / CRM staging CREATE must <em>not</em> apply this expansion (character lengths
   *       stay as modeled).
   * </ul>
   *
   * <p>SQL Server {@code VARCHAR(n)} with a UTF-8 collation measures {@code n} in <em>bytes</em>.
   * Multi-byte content that fits in {@code NVARCHAR(50)} therefore overflows {@code VARCHAR(50)}
   * (issue #91). Factor 3 is the worst-case UTF-8 size per BMP code unit.
   */
  public static final int SQL_SERVER_UTF8_LENGTH_FACTOR = 3;

  /** SQL Server maximum length for non-{@code MAX} {@code VARCHAR}/{@code CHAR}. */
  public static final int SQL_SERVER_MAX_NON_MAX_STRING_LENGTH = 8000;

  /**
   * Match ANSI string types without a trailing word-boundary after {@code )} (which is non-word and
   * would never form {@code \b}). Negative lookbehind/ahead exclude {@code NCHAR}/{@code NVARCHAR}/
   * {@code NTEXT}. Group 1 is the full type token (e.g. {@code VARCHAR(50)}); group 2 is {@code
   * CHAR}/{@code VARCHAR}; group 3 is a numeric length; group 4 is {@code MAX} when present.
   */
  private static final Pattern SQL_SERVER_ANSI_STRING_TYPE =
      Pattern.compile(
          "(?i)(?<![A-Za-z0-9_])("
              + "((?:VAR)?CHAR)\\s*\\(\\s*(?:(\\d+)|(MAX))\\s*\\)"
              + "|TEXT"
              + ")(?![A-Za-z0-9_])"
              + "(?!\\s+COLLATE\\b)");

  private DvDdlSupport() {}

  public static boolean isSingleStore(DatabaseMeta databaseMeta) {
    return databaseMeta != null && "SINGLESTORE".equalsIgnoreCase(databaseMeta.getPluginId());
  }

  /**
   * Whether the target engine supports emitting table-level PRIMARY KEY clauses in CREATE TABLE.
   * All current EDW targets used by this plugin do.
   */
  public static boolean supportsPrimaryKeyConstraints(DatabaseMeta databaseMeta) {
    return databaseMeta != null;
  }

  /**
   * Whether the target engine supports FOREIGN KEY constraints. SingleStore does not; other
   * supported EDW engines (PostgreSQL, MySQL/MariaDB, SQL Server, etc.) do.
   */
  public static boolean supportsForeignKeyConstraints(DatabaseMeta databaseMeta) {
    if (databaseMeta == null) {
      return false;
    }
    return !isSingleStore(databaseMeta);
  }

  public static boolean isShardKeyDdlEnabled(
      DataVaultConfiguration config, DatabaseMeta databaseMeta) {
    return config != null && config.isSingleStoreShardKeyOnHashKey() && isSingleStore(databaseMeta);
  }

  /**
   * Returns CREATE TABLE DDL with an optional SingleStore {@code SHARD KEY} clause, or the existing
   * Hop DDL when the table already exists (ALTER) or no DDL is required. SQL Server string columns
   * receive a UTF-8 {@code COLLATE} clause.
   */
  public static String getCreateTableDdl(
      Database db,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      String primaryKeyColumn,
      boolean semicolon)
      throws HopDatabaseException {
    List<String> primaryKeyFieldNames =
        Utils.isEmpty(primaryKeyColumn) ? List.of() : List.of(primaryKeyColumn);
    return getCreateTableDdl(
        db, tableName, fields, shardKeyColumns, primaryKeyFieldNames, List.of(), semicolon);
  }

  /**
   * Returns CREATE TABLE DDL with optional primary key, foreign key, and SingleStore shard key
   * clauses. When the table already exists, returns Hop ALTER DDL without adding constraints.
   */
  public static String getCreateTableDdl(
      Database db,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      List<String> primaryKeyFieldNames,
      List<ForeignKeySpec> foreignKeys,
      boolean semicolon)
      throws HopDatabaseException {
    if (db == null || Utils.isEmpty(tableName) || fields == null || fields.isEmpty()) {
      return "";
    }

    DatabaseMeta databaseMeta = db.getDatabaseMeta();
    IRowMeta layout = fields.clone();
    databaseMeta.quoteReservedWords(layout);

    String existingDdl = db.getDDL(tableName, layout);
    if (Utils.isEmpty(existingDdl)) {
      return "";
    }
    if (!isCreateTableDdl(existingDdl)) {
      return enrichSqlServerDdl(databaseMeta, existingDdl);
    }

    return buildCreateTableStatement(
        databaseMeta,
        db,
        tableName,
        layout,
        shardKeyColumns,
        primaryKeyFieldNames,
        foreignKeys,
        semicolon);
  }

  /**
   * Returns Hop-generated DDL for a target table, with SQL Server UTF-8 collations applied to ANSI
   * string columns on both CREATE and ALTER paths.
   */
  public static String getTargetTableDdl(Database db, String tableName, IRowMeta fields)
      throws HopDatabaseException {
    return getTargetTableDdl(db, tableName, fields, null, List.of(), List.of(), null);
  }

  public static String getTargetTableDdl(
      Database db, String tableName, IRowMeta fields, TargetTypeMappingContext context)
      throws HopDatabaseException {
    return getTargetTableDdl(db, tableName, fields, null, List.of(), List.of(), context);
  }

  /**
   * Returns target-table DDL, injecting primary/foreign key clauses into CREATE TABLE when
   * requested. ALTER TABLE drift paths are unchanged (no constraint retrofit).
   */
  public static String getTargetTableDdl(
      Database db,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      List<String> primaryKeyFieldNames,
      List<ForeignKeySpec> foreignKeys)
      throws HopDatabaseException {
    return getTargetTableDdl(
        db, tableName, fields, shardKeyColumns, primaryKeyFieldNames, foreignKeys, null);
  }

  public static String getTargetTableDdl(
      Database db,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      List<String> primaryKeyFieldNames,
      List<ForeignKeySpec> foreignKeys,
      TargetTypeMappingContext context)
      throws HopDatabaseException {
    if (db == null || Utils.isEmpty(tableName) || fields == null || fields.isEmpty()) {
      return "";
    }
    DatabaseMeta databaseMeta = db.getDatabaseMeta();
    IRowMeta layout = fields.clone();
    databaseMeta.quoteReservedWords(layout);

    String hopDdl = db.getDDL(tableName, layout);
    if (Utils.isEmpty(hopDdl)) {
      return "";
    }
    boolean hasConstraints =
        (primaryKeyFieldNames != null && !primaryKeyFieldNames.isEmpty())
            || (foreignKeys != null && !foreignKeys.isEmpty())
            || (shardKeyColumns != null && shardKeyColumns.length > 0);
    boolean hasMapping = context != null && context.hasMapping();
    if (isCreateTableDdl(hopDdl)
        && (hasConstraints || DvSqlOrderBySupport.isSqlServer(databaseMeta) || hasMapping)) {
      return buildCreateTableStatement(
          databaseMeta,
          db,
          tableName,
          layout,
          shardKeyColumns,
          primaryKeyFieldNames,
          foreignKeys,
          true,
          true,
          context);
    }
    if (!isCreateTableDdl(hopDdl) && hasMapping) {
      return generateAlterDdl(db, tableName, layout, context, true);
    }
    return enrichSqlServerDdl(databaseMeta, hopDdl);
  }

  static boolean isCreateTableDdl(String ddl) {
    if (Utils.isEmpty(ddl)) {
      return false;
    }
    return ddl.trim().regionMatches(true, 0, "CREATE", 0, 6);
  }

  /**
   * Extracts the physical table name from a CREATE TABLE statement. Returns {@code null} when the
   * statement cannot be parsed.
   */
  public static String extractCreateTableName(String ddl) {
    if (!isCreateTableDdl(ddl)) {
      return null;
    }
    String remainder =
        ddl.trim()
            .replaceFirst(
                "(?is)^CREATE\\s+(OR\\s+REPLACE\\s+)?TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?", "");
    remainder = remainder.trim();
    if (remainder.isEmpty()) {
      return null;
    }
    if (remainder.charAt(0) == '"') {
      int endQuote = remainder.indexOf('"', 1);
      if (endQuote > 1) {
        return remainder.substring(1, endQuote);
      }
    }
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < remainder.length(); i++) {
      char c = remainder.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
        name.append(c);
      } else {
        break;
      }
    }
    String parsed = name.toString();
    if (parsed.isEmpty()) {
      return null;
    }
    int dot = parsed.lastIndexOf('.');
    return dot >= 0 ? parsed.substring(dot + 1) : parsed;
  }

  /**
   * Removes duplicate CREATE TABLE statements that target the same physical table. Keeps the first
   * statement for each table name.
   */
  public static List<String> deduplicateCreateTableDdl(List<String> ddlStatements) {
    if (ddlStatements == null || ddlStatements.isEmpty()) {
      return ddlStatements;
    }
    List<String> result = new ArrayList<>(ddlStatements.size());
    Set<String> seenCreateTables = new HashSet<>();
    for (String ddl : ddlStatements) {
      if (isCreateTableDdl(ddl)) {
        String tableName = extractCreateTableName(ddl);
        if (!Utils.isEmpty(tableName)) {
          String key = tableName.toLowerCase(Locale.ROOT);
          if (seenCreateTables.contains(key)) {
            continue;
          }
          seenCreateTables.add(key);
        }
      }
      result.add(ddl);
    }
    return result;
  }

  /**
   * Returns {@code true} when a CREATE TABLE statement should not be executed because the table was
   * already created in the current batch or already exists in the target database.
   */
  public static boolean shouldSkipCreateTable(
      Database db,
      IVariables variables,
      DatabaseMeta databaseMeta,
      String ddl,
      Set<String> createdInBatch)
      throws HopDatabaseException {
    if (!isCreateTableDdl(ddl)) {
      return false;
    }
    String tableName = extractCreateTableName(ddl);
    if (Utils.isEmpty(tableName)) {
      return false;
    }
    String key = tableName.toLowerCase(Locale.ROOT);
    if (createdInBatch != null && createdInBatch.contains(key)) {
      return true;
    }
    if (db == null) {
      return false;
    }
    String schema =
        databaseMeta != null && variables != null
            ? variables.resolve(databaseMeta.getPreferredSchemaName())
            : databaseMeta != null ? databaseMeta.getPreferredSchemaName() : null;
    if (db.checkTableExists(schema, tableName)) {
      return true;
    }
    return createdInBatch != null && createdInBatch.contains(key);
  }

  /**
   * Builds a CREATE TABLE statement using the same field-definition primitives as Hop's {@link
   * Database#getCreateTableStatement(String, IRowMeta, String, boolean, String, boolean)}.
   */
  public static String buildCreateTableStatement(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      String primaryKeyColumn,
      boolean semicolon) {
    List<String> primaryKeyFieldNames =
        Utils.isEmpty(primaryKeyColumn) ? List.of() : List.of(primaryKeyColumn);
    return buildCreateTableStatement(
        databaseMeta,
        variables,
        tableName,
        fields,
        shardKeyColumns,
        primaryKeyFieldNames,
        List.of(),
        semicolon);
  }

  public static String buildCreateTableStatement(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      List<String> primaryKeyFieldNames,
      boolean semicolon) {
    return buildCreateTableStatement(
        databaseMeta,
        variables,
        tableName,
        fields,
        shardKeyColumns,
        primaryKeyFieldNames,
        List.of(),
        semicolon);
  }

  public static String buildCreateTableStatement(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      List<String> primaryKeyFieldNames,
      List<ForeignKeySpec> foreignKeys,
      boolean semicolon) {
    return buildCreateTableStatement(
        databaseMeta,
        variables,
        tableName,
        fields,
        shardKeyColumns,
        primaryKeyFieldNames,
        foreignKeys,
        semicolon,
        true,
        null);
  }

  /**
   * @param applySqlServerUtf8EdwPolicy vault/EDW {@code true} (UTF-8 COLLATE + length ×3); catalog
   *     / CRM staging {@code false} (Hop native types, character lengths preserved)
   */
  public static String buildCreateTableStatement(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      List<String> primaryKeyFieldNames,
      List<ForeignKeySpec> foreignKeys,
      boolean semicolon,
      boolean applySqlServerUtf8EdwPolicy) {
    return buildCreateTableStatement(
        databaseMeta,
        variables,
        tableName,
        fields,
        shardKeyColumns,
        primaryKeyFieldNames,
        foreignKeys,
        semicolon,
        applySqlServerUtf8EdwPolicy,
        null);
  }

  /**
   * @param applySqlServerUtf8EdwPolicy vault/EDW {@code true} (UTF-8 COLLATE + length ×3); catalog
   *     / CRM staging {@code false} (Hop native types, character lengths preserved)
   * @param context optional target type mapping; {@code null} keeps Hop dialect types
   */
  public static String buildCreateTableStatement(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String tableName,
      IRowMeta fields,
      String[] shardKeyColumns,
      List<String> primaryKeyFieldNames,
      List<ForeignKeySpec> foreignKeys,
      boolean semicolon,
      boolean applySqlServerUtf8EdwPolicy,
      TargetTypeMappingContext context) {
    if (databaseMeta == null || Utils.isEmpty(tableName) || fields == null || fields.isEmpty()) {
      return "";
    }

    IDatabase database = databaseMeta.getIDatabase();
    StringBuilder ddl = new StringBuilder();
    ddl.append(database.getCreateTableStatement());
    ddl.append(tableName);
    ddl.append(Const.CR).append("(").append(Const.CR);

    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        ddl.append(",").append(Const.CR);
      }
      IValueMeta valueMeta = fields.getValueMeta(i);
      // Use a table-level PRIMARY KEY clause so JDBC discovery finds the constraint and
      // PostgreSQL does not emit BIGSERIAL for the first key column.
      ddl.append(getFieldDefinition(databaseMeta, valueMeta, applySqlServerUtf8EdwPolicy, context));
    }

    if (supportsPrimaryKeyConstraints(databaseMeta)) {
      appendPrimaryKeyClause(ddl, databaseMeta, primaryKeyFieldNames);
    }

    if (shardKeyColumns != null && shardKeyColumns.length > 0) {
      ddl.append(",").append(Const.CR);
      ddl.append("SHARD KEY (");
      for (int i = 0; i < shardKeyColumns.length; i++) {
        if (i > 0) {
          ddl.append(", ");
        }
        ddl.append(databaseMeta.quoteField(shardKeyColumns[i]));
      }
      ddl.append(")");
    }

    if (supportsForeignKeyConstraints(databaseMeta)) {
      appendForeignKeyClauses(ddl, databaseMeta, foreignKeys);
    }

    ddl.append(")").append(Const.CR);
    ddl.append(database.getDataTablespaceDDL(variables, databaseMeta));

    if (semicolon) {
      ddl.append(";");
    }
    return ddl.toString();
  }

  static void appendPrimaryKeyClause(
      StringBuilder ddl, DatabaseMeta databaseMeta, List<String> primaryKeyFieldNames) {
    if (primaryKeyFieldNames == null || primaryKeyFieldNames.isEmpty()) {
      return;
    }
    ddl.append(",").append(Const.CR);
    ddl.append("PRIMARY KEY (");
    for (int i = 0; i < primaryKeyFieldNames.size(); i++) {
      if (i > 0) {
        ddl.append(", ");
      }
      ddl.append(databaseMeta.quoteField(primaryKeyFieldNames.get(i)));
    }
    ddl.append(")");
  }

  static void appendForeignKeyClauses(
      StringBuilder ddl, DatabaseMeta databaseMeta, List<ForeignKeySpec> foreignKeys) {
    if (foreignKeys == null || foreignKeys.isEmpty() || databaseMeta == null) {
      return;
    }
    for (ForeignKeySpec fk : foreignKeys) {
      if (fk == null || !fk.isValid()) {
        continue;
      }
      ddl.append(",").append(Const.CR);
      if (!Utils.isEmpty(fk.getConstraintName())) {
        ddl.append("CONSTRAINT ")
            .append(databaseMeta.quoteField(fk.getConstraintName()))
            .append(" ");
      }
      ddl.append("FOREIGN KEY (");
      for (int i = 0; i < fk.getChildColumns().size(); i++) {
        if (i > 0) {
          ddl.append(", ");
        }
        ddl.append(databaseMeta.quoteField(fk.getChildColumns().get(i)));
      }
      ddl.append(") REFERENCES ");
      ddl.append(databaseMeta.quoteField(fk.getParentTableName()));
      ddl.append(" (");
      for (int i = 0; i < fk.getParentColumns().size(); i++) {
        if (i > 0) {
          ddl.append(", ");
        }
        ddl.append(databaseMeta.quoteField(fk.getParentColumns().get(i)));
      }
      ddl.append(")");
    }
  }

  /**
   * Field definition with SQL Server vault/EDW UTF-8 policy (collation + length ×3) on ANSI string
   * types. Binary/numeric/date columns and already-collated definitions are left unchanged.
   *
   * <p>For catalog/CRM staging tables use {@link #getFieldDefinition(DatabaseMeta, IValueMeta,
   * boolean)} with {@code applySqlServerUtf8EdwPolicy=false} so character lengths are not expanded.
   */
  public static String getFieldDefinition(DatabaseMeta databaseMeta, IValueMeta valueMeta) {
    return getFieldDefinition(databaseMeta, valueMeta, true, null);
  }

  /**
   * @param applySqlServerUtf8EdwPolicy when {@code true} (vault/EDW), apply UTF-8 COLLATE and
   *     length expansion; when {@code false} (catalog/CRM staging), use Hop's native field
   *     definition only so modeled character lengths stay as-is
   */
  public static String getFieldDefinition(
      DatabaseMeta databaseMeta, IValueMeta valueMeta, boolean applySqlServerUtf8EdwPolicy) {
    return getFieldDefinition(databaseMeta, valueMeta, applySqlServerUtf8EdwPolicy, null);
  }

  /**
   * @param context when a rule matches, the user SQL type is used and SQL Server UTF-8 rewrite is
   *     skipped for that column
   */
  public static String getFieldDefinition(
      DatabaseMeta databaseMeta,
      IValueMeta valueMeta,
      boolean applySqlServerUtf8EdwPolicy,
      TargetTypeMappingContext context) {
    if (databaseMeta == null || valueMeta == null) {
      return "";
    }
    String customType = resolveCustomSqlType(valueMeta, context);
    if (!Utils.isEmpty(customType)) {
      String hopDefinition = databaseMeta.getFieldDefinition(valueMeta, null, null, false);
      String hopType = hopTypeOnly(databaseMeta, valueMeta);
      return TargetTypeSqlSupport.replaceTypeToken(hopDefinition, hopType, customType);
    }
    // addFieldname=true matches Database#getCreateTableStatement field lines.
    String definition = databaseMeta.getFieldDefinition(valueMeta, null, null, false);
    if (!applySqlServerUtf8EdwPolicy) {
      return definition;
    }
    return enrichSqlServerFieldDefinition(databaseMeta, definition);
  }

  /** Native SQL type only (no field name), after mapping rules and optional UTF-8 policy. */
  public static String getSqlType(
      DatabaseMeta databaseMeta,
      IValueMeta valueMeta,
      boolean applySqlServerUtf8EdwPolicy,
      TargetTypeMappingContext context) {
    if (databaseMeta == null || valueMeta == null) {
      return "";
    }
    String customType = resolveCustomSqlType(valueMeta, context);
    if (!Utils.isEmpty(customType)) {
      return customType;
    }
    String hopType = hopTypeOnly(databaseMeta, valueMeta);
    if (!applySqlServerUtf8EdwPolicy) {
      return hopType;
    }
    return TargetTypeSqlSupport.stripTrailingWhitespace(
        enrichSqlServerFieldDefinition(databaseMeta, hopType));
  }

  static String generateAlterDdl(
      Database db,
      String tableName,
      IRowMeta fields,
      TargetTypeMappingContext context,
      boolean applySqlServerUtf8EdwPolicy)
      throws HopDatabaseException {
    if (db == null || Utils.isEmpty(tableName) || fields == null || fields.isEmpty()) {
      return "";
    }
    DatabaseMeta databaseMeta = db.getDatabaseMeta();
    IRowMeta tabFields = db.getTableFields(tableName);
    if (tabFields == null) {
      tabFields = new org.apache.hop.core.row.RowMeta();
    }
    databaseMeta.quoteReservedWords(tabFields);

    StringBuilder ddl = new StringBuilder();
    for (int i = 0; i < fields.size(); i++) {
      IValueMeta desired = fields.getValueMeta(i);
      if (tabFields.searchValueMeta(desired.getName()) == null) {
        String hopAdd =
            databaseMeta.getAddColumnStatement(tableName, desired, null, false, null, true);
        ddl.append(
            TargetTypeSqlSupport.replaceTypeToken(
                hopAdd,
                hopTypeOnly(databaseMeta, desired),
                getSqlType(databaseMeta, desired, applySqlServerUtf8EdwPolicy, context)));
      }
    }

    for (int i = 0; i < tabFields.size(); i++) {
      IValueMeta current = tabFields.getValueMeta(i);
      if (fields.searchValueMeta(current.getName()) == null) {
        ddl.append(
            databaseMeta.getDropColumnStatement(tableName, current, null, false, null, true));
      }
    }

    for (int i = 0; i < fields.size(); i++) {
      IValueMeta desired = fields.getValueMeta(i);
      IValueMeta current = tabFields.searchValueMeta(desired.getName());
      if (current == null) {
        continue;
      }
      String desiredType = getSqlType(databaseMeta, desired, applySqlServerUtf8EdwPolicy, context);
      String currentType = TargetTypeSqlSupport.physicalSqlType(current);
      if (Utils.isEmpty(currentType)) {
        currentType = hopTypeOnly(databaseMeta, current);
      }
      if (TargetTypeSqlSupport.sameNormalizedType(desiredType, currentType)) {
        continue;
      }
      String hopModify =
          databaseMeta.getModifyColumnStatement(tableName, desired, null, false, null, true);
      ddl.append(
          TargetTypeSqlSupport.replaceTypeToken(
              hopModify, hopTypeOnly(databaseMeta, desired), desiredType));
    }
    return ddl.toString();
  }

  static String resolveCustomSqlType(IValueMeta valueMeta, TargetTypeMappingContext context) {
    if (context == null || !context.hasMapping()) {
      return null;
    }
    return TargetTypeMappingResolver.resolveSqlType(
        valueMeta, context.getMapping(), context.getVariables());
  }

  static String hopTypeOnly(DatabaseMeta databaseMeta, IValueMeta valueMeta) {
    if (databaseMeta == null || valueMeta == null) {
      return "";
    }
    return TargetTypeSqlSupport.stripTrailingWhitespace(
        databaseMeta.getFieldDefinition(valueMeta, null, null, false, false, false));
  }

  /**
   * Appends {@code COLLATE Latin1_General_100_CI_AS_SC_UTF8} to SQL Server ANSI string field
   * definitions when missing.
   */
  public static String enrichSqlServerFieldDefinition(
      DatabaseMeta databaseMeta, String fieldDefinition) {
    if (!DvSqlOrderBySupport.isSqlServer(databaseMeta) || Utils.isEmpty(fieldDefinition)) {
      return fieldDefinition;
    }
    return rewriteSqlServerStringCollations(fieldDefinition);
  }

  /**
   * Rewrites CREATE/ALTER DDL so SQL Server ANSI string types carry the EDW UTF-8 collation. No-op
   * for other engines or empty input. Does not double-apply when {@code COLLATE} is already present
   * after a string type.
   */
  public static String enrichSqlServerDdl(DatabaseMeta databaseMeta, String ddl) {
    if (!DvSqlOrderBySupport.isSqlServer(databaseMeta) || Utils.isEmpty(ddl)) {
      return ddl;
    }
    return rewriteSqlServerStringCollations(ddl);
  }

  /**
   * Rewrites {@code CHAR}/{@code VARCHAR}/{@code TEXT} tokens for SQL Server EDW targets: expands
   * numeric lengths for UTF-8 byte storage and appends the UTF-8 {@code COLLATE} clause when
   * missing. Skips {@code NCHAR}/{@code NVARCHAR}/{@code NTEXT}. Tokens that already carry {@code
   * COLLATE} are left unchanged (idempotent; avoids double length expansion).
   */
  static String rewriteSqlServerStringCollations(String sql) {
    if (Utils.isEmpty(sql)) {
      return sql;
    }
    Matcher matcher = SQL_SERVER_ANSI_STRING_TYPE.matcher(sql);
    StringBuffer out = new StringBuffer();
    while (matcher.find()) {
      String rewritten = rewriteSqlServerAnsiStringTypeMatch(matcher);
      matcher.appendReplacement(out, Matcher.quoteReplacement(rewritten));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /**
   * Builds the replacement type token for one ANSI string match: expanded length when needed, plus
   * UTF-8 collation.
   */
  static String rewriteSqlServerAnsiStringTypeMatch(Matcher matcher) {
    String fullToken = matcher.group(1);
    String baseType = matcher.group(2); // CHAR / VARCHAR, or null for TEXT
    String numericLength = matcher.group(3);
    String maxToken = matcher.group(4);

    String typeWithLength;
    if (baseType == null) {
      // TEXT
      typeWithLength = fullToken;
    } else if (maxToken != null) {
      typeWithLength = baseType + "(MAX)";
    } else if (numericLength != null) {
      typeWithLength = expandSqlServerUtf8StringType(baseType, Integer.parseInt(numericLength));
    } else {
      typeWithLength = fullToken;
    }
    return typeWithLength + " COLLATE " + SQL_SERVER_UTF8_COLLATION;
  }

  /**
   * Expands a character-oriented string length to a UTF-8 byte-oriented SQL Server type. Lengths
   * that exceed {@link #SQL_SERVER_MAX_NON_MAX_STRING_LENGTH} after expansion become {@code
   * VARCHAR(MAX)} (including oversized {@code CHAR}).
   */
  static String expandSqlServerUtf8StringType(String baseType, int characterLength) {
    if (characterLength <= 0) {
      return baseType + "(" + characterLength + ")";
    }
    int byteLength = utf8ByteLengthForCharacterLength(characterLength);
    if (byteLength > SQL_SERVER_MAX_NON_MAX_STRING_LENGTH) {
      // Prefer VARCHAR(MAX) over an illegal CHAR/VARCHAR(n>8000).
      return "VARCHAR(MAX)";
    }
    return baseType + "(" + byteLength + ")";
  }

  /**
   * Converts a character-oriented length (as stored on source fields / {@link IValueMeta}) to the
   * minimum UTF-8 byte length that can hold the same Unicode content on SQL Server {@code
   * VARCHAR}/{@code CHAR}. Returns a value greater than {@link
   * #SQL_SERVER_MAX_NON_MAX_STRING_LENGTH} when {@code VARCHAR(MAX)} should be used.
   *
   * <p>Used by vault DDL rewrite <em>and</em> by model-check capacity via {@link
   * #effectiveStringCapacity}. Keep those call sites in sync.
   */
  public static int utf8ByteLengthForCharacterLength(int characterLength) {
    if (characterLength <= 0) {
      return characterLength;
    }
    long expanded = (long) characterLength * SQL_SERVER_UTF8_LENGTH_FACTOR;
    if (expanded > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) expanded;
  }

  /**
   * Storage capacity for a model/catalog string length on the given database.
   *
   * <p>On SQL Server vault targets this is the UTF-8 byte capacity ({@code modelLength × {@link
   * #SQL_SERVER_UTF8_LENGTH_FACTOR}}); elsewhere it is the character length as modeled. Use this
   * whenever comparing a physical source length to a model target length so validation matches
   * generated vault DDL.
   */
  public static int effectiveStringCapacity(DatabaseMeta databaseMeta, int modelCharacterLength) {
    if (modelCharacterLength <= 0) {
      return modelCharacterLength;
    }
    if (DvSqlOrderBySupport.isSqlServer(databaseMeta)) {
      return utf8ByteLengthForCharacterLength(modelCharacterLength);
    }
    return modelCharacterLength;
  }
}
