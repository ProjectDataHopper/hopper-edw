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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class BvSourceQuerySqlSupportTest {

  @Test
  void fromValueMetaCopiesTypeLengthAndPrecision() {
    ValueMetaString name = new ValueMetaString("customer_name");
    name.setLength(100);
    BvSourceQueryColumn nameCol = BvSourceQuerySqlSupport.fromValueMeta(name);
    assertEquals("customer_name", nameCol.getName());
    assertEquals("String", nameCol.getDataType());
    assertEquals("100", nameCol.getLength());
    assertEquals(null, nameCol.getPrecision());

    ValueMetaInteger score = new ValueMetaInteger("demo_score");
    score.setLength(9);
    score.setPrecision(0);
    BvSourceQueryColumn scoreCol = BvSourceQuerySqlSupport.fromValueMeta(score);
    assertEquals("Integer", scoreCol.getDataType());
    assertEquals("9", scoreCol.getLength());
    assertEquals("0", scoreCol.getPrecision());

    IValueMeta loadTs = new ValueMetaTimestamp("x_load_ts");
    BvSourceQueryColumn tsCol = BvSourceQuerySqlSupport.fromValueMeta(loadTs);
    assertEquals("Timestamp", tsCol.getDataType());
    assertEquals(null, tsCol.getLength());
  }

  @Test
  void stripTrailingSemicolonRemovesTerminator() {
    assertEquals("SELECT 1", BvSourceQuerySqlSupport.stripTrailingSemicolon("SELECT 1;"));
    assertEquals("SELECT 1", BvSourceQuerySqlSupport.stripTrailingSemicolon("SELECT 1 ;\n"));
  }

  @Test
  void wrapSqlAsSubqueryKeepsInnerWith() {
    String wrapped =
        BvSourceQuerySqlSupport.wrapSqlAsSubquery(
            "WITH x AS (SELECT 1 AS hk) SELECT hk FROM x;", "src");
    assertTrue(wrapped.startsWith("(WITH x AS"));
    assertTrue(wrapped.endsWith(") src"));
    assertFalse(wrapped.contains(";"));
  }

  @Test
  void fromClauseTableUsesQuotedName() {
    BvSourceQuery source = new BvSourceQuery();
    source.setName("sat_customer_corrected");
    source.setTableName("sat_customer_v");
    source.setSchemaName("vault");
    source.setSourceKind(BvSourceQueryKind.TABLE);
    DatabaseMeta db = new TestDatabaseMeta("CRM");

    String from = BvSourceQuerySqlSupport.fromClause(db, new Variables(), source);
    assertTrue(from.contains("sat_customer_v"));
    assertFalse(from.startsWith("("));
  }

  @Test
  void fromClauseSqlWrapsAsSubquery() {
    BvSourceQuery source = new BvSourceQuery();
    source.setName("sat_customer_corrected");
    source.setSourceKind(BvSourceQueryKind.SQL);
    source.setSqlQuery("SELECT customer_hk, x_load_ts FROM sat_customer WHERE deleted = 'N'");

    String from = BvSourceQuerySqlSupport.fromClause(null, new Variables(), source);
    assertTrue(from.startsWith("(SELECT customer_hk"));
    assertTrue(from.endsWith(") src"));
  }

  @Test
  void selectExpressionAliasesWhenNamesDiffer() {
    DatabaseMeta db = new TestDatabaseMeta("Vault");
    assertEquals(
        "view_hk AS customer_hk",
        BvSourceQuerySqlSupport.selectExpression(db, "view_hk", "customer_hk"));
    assertEquals(
        "customer_hk", BvSourceQuerySqlSupport.selectExpression(db, "customer_hk", "customer_hk"));
  }

  @Test
  void buildSelectPlacesWhereAndOrderByOutsideFrom() {
    String sql =
        BvSourceQuerySqlSupport.buildSelect(
            List.of("hk", "ts"),
            "(SELECT hk, ts FROM sat_customer) src",
            List.of("ts > ?"),
            List.of("hk", "ts"));
    assertTrue(sql.contains("FROM (SELECT hk, ts FROM sat_customer) src"));
    assertTrue(sql.contains(" WHERE ts > ?"));
    assertTrue(sql.contains(" ORDER BY hk, ts"));
    assertTrue(sql.indexOf(" WHERE ") < sql.indexOf(" ORDER BY "));
    assertTrue(sql.indexOf("FROM (") < sql.indexOf(" WHERE "));
  }
}
