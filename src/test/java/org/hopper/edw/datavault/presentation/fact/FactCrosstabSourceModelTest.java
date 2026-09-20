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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.semantic.bind.HdmSemanticSeed;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FactCrosstabSourceModelTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  @Test
  void starIncludesFactRolesJunkAndRangeColumnOnFact() throws Exception {
    DimensionalModel model = FactCrosstabTestModels.star();
    FactCrosstabSourceModel sources =
        FactCrosstabSourceModel.build(
            model, FactCrosstabTestModels.fact(model), FactCrosstabTestModels.variables(), null);

    assertEquals(5, sources.getTables().size());
    FactCrosstabSourceTable fact = sources.factTable();
    assertNotNull(fact.findColumn("quantity"));
    assertNotNull(fact.findColumn("amount"));
    assertNotNull(fact.findColumn("order_id"));
    assertNotNull(fact.findColumn("qty_band"));
    assertNull(fact.findColumn("customer_key"));

    FactCrosstabSourceTable customer = sources.findTable("dim_customer");
    assertEquals(DmTableType.DIMENSION, customer.getTableType());
    assertEquals("customer_key", customer.getFactForeignKey());
    assertEquals("dim_key", customer.getDimensionKeyColumn());
    assertNotNull(customer.findColumn("customer_name"));
    assertNotNull(customer.findColumn("country"));
    assertNull(customer.findColumn("dim_key"));

    assertEquals(DmTableType.DIMENSION_ALIAS, sources.findTable("dim_order_date").getTableType());
    assertEquals("d_date", sources.findTable("dim_order_date").getPhysicalTableName());
    assertEquals("d_date", sources.findTable("dim_ship_date").getPhysicalTableName());
    assertFalse(
        sources
            .findTable("dim_order_date")
            .getJoinAlias()
            .equals(sources.findTable("dim_ship_date").getJoinAlias()));

    FactCrosstabSourceTable junk = sources.findTable("dim_order_flags");
    assertEquals(DmTableType.JUNK_DIMENSION, junk.getTableType());
    assertNotNull(junk.findColumn("channel"));
  }

  @Test
  void factlessFactListsDegeneratesAndRoles() throws Exception {
    DimensionalModel model = FactCrosstabTestModels.factlessStar();
    IDmFactLikeTable fact = (IDmFactLikeTable) model.findTable("f_coverage");
    FactCrosstabSourceModel sources =
        FactCrosstabSourceModel.build(model, fact, FactCrosstabTestModels.variables(), null);
    assertTrue(sources.factTable().isFact());
    assertNotNull(sources.factTable().findColumn("coverage_id"));
    assertTrue(
        sources.factTable().getColumns().stream()
            .noneMatch(c -> c.getKind() == FactCrosstabSourceColumn.Kind.MEASURE));
    assertNotNull(sources.findTable("dim_customer"));
  }

  @Test
  void fromSemanticUsesLayerFieldsAndJoins() throws Exception {
    DimensionalModel hdm = FactCrosstabTestModels.star();
    hdm.setFilename("/models/sales.hdm");
    SemanticModel semantic = HdmSemanticSeed.seed(hdm, FactCrosstabTestModels.variables(), null);
    SemanticEntity fact = semantic.findEntity("f_order_lines");
    FactCrosstabSourceModel sources =
        FactCrosstabSourceModel.fromSemantic(
            semantic, fact, hdm, FactCrosstabTestModels.variables(), null);

    assertEquals(5, sources.getTables().size());
    assertNotNull(sources.factTable().findColumn("quantity"));
    assertNotNull(sources.factTable().findColumn("amount"));
    assertNotNull(sources.factTable().findColumn("order_id"));
    assertNotNull(sources.factTable().findColumn("qty_band"));
    assertEquals("customer_key", sources.findTable("dim_customer").getFactForeignKey());
    assertEquals("dim_key", sources.findTable("dim_customer").getDimensionKeyColumn());
    assertEquals("d_customer", sources.findTable("dim_customer").getPhysicalTableName());
    assertEquals("d_date", sources.findTable("dim_order_date").getPhysicalTableName());
    assertNotNull(sources.findTable("dim_order_flags").findColumn("channel"));
  }

  @Test
  void fromSemanticOmitsFieldsRemovedFromTheLayer() throws Exception {
    DimensionalModel hdm = FactCrosstabTestModels.star();
    hdm.setFilename("/models/sales.hdm");
    SemanticModel semantic = HdmSemanticSeed.seed(hdm, FactCrosstabTestModels.variables(), null);
    SemanticEntity fact = semantic.findEntity("f_order_lines");
    fact.getMeasures().removeIf(measure -> "quantity".equals(measure.getName()));
    FactCrosstabSourceModel sources =
        FactCrosstabSourceModel.fromSemantic(
            semantic, fact, hdm, FactCrosstabTestModels.variables(), null);
    assertNull(sources.factTable().findColumn("quantity"));
    assertNotNull(sources.factTable().findColumn("amount"));
  }

  @Test
  void twoLayersCanBindTheSameDimensionalModel() throws Exception {
    DimensionalModel hdm = FactCrosstabTestModels.star();
    hdm.setFilename("/models/sales.hdm");
    SemanticModel finance = HdmSemanticSeed.seed(hdm, FactCrosstabTestModels.variables(), null);
    finance.setName("finance");
    SemanticModel ops = HdmSemanticSeed.seed(hdm, FactCrosstabTestModels.variables(), null);
    ops.setName("ops");
    ops.findEntity("f_order_lines")
        .getMeasures()
        .removeIf(measure -> "quantity".equals(measure.getName()));

    assertEquals("/models/sales.hdm", finance.getDimensionalModelFilename());
    assertEquals(finance.getDimensionalModelFilename(), ops.getDimensionalModelFilename());
    assertNotNull(
        FactCrosstabSourceModel.fromSemantic(
                finance,
                finance.findEntity("f_order_lines"),
                hdm,
                FactCrosstabTestModels.variables(),
                null)
            .factTable()
            .findColumn("quantity"));
    assertNull(
        FactCrosstabSourceModel.fromSemantic(
                ops, ops.findEntity("f_order_lines"), hdm, FactCrosstabTestModels.variables(), null)
            .factTable()
            .findColumn("quantity"));
  }
}
