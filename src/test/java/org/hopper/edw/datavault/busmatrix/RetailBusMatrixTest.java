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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Loads the retail-example RDG and checks the issue #177 sketch table. */
class RetailBusMatrixTest {

  private static final Path RETAIL = Path.of("retail-example").toAbsolutePath().normalize();

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void retailSourcesMatchesSketchTable() throws Exception {
    assumeTrue(Files.isDirectory(RETAIL.resolve("models")), "retail-example models missing");

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", RETAIL.toString());
    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("retail-sources");
    group
        .getDimensionalModelFiles()
        .addAll(
            List.of(
                "${PROJECT_HOME}/models/retail-conformed-dims.hdm",
                "${PROJECT_HOME}/models/retail-f-orders.hdm",
                "${PROJECT_HOME}/models/retail-f-order-lines.hdm",
                "${PROJECT_HOME}/models/retail-f-inventory.hdm"));

    BusMatrix matrix = BusMatrixBuilder.build(group, variables, null);
    assertEquals(3, matrix.getRows().size(), matrix.getWarnings().toString());

    BusMatrixRow orders = row(matrix, "f_orders");
    BusMatrixRow lines = row(matrix, "f_order_lines");
    BusMatrixRow inventory = row(matrix, "f_pit_inventory");

    assertEquals("Retail", orders.business());
    assertEquals("Sales", orders.level1());
    assertEquals("Order management", orders.level2());
    assertEquals("Orders", orders.level3());
    assertEquals("Order lines", lines.level3());
    assertEquals("Inventory", inventory.level1());
    assertEquals("Inventory snapshot", inventory.level3());

    assertEquals(3, orders.cell(columnKey(matrix, "d_date")).roleCount());
    assertTrue(orders.cell(columnKey(matrix, "d_customer")).used());
    assertTrue(orders.cell(columnKey(matrix, "d_order")).used());
    assertTrue(orders.cell(columnKey(matrix, "d_orders_junk")).used());
    assertFalse(orders.cell(columnKey(matrix, "d_product")).used());
    assertFalse(orders.cell(columnKey(matrix, "d_warehouse")).used());
    assertFalse(orders.cell(columnKey(matrix, "qty_class")).used());

    assertEquals(3, lines.cell(columnKey(matrix, "d_date")).roleCount());
    assertTrue(lines.cell(columnKey(matrix, "d_product")).used());
    assertTrue(lines.cell(columnKey(matrix, "d_order")).used());
    assertFalse(lines.cell(columnKey(matrix, "d_customer")).used());
    assertFalse(lines.cell(columnKey(matrix, "d_warehouse")).used());

    assertTrue(inventory.cell(columnKey(matrix, "d_date")).used());
    assertEquals(1, inventory.cell(columnKey(matrix, "d_date")).roleCount());
    assertTrue(inventory.cell(columnKey(matrix, "d_product")).used());
    assertTrue(inventory.cell(columnKey(matrix, "d_warehouse")).used());
    assertTrue(inventory.cell(columnKey(matrix, "qty_class")).used());
    assertFalse(inventory.cell(columnKey(matrix, "d_customer")).used());
    assertFalse(inventory.cell(columnKey(matrix, "d_order")).used());

    String svg = BusMatrixSvgPainter.paint(matrix, false);
    assertTrue(svg.contains("Retail"), svg);
    assertTrue(svg.contains("Order management"), svg);
    assertTrue(svg.contains("Inventory snapshot"), svg);
    assertTrue(svg.contains("preserveAspectRatio=\"xMinYMin meet\""), svg);
  }

  private static BusMatrixRow row(BusMatrix matrix, String factName) {
    return matrix.getRows().stream()
        .filter(row -> factName.equals(row.factName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing fact " + factName));
  }

  private static String columnKey(BusMatrix matrix, String physical) {
    return matrix.getColumns().stream()
        .filter(c -> physical.equals(c.physicalTableName()) || physical.equals(c.label()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing column " + physical))
        .key();
  }
}
