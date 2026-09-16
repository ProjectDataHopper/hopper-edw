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
package org.hopper.edw.datavault.busmatrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmRangeDimension;
import org.junit.jupiter.api.Test;

class BusMatrixBuilderTest {

  @Test
  void collapsesRolePlayingDatesAndKeepsUnusedDimensions() {
    DimensionalModel model = new DimensionalModel();
    model.setName("star");
    model.setFilename("star.hdm");

    DmDimension date = dimension("d_date", "d_date");
    DmDimension customer = dimension("d_customer", "d_customer");
    DmDimension unused = dimension("d_store", "d_store");
    DmDimensionAlias orderDate = alias("d_order_date", "d_date");
    DmDimensionAlias shipDate = alias("d_shipping_date", "d_date");
    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("d_orders_junk");
    junk.setTableName("d_orders_junk");

    DmFact fact = new DmFact();
    fact.setName("f_orders");
    fact.setTableName("f_orders");
    fact.setGrain("one row per order");
    fact.getBusinessProcessOrEmpty().setBusiness("Retail");
    fact.getBusinessProcessOrEmpty().setLevel1("Sales");
    fact.getBusinessProcessOrEmpty().setLevel2("Order management");
    fact.getBusinessProcessOrEmpty().setLevel3("Orders");
    fact.getDimensionRoles()
        .addAll(
            List.of(
                new DmFactDimensionRole("d_customer", "customer_hk"),
                new DmFactDimensionRole("d_order_date", "order_date_key"),
                new DmFactDimensionRole("d_shipping_date", "shipping_date_key")));
    fact.getJunkDimensionRoles()
        .add(new DmFactJunkDimensionRole("d_orders_junk", "orders_junk_hk"));

    model.getTables().addAll(List.of(date, customer, unused, orderDate, shipDate, junk, fact));

    BusMatrix matrix = BusMatrixBuilder.build("retail", List.of(model), new Variables(), null);

    assertEquals(1, matrix.getRows().size());
    BusMatrixRow row = matrix.getRows().get(0);
    assertEquals("f_orders", row.factName());
    assertEquals("Retail", row.business());
    assertEquals("Orders", row.level3());

    assertEquals(
        1,
        matrix.getColumns().stream().filter(c -> "d_date".equals(c.physicalTableName())).count());
    assertEquals(2, row.cell(columnKey(matrix, "d_date")).roleCount());
    assertEquals("2", row.cell(columnKey(matrix, "d_date")).mark());
    assertTrue(row.cell(columnKey(matrix, "d_customer")).used());
    assertTrue(row.cell(columnKey(matrix, "d_orders_junk")).used());
    assertFalse(row.cell(columnKey(matrix, "d_store")).used());

    BusMatrix hidden = matrix.filtered(null, null, null, null, true);
    assertTrue(
        hidden.getColumns().stream().noneMatch(c -> "d_store".equals(c.physicalTableName())));
  }

  @Test
  void csvContainsBomAndFactName() {
    DimensionalModel model = new DimensionalModel();
    DmFact fact = new DmFact();
    fact.setName("f_orders");
    model.getTables().add(fact);
    BusMatrix matrix = BusMatrixBuilder.build("g", List.of(model), new Variables(), null);
    String csv = BusMatrixCsvWriter.write(matrix);
    assertTrue(csv.startsWith("\uFEFF"));
    assertTrue(csv.contains("f_orders"));
    assertTrue(csv.contains("Model file"));
  }

  @Test
  void htmlMarksGroupStartsAndWrapsDimensionLabels() {
    DimensionalModel model = new DimensionalModel();
    model.setName("star");
    model.setFilename("star.hdm");
    DmDimension customer = dimension("d_customer", "d_customer");
    DmFact orders = new DmFact();
    orders.setName("f_orders");
    orders.getBusinessProcessOrEmpty().setBusiness("Retail");
    orders.getBusinessProcessOrEmpty().setLevel1("Sales");
    orders.getDimensionRoles().add(new DmFactDimensionRole("d_customer", "customer_hk"));
    DmFact inventory = new DmFact();
    inventory.setName("f_inventory");
    inventory.getBusinessProcessOrEmpty().setBusiness("Retail");
    inventory.getBusinessProcessOrEmpty().setLevel1("Inventory");
    inventory.getDimensionRoles().add(new DmFactDimensionRole("d_customer", "customer_hk"));
    model.getTables().addAll(List.of(customer, orders, inventory));
    BusMatrix matrix = BusMatrixBuilder.build("retail", List.of(model), new Variables(), null);
    String html = BusMatrixHtmlRenderer.table(matrix, name -> "tables/dm/" + name + ".html");
    assertTrue(html.contains("hop-doc-bus-matrix-group"));
    assertTrue(html.contains("hop-doc-bus-matrix-dim-label"));
    assertTrue(html.contains("d_customer"));
    assertTrue(html.contains("f_orders"));
  }

  @Test
  void svgContainsMarkForUsedCell() {
    DimensionalModel model = new DimensionalModel();
    DmDimension customer = dimension("d_customer", "d_customer");
    DmFact fact = new DmFact();
    fact.setName("f_orders");
    fact.getDimensionRoles().add(new DmFactDimensionRole("d_customer", "customer_hk"));
    model.getTables().addAll(List.of(customer, fact));
    BusMatrix matrix = BusMatrixBuilder.build("g", List.of(model), new Variables(), null);
    String svg = BusMatrixSvgPainter.paint(matrix, false);
    assertTrue(svg.contains("<svg"));
    assertTrue(svg.contains(">X</text>") || svg.contains(">X<"));
    assertTrue(svg.contains("rotate(-45"), svg);
    assertTrue(svg.contains("#c9e8fb") || svg.contains("#ecf5fa"), svg);
    assertTrue(svg.contains("<polygon"), svg);
  }

  @Test
  void marksRangeRolesAndWarnsOnUnresolvedAlias() {
    DimensionalModel model = new DimensionalModel();
    model.setName("star");
    DmRangeDimension qty = new DmRangeDimension();
    qty.setName("qty_class");
    qty.setTableName("qty_class");
    DmDimensionAlias ghost = alias("d_ghost", "d_missing");
    DmFact fact = new DmFact();
    fact.setName("f_inv");
    fact.getRangeDimensionRoles()
        .add(new DmFactRangeDimensionRole("qty_class", "qty", "qty_class"));
    fact.getDimensionRoles().add(new DmFactDimensionRole("d_ghost", "ghost_key"));
    model.getTables().addAll(List.of(qty, ghost, fact));

    BusMatrix matrix = BusMatrixBuilder.build("g", List.of(model), new Variables(), null);
    assertTrue(matrix.getRows().get(0).cell(columnKey(matrix, "qty_class")).used());
    assertTrue(
        matrix.getWarnings().stream().anyMatch(w -> w.toLowerCase().contains("unresolved")),
        matrix.getWarnings().toString());
  }

  private static String columnKey(BusMatrix matrix, String physical) {
    return matrix.getColumns().stream()
        .filter(c -> physical.equals(c.physicalTableName()) || physical.equals(c.label()))
        .findFirst()
        .orElseThrow()
        .key();
  }

  private static DmDimension dimension(String name, String tableName) {
    DmDimension dimension = new DmDimension();
    dimension.setName(name);
    dimension.setTableName(tableName);
    return dimension;
  }

  private static DmDimensionAlias alias(String name, String target) {
    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName(name);
    alias.setReferencedDimensionName(target);
    return alias;
  }
}
