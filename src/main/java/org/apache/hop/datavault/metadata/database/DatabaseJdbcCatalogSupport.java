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

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Resolves the JDBC catalog (database) name from {@link DatabaseMeta} using the active variable
 * space. SQL Server {@code DatabaseMetaData.getColumns} treats catalog as a real database name and
 * fails when {@code ${DB_NAME}} is passed unresolved.
 */
public final class DatabaseJdbcCatalogSupport {

  private DatabaseJdbcCatalogSupport() {}

  public static String resolveCatalog(IVariables variables, DatabaseMeta databaseMeta) {
    if (databaseMeta == null) {
      return null;
    }
    String databaseName = databaseMeta.getDatabaseName();
    if (Utils.isEmpty(databaseName)) {
      return null;
    }
    String resolved =
        variables != null ? variables.resolve(databaseName.trim()) : databaseName.trim();
    if (Utils.isEmpty(resolved) || unresolvedVariable(resolved)) {
      return null;
    }
    return resolved;
  }

  static boolean unresolvedVariable(String value) {
    return value != null && value.contains("${");
  }
}
