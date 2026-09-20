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
package org.hopper.edw.datavault.presentation.fact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.hopper.core.AggregationMethod;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.semantic.bind.HdmSemanticSeed;
import org.hopper.edw.semantic.model.SemanticModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FactCrosstabSqlBuilderTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  @Test
  void factOnlyColumnsHaveNoJoins() throws Exception {
    FactCrosstabQuery query =
        buildQuery(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField(
                      "f_order_lines", "quantity", "Quantity", AggregationMethod.SUM));
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("f_order_lines", "order_id", "Order"));
            });
    String sql = query.getSql();
    assertTrue(sql.contains("FROM "));
    assertFalse(sql.contains("JOIN "));
    assertTrue(sql.contains("quantity"));
    assertTrue(sql.contains("order_id"));
    assertFalse(sql.contains("d_customer"));
  }

  @Test
  void unusedDimensionsAreOmitted() throws Exception {
    FactCrosstabQuery query =
        buildQuery(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_customer", "customer_name", "Customer"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField(
                      "f_order_lines", "amount", "Amount", AggregationMethod.SUM));
            });
    String sql = query.getSql();
    assertTrue(sql.contains("d_customer"));
    assertFalse(sql.contains("d_date"));
    assertFalse(sql.contains("d_order_flags"));
    assertTrue(sql.contains("customer_key"));
  }

  @Test
  void rolePlayingDatesJoinTheSamePhysicalTableTwice() throws Exception {
    FactCrosstabQuery query =
        buildQuery(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_order_date", "year", "Order year"));
              spec.addField(
                  FactCrosstabSpec.Zone.HORIZONTAL,
                  new FactCrosstabField("dim_ship_date", "year", "Ship year"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField("f_order_lines", "quantity", "Qty", AggregationMethod.SUM));
            });
    String sql = query.getSql();
    int fromDate = count(sql, "d_date");
    assertTrue(fromDate >= 2, sql);
    assertTrue(sql.contains("order_date_key"));
    assertTrue(sql.contains("ship_date_key"));
    assertEquals(3, query.getColumns().size());
    long yearAliases = query.getColumns().stream().map(c -> c.getResultAlias()).distinct().count();
    assertEquals(3, yearAliases);
  }

  @Test
  void junkAndRangeColumnsStayOnFactJoin() throws Exception {
    FactCrosstabQuery query =
        buildQuery(
            spec -> {
              spec.addField(
                  FactCrosstabSpec.Zone.VERTICAL,
                  new FactCrosstabField("dim_order_flags", "channel", "Channel"));
              spec.addField(
                  FactCrosstabSpec.Zone.GROUPS,
                  new FactCrosstabField("f_order_lines", "qty_band", "Qty band"));
              spec.addField(
                  FactCrosstabSpec.Zone.FACTS,
                  new FactCrosstabField("f_order_lines", "quantity", "Qty", AggregationMethod.SUM));
            });
    String sql = query.getSql();
    assertTrue(sql.contains("d_order_flags"));
    assertTrue(sql.contains("flags_key"));
    assertTrue(sql.contains("qty_band"));
    assertFalse(sql.contains("dim_qty_band"));
  }

  @Test
  void aliasesFactTableAndSelectedColumn() throws Exception {
    FactCrosstabQuery query =
        buildQuery(
            spec ->
                spec.addField(
                    FactCrosstabSpec.Zone.FACTS,
                    new FactCrosstabField(
                        "f_order_lines", "quantity", "Quantity", AggregationMethod.SUM)));
    String sql = query.getSql();
    assertTrue(sql.contains("FROM "), sql);
    assertTrue(sql.contains("f_order_lines"), sql);
    assertTrue(sql.contains(" AS "), sql);
    assertTrue(sql.contains("quantity"), sql);
    assertEquals("quantity", query.getColumns().get(0).getResultAlias());
  }

  @Test
  void applyRowLimitAppendsPostgresLimit() throws Exception {
    FactCrosstabQuery query =
        buildQuery(
            spec ->
                spec.addField(
                    FactCrosstabSpec.Zone.FACTS,
                    new FactCrosstabField(
                        "f_order_lines", "quantity", "Quantity", AggregationMethod.SUM)));
    String limited =
        FactCrosstabSqlBuilder.applyRowLimit(
            query.getSql(),
            FactCrosstabTestModels.postgres(),
            FactCrosstabSqlBuilder.DEBUG_ROW_LIMIT);
    assertTrue(limited.toLowerCase().contains("limit 1000"), limited);
    assertTrue(limited.regionMatches(true, 0, "SELECT", 0, 6), limited);
  }

  @Test
  void semanticSourcesJoinTheSameStar() throws Exception {
    DimensionalModel model = FactCrosstabTestModels.star();
    model.setFilename("/models/sales.hdm");
    SemanticModel semantic = HdmSemanticSeed.seed(model, FactCrosstabTestModels.variables(), null);
    FactCrosstabSourceModel sources =
        FactCrosstabSourceModel.fromSemantic(
            semantic,
            semantic.findEntity("f_order_lines"),
            model,
            FactCrosstabTestModels.variables(),
            null);
    FactCrosstabSpec spec = new FactCrosstabSpec();
    spec.setFactTableName("f_order_lines");
    spec.addField(
        FactCrosstabSpec.Zone.VERTICAL,
        new FactCrosstabField("dim_customer", "customer_name", "Customer"));
    spec.addField(
        FactCrosstabSpec.Zone.FACTS,
        new FactCrosstabField("f_order_lines", "amount", "Amount", AggregationMethod.SUM));
    String sql =
        FactCrosstabSqlBuilder.build(
                spec,
                sources,
                FactCrosstabTestModels.postgres(),
                FactCrosstabTestModels.variables())
            .getSql();
    assertTrue(sql.contains("d_customer"));
    assertTrue(sql.contains("customer_key"));
    assertFalse(sql.contains("d_date"));
  }

  @Test
  void emptySpecIsRejected() {
    assertThrows(
        Exception.class,
        () -> {
          DimensionalModel model = FactCrosstabTestModels.star();
          FactCrosstabSourceModel sources =
              FactCrosstabSourceModel.build(
                  model,
                  FactCrosstabTestModels.fact(model),
                  FactCrosstabTestModels.variables(),
                  null);
          FactCrosstabSqlBuilder.build(
              new FactCrosstabSpec(),
              sources,
              FactCrosstabTestModels.postgres(),
              FactCrosstabTestModels.variables());
        });
  }

  private static FactCrosstabQuery buildQuery(SpecWriter writer) throws Exception {
    DimensionalModel model = FactCrosstabTestModels.star();
    FactCrosstabSourceModel sources =
        FactCrosstabSourceModel.build(
            model, FactCrosstabTestModels.fact(model), FactCrosstabTestModels.variables(), null);
    FactCrosstabSpec spec = new FactCrosstabSpec();
    spec.setFactTableName("f_order_lines");
    writer.write(spec);
    DatabaseMeta databaseMeta = FactCrosstabTestModels.postgres();
    return FactCrosstabSqlBuilder.build(
        spec, sources, databaseMeta, FactCrosstabTestModels.variables());
  }

  private static int count(String haystack, String needle) {
    int n = 0;
    int from = 0;
    while (true) {
      int at = haystack.indexOf(needle, from);
      if (at < 0) {
        return n;
      }
      n++;
      from = at + needle.length();
    }
  }

  @FunctionalInterface
  private interface SpecWriter {
    void write(FactCrosstabSpec spec);
  }
}
