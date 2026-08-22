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
package org.apache.hop.datavault.metadata.dimensional.dbimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmBridge;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionScdType;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactlessFact;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmDatabaseTableImportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void inferTableTypeUsesNamingHeuristics() {
    assertEquals(DmTableType.DIMENSION, DmDatabaseTableImportSupport.inferTableType("d_customer"));
    assertEquals(DmTableType.DIMENSION, DmDatabaseTableImportSupport.inferTableType("dim_product"));
    assertEquals(DmTableType.FACT, DmDatabaseTableImportSupport.inferTableType("f_sales"));
    assertEquals(DmTableType.FACT, DmDatabaseTableImportSupport.inferTableType("fact_orders"));
    assertEquals(
        DmTableType.BRIDGE, DmDatabaseTableImportSupport.inferTableType("bridge_customer_product"));
    assertEquals(
        DmTableType.FACTLESS_FACT,
        DmDatabaseTableImportSupport.inferTableType("factless_coverage"));
    assertEquals(
        DmTableType.FACTLESS_FACT,
        DmDatabaseTableImportSupport.inferTableType("f_factless_coverage"));
  }

  @Test
  void buildDimensionFromCustomerTableLayout() throws Exception {
    DimensionalModel model = new DimensionalModel();
    IRowMeta rowMeta = customerDimensionLayout();

    DmDimension dimension =
        (DmDimension)
            DmDatabaseTableImportSupport.buildTableFromRowMeta(
                "d_customer",
                rowMeta,
                model,
                model.getConfigurationOrDefault(),
                DmDatabaseImportOptions.defaults(),
                new Variables(),
                0);

    assertEquals("dim_customer", dimension.getName());
    assertEquals(DmDimensionScdType.TYPE1, dimension.getScdTypeOrDefault());
    assertEquals("customer_id", dimension.getNaturalKeysOrEmpty().get(0).getFieldName());
    assertEquals(
        Set.of("customer_name", "city"),
        dimension.getAttributesOrEmpty().stream()
            .map(attr -> attr.getFieldName())
            .collect(Collectors.toSet()));
    assertTrue(dimension.getSourceOrDefault().getSourceSql().contains("d_customer"));
    assertTrue(dimension.getSourceOrDefault().getSourceSql().contains("customer_name"));
  }

  @Test
  void buildFactResolvesDimensionRolesFromForeignKeys() throws Exception {
    DimensionalModel model = new DimensionalModel();
    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.setTableName("d_customer");
    model.getTables().add(customer);

    IRowMeta rowMeta = salesFactLayout();
    DmFact fact =
        (DmFact)
            DmDatabaseTableImportSupport.buildTableFromRowMeta(
                "f_sales",
                rowMeta,
                model,
                model.getConfigurationOrDefault(),
                DmDatabaseImportOptions.defaults(),
                new Variables(),
                1);

    assertEquals("fact_sales", fact.getName());
    assertEquals(1, fact.getDimensionRolesOrEmpty().size());
    assertEquals("dim_customer", fact.getDimensionRolesOrEmpty().get(0).getDimensionTableName());
    assertEquals("customer", fact.getDimensionRolesOrEmpty().get(0).getSourceFieldName());
    assertEquals(
        Set.of("quantity", "amount"),
        fact.getMeasuresOrEmpty().stream()
            .map(measure -> measure.getFieldName())
            .collect(Collectors.toSet()));
  }

  @Test
  void buildBridgeAndFactlessLayouts() throws Exception {
    DimensionalModel model = new DimensionalModel();

    DmBridge bridge =
        (DmBridge)
            DmDatabaseTableImportSupport.buildTableFromRowMeta(
                "bridge_customer_product",
                bridgeLayout(),
                model,
                model.getConfigurationOrDefault(),
                DmDatabaseImportOptions.defaults(),
                new Variables(),
                0);
    assertEquals(2, bridge.getDimensionRefsOrEmpty().size());
    assertEquals("allocation_pct", bridge.getWeightField());

    DmFactlessFact factless =
        assertInstanceOf(
            DmFactlessFact.class,
            DmDatabaseTableImportSupport.buildTableFromRowMeta(
                "factless_coverage",
                factlessLayout(),
                model,
                model.getConfigurationOrDefault(),
                DmDatabaseImportOptions.defaults(),
                new Variables(),
                1));
    assertEquals(2, factless.getDimensionRolesOrEmpty().size());
  }

  private static IRowMeta customerDimensionLayout() throws HopException {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("dim_key"));
    rowMeta.addValueMeta(new ValueMetaInteger("customer_id"));
    rowMeta.addValueMeta(new ValueMetaString("customer_name"));
    rowMeta.addValueMeta(new ValueMetaString("city"));
    rowMeta.addValueMeta(new ValueMetaString("load_dt"));
    return rowMeta;
  }

  private static IRowMeta salesFactLayout() throws HopException {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("customer_key"));
    rowMeta.addValueMeta(new ValueMetaInteger("quantity"));
    rowMeta.addValueMeta(new ValueMetaNumber("amount"));
    return rowMeta;
  }

  private static IRowMeta bridgeLayout() throws HopException {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("customer_key"));
    rowMeta.addValueMeta(new ValueMetaInteger("product_key"));
    rowMeta.addValueMeta(new ValueMetaNumber("allocation_pct"));
    return rowMeta;
  }

  private static IRowMeta factlessLayout() throws HopException {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("customer_key"));
    rowMeta.addValueMeta(new ValueMetaInteger("product_key"));
    return rowMeta;
  }
}
