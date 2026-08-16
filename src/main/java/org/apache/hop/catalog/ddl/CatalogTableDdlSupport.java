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
package org.apache.hop.catalog.ddl;

import java.util.List;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DvDdlSupport;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldPrimaryKeySupport;
import org.apache.hop.datavault.metadata.targettypemapping.TargetTypeMappingContext;

/** Generates and optionally executes CREATE TABLE DDL from catalog record definitions. */
public final class CatalogTableDdlSupport {

  public enum DdlStatus {
    CREATED,
    OUTPUT_ONLY,
    SKIPPED_EXISTS,
    ERROR
  }

  public record DdlResult(String ddl, DdlStatus status, String message) {}

  public record ResolvedTarget(String connectionName, String schemaName, String tableName) {}

  private CatalogTableDdlSupport() {}

  public static ResolvedTarget resolveTarget(
      RecordDefinition definition,
      String overrideConnectionName,
      String overrideSchemaName,
      String overrideTableName,
      IVariables variables) {
    String connectionName = Const.NVL(overrideConnectionName, "");
    String schemaName = Const.NVL(overrideSchemaName, "");
    String tableName = Const.NVL(overrideTableName, "");

    if (definition != null && definition.getPhysicalTable() != null) {
      PhysicalTableRef physicalTable = definition.getPhysicalTable();
      if (Utils.isEmpty(connectionName)) {
        connectionName = Const.NVL(physicalTable.getDatabaseMetaName(), "");
      }
      if (Utils.isEmpty(schemaName)) {
        schemaName = Const.NVL(physicalTable.getSchemaName(), "");
      }
      if (Utils.isEmpty(tableName)) {
        tableName = Const.NVL(physicalTable.getTableName(), "");
      }
    }

    if (variables != null) {
      connectionName = variables.resolve(connectionName);
      schemaName = variables.resolve(schemaName);
      tableName = variables.resolve(tableName);
    }
    return new ResolvedTarget(connectionName, schemaName, tableName);
  }

  public static List<SourceField> sourceFieldsFromDefinition(
      RecordDefinition definition, IVariables variables) throws HopException {
    // variables reserved for future name resolution; layout merge is shared with import path.
    return DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
  }

  public static String generateCreateTableDdl(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schemaName,
      String tableName,
      List<SourceField> sourceFields,
      boolean appendSemicolon)
      throws HopException {
    return generateCreateTableDdl(
        databaseMeta, variables, schemaName, tableName, sourceFields, appendSemicolon, null);
  }

  public static String generateCreateTableDdl(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schemaName,
      String tableName,
      List<SourceField> sourceFields,
      boolean appendSemicolon,
      TargetTypeMappingContext targetTypeMapping)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Database connection is required to generate DDL.");
    }
    if (Utils.isEmpty(tableName)) {
      throw new HopException("Table name is required to generate DDL.");
    }
    if (sourceFields == null || sourceFields.isEmpty()) {
      throw new HopException("At least one source field is required to generate DDL.");
    }

    IRowMeta rowMeta = DvSourceFieldSupport.toRowMeta(sourceFields, variables);
    databaseMeta.quoteReservedWords(rowMeta);
    String qualifiedTableName =
        databaseMeta.getQuotedSchemaTableCombination(variables, schemaName, tableName);
    List<String> primaryKeyFieldNames =
        SourceFieldPrimaryKeySupport.primaryKeyFieldNames(sourceFields);

    // Catalog/CRM staging: do not apply vault SQL Server UTF-8 length ×3 (keep character lengths).
    return DvDdlSupport.buildCreateTableStatement(
        databaseMeta,
        variables,
        qualifiedTableName,
        rowMeta,
        null,
        primaryKeyFieldNames,
        List.of(),
        appendSemicolon,
        false,
        targetTypeMapping);
  }

  public static String generateDropTableDdl(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schemaName,
      String tableName,
      boolean appendSemicolon) {
    String qualifiedTableName =
        databaseMeta.getQuotedSchemaTableCombination(variables, schemaName, tableName);
    String ddl = "DROP TABLE IF EXISTS " + qualifiedTableName;
    if (appendSemicolon && !ddl.endsWith(";")) {
      ddl += ";";
    }
    return ddl;
  }

  public static String buildTableDdlScript(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schemaName,
      String tableName,
      List<SourceField> sourceFields,
      boolean dropTableIfExists,
      boolean appendSemicolon)
      throws HopException {
    return buildTableDdlScript(
        databaseMeta,
        variables,
        schemaName,
        tableName,
        sourceFields,
        dropTableIfExists,
        appendSemicolon,
        null);
  }

  public static String buildTableDdlScript(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schemaName,
      String tableName,
      List<SourceField> sourceFields,
      boolean dropTableIfExists,
      boolean appendSemicolon,
      TargetTypeMappingContext targetTypeMapping)
      throws HopException {
    String createDdl =
        generateCreateTableDdl(
            databaseMeta,
            variables,
            schemaName,
            tableName,
            sourceFields,
            appendSemicolon,
            targetTypeMapping);
    if (!dropTableIfExists) {
      return createDdl;
    }
    String dropDdl =
        generateDropTableDdl(databaseMeta, variables, schemaName, tableName, appendSemicolon);
    return dropDdl + Const.CR + createDdl;
  }

  public static DdlResult applyTableDdl(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schemaName,
      String tableName,
      List<SourceField> sourceFields,
      boolean dropTableIfExists,
      boolean executeDdl,
      boolean skipIfTableExists,
      boolean appendSemicolon,
      ILoggingObject loggingObject)
      throws HopException {
    return applyTableDdl(
        databaseMeta,
        variables,
        schemaName,
        tableName,
        sourceFields,
        dropTableIfExists,
        executeDdl,
        skipIfTableExists,
        appendSemicolon,
        loggingObject,
        null);
  }

  public static DdlResult applyTableDdl(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String schemaName,
      String tableName,
      List<SourceField> sourceFields,
      boolean dropTableIfExists,
      boolean executeDdl,
      boolean skipIfTableExists,
      boolean appendSemicolon,
      ILoggingObject loggingObject,
      TargetTypeMappingContext targetTypeMapping)
      throws HopException {
    String ddl =
        buildTableDdlScript(
            databaseMeta,
            variables,
            schemaName,
            tableName,
            sourceFields,
            dropTableIfExists,
            appendSemicolon,
            targetTypeMapping);
    if (!executeDdl) {
      return new DdlResult(ddl, DdlStatus.OUTPUT_ONLY, null);
    }

    try (Database database = new Database(loggingObject, variables, databaseMeta)) {
      database.connect();
      if (!dropTableIfExists
          && skipIfTableExists
          && DvDdlSupport.shouldSkipCreateTable(database, variables, databaseMeta, ddl, null)) {
        return new DdlResult(ddl, DdlStatus.SKIPPED_EXISTS, null);
      }
      database.execStatements(ddl);
      return new DdlResult(ddl, DdlStatus.CREATED, null);
    } catch (HopDatabaseException e) {
      return new DdlResult(ddl, DdlStatus.ERROR, e.getMessage());
    }
  }
}
