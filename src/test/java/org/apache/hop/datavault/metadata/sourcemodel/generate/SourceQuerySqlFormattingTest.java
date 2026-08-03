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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;

class SourceQuerySqlFormattingTest {

  @Test
  void formatForDisplayBreaksJoinsAndSelectListOntoSeparateLines() throws Exception {
    SourceModel model = productLookupModel();
    SourceQuery query = model.findQuery("feed_product_enriched");
    String sql = SourceQuerySqlGenerator.generate(model, query, null, new Variables());
    String formatted = DvSqlSupport.formatForDisplay(sql);

    assertTrue(formatted.contains("\n"), formatted);
    assertTrue(formatted.toUpperCase().contains("LEFT OUTER JOIN"), formatted);
    // OUTER JOIN form is recognized as its own clause (not stuck on the FROM line).
    assertTrue(
        formatted.lines().anyMatch(line -> line.trim().toUpperCase().startsWith("LEFT OUTER JOIN")),
        formatted);
    assertTrue(
        formatted.lines().anyMatch(line -> line.trim().toUpperCase().startsWith("FROM")),
        formatted);
  }

  @Test
  void selectListWrapsNear100CharsWithSevenSpaceContinuation() {
    StringBuilder select = new StringBuilder("SELECT ");
    for (int i = 1; i <= 40; i++) {
      if (i > 1) {
        select.append(", ");
      }
      select.append("t.col_").append(i).append(" AS col_").append(i);
    }
    select.append(" FROM public.t t");

    String formatted = DvSqlSupport.formatForDisplay(select.toString());
    String[] lines = formatted.split("\n", -1);

    assertTrue(lines[0].toUpperCase().startsWith("SELECT "), formatted);
    assertTrue(
        formatted.lines().anyMatch(line -> line.trim().toUpperCase().startsWith("FROM")),
        formatted);

    // Continuation lines use exactly 7 leading spaces (no base indent at top level).
    String continuation = " ".repeat(7);
    boolean sawContinuation = false;
    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];
      if (line.trim().isEmpty() || line.trim().toUpperCase().startsWith("FROM")) {
        continue;
      }
      assertTrue(
          line.startsWith(continuation) && !line.startsWith(continuation + " "),
          "expected 7-space indent on continuation, got: [" + line + "]\n" + formatted);
      sawContinuation = true;
      // Soft wrap: no continuation line should be vastly over the limit (single long field exempt).
      assertTrue(
          line.length() <= 120 || !line.contains(","),
          "continuation line too long without natural break: " + line.length() + "\n" + formatted);
    }
    assertTrue(sawContinuation, "expected at least one wrapped SELECT line:\n" + formatted);

    // First SELECT line should not grow unbounded with dozens of short fields.
    assertTrue(
        lines[0].length() <= 110,
        "SELECT line should wrap near 100 chars, was " + lines[0].length() + ":\n" + lines[0]);
  }

  @Test
  void relationSupportListsRelatedTablesAndResolvedKeys() {
    SourceModel model = productLookupModel();
    SourceQueryJoin join = model.findQuery("feed_product_enriched").getJoins().get(0);
    join.setRelationshipName(null);
    join.setLeftColumns(List.of());
    join.setRightColumns(List.of());

    List<String> related =
        SourceQueryRelationSupport.relatedTableNames(model, java.util.Set.of("product"));
    assertTrue(related.contains("product_type"), related.toString());

    String keys =
        SourceQueryRelationSupport.formatResolvedKeys(model, join, java.util.Set.of("product"));
    assertTrue(keys.contains("type_id"), keys);
    assertTrue(keys.contains("="), keys);
  }

  private static SourceModel productLookupModel() {
    SourceModel model = new SourceModel();
    SourceTable product = new SourceTable("product");
    product.setTableName("product");
    product.setDatabaseName("CRM");
    product.setPhysicalType(DvSourceType.DATABASE);
    product.getColumns().add(pk("product_id"));
    product.getColumns().add(col("type_id"));
    model.getTables().add(product);

    SourceTable productType = new SourceTable("product_type");
    productType.setTableName("product_type");
    productType.setDatabaseName("CRM");
    productType.setPhysicalType(DvSourceType.DATABASE);
    productType.getColumns().add(pk("type_id"));
    productType.getColumns().add(col("type_name"));
    model.getTables().add(productType);

    SourceRelationship fk = new SourceRelationship("fk_product_type");
    fk.setChildTableName("product");
    fk.setParentTableName("product_type");
    fk.setChildColumns(List.of("type_id"));
    fk.setParentColumns(List.of("type_id"));
    fk.setDefaultJoinType(SourceJoinType.LEFT);
    model.getRelationships().add(fk);

    SourceQuery query = new SourceQuery("feed_product_enriched");
    query.setDrivingTableName("product");
    SourceQueryJoin join = new SourceQueryJoin();
    join.setTableName("product_type");
    join.setJoinType(SourceJoinType.LEFT);
    query.getJoins().add(join);
    SourceQueryColumn productId = new SourceQueryColumn("product", "product_id");
    productId.setPrimaryKeyPosition(1);
    query.getColumns().add(productId);
    query.getColumns().add(new SourceQueryColumn("product_type", "type_name"));
    model.getQueries().add(query);
    return model;
  }

  private static SourceColumn pk(String name) {
    SourceColumn column = col(name);
    column.setPrimaryKeyPosition(1);
    return column;
  }

  private static SourceColumn col(String name) {
    SourceColumn column = new SourceColumn(name);
    column.setHopType(2);
    return column;
  }
}
