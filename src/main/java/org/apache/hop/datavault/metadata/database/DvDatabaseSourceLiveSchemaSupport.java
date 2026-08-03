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
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves live table column metadata for {@link DvDatabaseSource}. */
public final class DvDatabaseSourceLiveSchemaSupport {

  private static final ILoggingObject LOGGING_OBJECT =
      new SimpleLoggingObject("DvDatabaseSourceLiveSchema", LoggingObjectType.GENERAL, null);

  private DvDatabaseSourceLiveSchemaSupport() {}

  public static IRowMeta resolveLiveFields(
      DvDatabaseSource source, IVariables variables, IHopMetadataProvider metadataProvider)
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

    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      throw new HopException("Error loading database connection '" + connectionName + "'", e);
    }
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' was not found.");
    }

    String schemaName = Const.NVL(source.getSchemaName(), "");
    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      return db.getTableFieldsMeta(
          variables != null ? variables.resolve(schemaName) : schemaName,
          variables != null ? variables.resolve(tableName) : tableName);
    } catch (HopDatabaseException e) {
      throw new HopException("Error reading live source table metadata.", e);
    }
  }
}
