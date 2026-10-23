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
package org.hopper.edw.datavault.virtualization.jdbc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.hop.core.util.Utils;

/**
 * Picks the JDBC schema a statement addresses. A bare table name stays on the connection schema.
 * {@code f_orders.d_customer} addresses the service named {@code f_orders}, which is what Hop's
 * database explorer sends when the open connection's default schema is a different service.
 */
public final class JdbcSqlSchemaResolver {

  private JdbcSqlSchemaResolver() {}

  /**
   * @param connectionSchema schema already selected on the connection; may be empty
   * @param knownSchemas enabled source and dimensional service names
   * @return the service to open, using the connection schema when the statement does not qualify
   *     one
   */
  public static String resolve(String sql, String connectionSchema, Collection<String> knownSchemas)
      throws JdbcSqlSchemaException {
    if (Utils.isEmpty(sql) || knownSchemas == null || knownSchemas.isEmpty()) {
      return connectionSchema;
    }
    Map<String, String> known = new LinkedHashMap<>();
    for (String name : knownSchemas) {
      if (!Utils.isEmpty(name)) {
        known.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), name.trim());
      }
    }
    if (known.isEmpty()) {
      return connectionSchema;
    }
    SqlNode parsed;
    try {
      parsed =
          SqlParser.create(
                  sql,
                  SqlParser.config()
                      .withCaseSensitive(false)
                      .withQuotedCasing(Casing.UNCHANGED)
                      .withUnquotedCasing(Casing.UNCHANGED))
              .parseQuery();
    } catch (SqlParseException e) {
      return connectionSchema;
    }
    Set<String> referenced = new LinkedHashSet<>();
    parsed.accept(
        new SqlBasicVisitor<Void>() {
          @Override
          public Void visit(SqlCall call) {
            if (call instanceof SqlSelect select) {
              note(select.getFrom(), known, referenced);
            }
            return super.visit(call);
          }
        });
    if (referenced.isEmpty()) {
      return connectionSchema;
    }
    if (referenced.size() > 1) {
      throw new JdbcSqlSchemaException(
          "A query can use only one JDBC schema. This statement names: "
              + String.join(", ", referenced)
              + ".");
    }
    return referenced.iterator().next();
  }

  private static void note(SqlNode from, Map<String, String> known, Set<String> referenced) {
    if (from == null) {
      return;
    }
    if (from instanceof SqlIdentifier id) {
      if (id.names.size() >= 2) {
        String canonical = known.get(id.names.get(0).toLowerCase(Locale.ROOT));
        if (canonical != null) {
          referenced.add(canonical);
        }
      }
      return;
    }
    if (from instanceof SqlJoin join) {
      note(join.getLeft(), known, referenced);
      note(join.getRight(), known, referenced);
      return;
    }
    if (from instanceof SqlCall call && call.getKind() == SqlKind.AS) {
      note(call.operand(0), known, referenced);
    }
  }

  /** The statement qualifies more than one enabled service. */
  public static final class JdbcSqlSchemaException extends Exception {
    public JdbcSqlSchemaException(String message) {
      super(message);
    }
  }
}
