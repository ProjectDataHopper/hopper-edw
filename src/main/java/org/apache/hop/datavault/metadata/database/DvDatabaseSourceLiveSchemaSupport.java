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
package org.apache.hop.datavault.metadata.database;

import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvModelCheckCache;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves live table column metadata for {@link DvDatabaseSource}. */
public final class DvDatabaseSourceLiveSchemaSupport {

  private static final ILoggingObject LOGGING_OBJECT =
      new SimpleLoggingObject("DvDatabaseSourceLiveSchema", LoggingObjectType.GENERAL, null);

  private DvDatabaseSourceLiveSchemaSupport() {}

  public static IRowMeta resolveLiveFields(
      DvDatabaseSource source, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return resolveLiveFields(source, variables, metadataProvider, null);
  }

  /**
   * Resolves live table columns. When {@code cache} is provided, reuses open JDBC connections and
   * previously resolved {@link IRowMeta} for the same connection/schema/table within one model
   * check run.
   */
  public static IRowMeta resolveLiveFields(
      DvDatabaseSource source,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DvModelCheckCache cache)
      throws HopException {
    String connectionName = Const.NVL(source.getDatabaseName(), "").trim();
    if (Utils.isEmpty(connectionName)) {
      throw new HopException(
          "Please select a database connection before resolving source field types.");
    }

    String tableName = Const.NVL(source.getTableName(), "").trim();
    if (Utils.isEmpty(tableName)) {
      throw new HopException(
          "Please specify a source table or view before resolving source field types.");
    }

    String schemaName = Const.NVL(source.getSchemaName(), "");
    String cacheKey =
        DvModelCheckCache.databaseLiveFieldsKey(connectionName, schemaName, tableName, variables);
    if (cache != null) {
      IRowMeta cached = cache.getLiveFields(cacheKey);
      if (cached != null) {
        return cached;
      }
    }

    DatabaseMeta databaseMeta = loadDatabaseMeta(connectionName, metadataProvider, cache);

    String resolvedSchema = variables != null ? variables.resolve(schemaName) : schemaName;
    String resolvedTable = variables != null ? variables.resolve(tableName) : tableName;
    String resolvedConnection =
        variables != null ? variables.resolve(connectionName).trim() : connectionName;

    Database shared = cache != null ? cache.getOpenDatabase(resolvedConnection) : null;
    if (shared != null) {
      IRowMeta rowMeta = readTableFields(shared, databaseMeta, resolvedSchema, resolvedTable);
      if (cache != null) {
        cache.putLiveFields(cacheKey, rowMeta);
      }
      return rowMeta;
    }

    Database db = new Database(LOGGING_OBJECT, variables, databaseMeta);
    boolean keepOpen = cache != null;
    try {
      db.connect();
      IRowMeta rowMeta = readTableFields(db, databaseMeta, resolvedSchema, resolvedTable);
      if (keepOpen) {
        cache.putOpenDatabase(resolvedConnection, db);
        cache.putLiveFields(cacheKey, rowMeta);
        db = null; // ownership transferred to cache
      } else if (cache != null) {
        cache.putLiveFields(cacheKey, rowMeta);
      }
      return rowMeta;
    } catch (HopDatabaseException e) {
      throw new HopException("Error reading live source table metadata.", e);
    } finally {
      if (db != null) {
        try {
          db.disconnect();
        } catch (Exception e) {
          // ignore disconnect errors
        }
      }
    }
  }

  private static IRowMeta readTableFields(
      Database db, DatabaseMeta databaseMeta, String resolvedSchema, String resolvedTable)
      throws HopDatabaseException {
    IRowMeta rowMeta = db.getTableFieldsMeta(resolvedSchema, resolvedTable);
    // Prefer JDBC getColumns COLUMN_SIZE/TYPE_NAME over ResultSet display sizes
    // (SingleStore/MySQL).
    DatabaseJdbcColumnEnrichmentSupport.enrichRowMeta(
        db, databaseMeta, resolvedSchema, resolvedTable, rowMeta);
    return rowMeta;
  }

  private static DatabaseMeta loadDatabaseMeta(
      String connectionName, IHopMetadataProvider metadataProvider, DvModelCheckCache cache)
      throws HopException {
    String key = Const.NVL(connectionName, "").trim();
    if (cache != null) {
      DatabaseMeta cached = cache.getDatabaseMeta(key);
      if (cached != null) {
        return cached;
      }
    }
    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      throw new HopException("Error loading database connection '" + connectionName + "'", e);
    }
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' was not found.");
    }
    if (cache != null) {
      cache.putDatabaseMeta(key, databaseMeta);
    }
    return databaseMeta;
  }
}
