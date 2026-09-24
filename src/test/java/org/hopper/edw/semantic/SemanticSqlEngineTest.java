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
package org.hopper.edw.semantic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.hopper.core.AggregationMethod;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabTestModels;
import org.hopper.edw.semantic.bind.HdmSemanticSeed;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticSelection;
import org.hopper.edw.semantic.model.SemanticSelectionField;
import org.hopper.edw.semantic.query.SemanticQueryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SemanticSqlEngineTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  @Test
  void factOnlyHasNoJoin() throws Exception {
    String sql =
        sql(
            selection -> {
              selection.getMeasures().add(new SemanticSelectionField("f_order_lines", "quantity"));
            });
    assertTrue(sql.toLowerCase().contains("f_order_lines"), sql);
    assertFalse(sql.toLowerCase().contains(" join "), sql);
    assertTrue(sql.toUpperCase().contains("SUM("), sql);
    assertFalse(sql.toUpperCase().contains("GROUP BY"), sql);
  }

  @Test
  void unusedDimensionsOmitted() throws Exception {
    String sql =
        sql(
            selection -> {
              selection.getRows().add(new SemanticSelectionField("dim_customer", "customer_name"));
              selection.getMeasures().add(new SemanticSelectionField("f_order_lines", "amount"));
            });
    assertTrue(
        sql.toLowerCase().contains("d_customer") || sql.toLowerCase().contains("dim_customer"),
        sql);
    assertFalse(
        sql.toLowerCase().contains("d_date")
            && sql.toLowerCase().contains("join")
            && sql.contains("order_date"),
        sql);
  }

  @Test
  void rolePlayingDatesJoinTwice() throws Exception {
    String sql =
        sql(
            selection -> {
              selection.getColumns().add(new SemanticSelectionField("dim_order_date", "year"));
              selection.getColumns().add(new SemanticSelectionField("dim_ship_date", "year"));
              selection.getMeasures().add(new SemanticSelectionField("f_order_lines", "quantity"));
            });
    String lower = sql.toLowerCase();
    assertTrue(lower.contains("order_date") || lower.contains("dim_order_date"), sql);
    assertTrue(lower.contains("ship_date") || lower.contains("dim_ship_date"), sql);
    assertTrue(sql.toUpperCase().contains("SUM("), sql);
    assertTrue(sql.toUpperCase().contains("GROUP BY"), sql);
  }

  @Test
  void selectionAggregationIsPushedIntoSql() throws Exception {
    String sql =
        sql(
            selection -> {
              selection.getRows().add(new SemanticSelectionField("dim_customer", "customer_name"));
              SemanticSelectionField counted =
                  new SemanticSelectionField("f_order_lines", "quantity");
              counted.setAggregationMethod(AggregationMethod.COUNT);
              selection.getMeasures().add(counted);
              SemanticSelectionField averaged = new SemanticSelectionField("f_order_lines", "amount");
              averaged.setAggregationMethod(AggregationMethod.AVERAGE);
              selection.getMeasures().add(averaged);
            });
    String upper = sql.toUpperCase();
    assertTrue(upper.contains("COUNT("), sql);
    assertTrue(upper.contains("AVG("), sql);
    int groupBy = upper.indexOf("GROUP BY");
    assertTrue(groupBy > 0, sql);
    assertTrue(upper.substring(groupBy).contains("CUSTOMER_NAME"), sql);
    assertFalse(upper.substring(groupBy).contains("QUANTITY"), sql);
    assertFalse(upper.substring(groupBy).contains("AMOUNT"), sql);
  }

  private static String sql(SelectionWriter writer) throws Exception {
    DimensionalModel hdm = FactCrosstabTestModels.star();
    hdm.setFilename("/models/sales.hdm");
    SemanticModel model = HdmSemanticSeed.seed(hdm, new Variables(), null);
    SemanticSelection selection = new SemanticSelection();
    selection.setName("test");
    selection.setEntityName("f_order_lines");
    writer.write(selection);
    return SemanticQueryService.sql(
        model, selection, FactCrosstabTestModels.postgres(), new Variables());
  }

  @FunctionalInterface
  private interface SelectionWriter {
    void write(SemanticSelection selection);
  }
}
