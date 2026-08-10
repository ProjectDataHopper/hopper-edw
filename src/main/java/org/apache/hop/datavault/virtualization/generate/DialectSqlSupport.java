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
package org.apache.hop.datavault.virtualization.generate;

import java.util.Locale;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.AnsiSqlDialect;
import org.apache.calcite.sql.dialect.MssqlSqlDialect;
import org.apache.calcite.sql.dialect.MysqlSqlDialect;
import org.apache.calcite.sql.dialect.PostgresqlSqlDialect;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.variables.IVariables;

/** Maps Hop {@link DatabaseMeta} to Calcite dialects and converts RelNode trees to SQL. */
public final class DialectSqlSupport {

  private DialectSqlSupport() {}

  public static SqlDialect dialectFor(DatabaseMeta databaseMeta) {
    if (databaseMeta == null) {
      return AnsiSqlDialect.DEFAULT;
    }
    String pluginId =
        databaseMeta.getPluginId() != null
            ? databaseMeta.getPluginId().toLowerCase(Locale.ROOT)
            : "";
    String name =
        databaseMeta.getName() != null ? databaseMeta.getName().toLowerCase(Locale.ROOT) : "";
    String blob = pluginId + " " + name;
    if (blob.contains("postgres") || blob.contains("greenplum") || blob.contains("redshift")) {
      return PostgresqlSqlDialect.DEFAULT;
    }
    if (blob.contains("mysql") || blob.contains("mariadb") || blob.contains("singlestore")) {
      return MysqlSqlDialect.DEFAULT;
    }
    if (blob.contains("mssql") || blob.contains("sqlserver") || blob.contains("sql server")) {
      return MssqlSqlDialect.DEFAULT;
    }
    return AnsiSqlDialect.DEFAULT;
  }

  /**
   * Converts a RelNode tree to dialect SQL, rewriting table identifiers to physical schema.table
   * names from the underlying source model tables.
   */
  public static String relToSql(RelNode rel, DatabaseMeta databaseMeta, IVariables variables) {
    SqlDialect dialect = dialectFor(databaseMeta);
    PhysicalRelToSqlConverter converter =
        new PhysicalRelToSqlConverter(dialect, databaseMeta, variables);
    SqlNode sqlNode = converter.visitRoot(rel).asStatement();
    return sqlNode.toSqlString(dialect).getSql();
  }
}
