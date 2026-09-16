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
import java.util.Map;
import org.junit.jupiter.api.Test;

class BusMatrixLayoutTest {

  @Test
  void ellipsizeFitsPixelBudget() {
    assertEquals("Retail", BusMatrixLayout.ellipsize("Retail", 100, String::length));
    assertEquals("Order…", BusMatrixLayout.ellipsize("Order management", 6, String::length));
  }

  @Test
  void measureWidensFactColumnForLongNames() {
    BusMatrixRow row =
        new BusMatrixRow(
            "f_pit_inventory",
            "f_pit_inventory",
            "one row per warehouse product",
            "FACT",
            "retail-f-inventory.hdm",
            "retail-f-inventory",
            "Retail",
            "Inventory",
            "Stock",
            "Inventory snapshot",
            Map.of());
    BusMatrix matrix = new BusMatrix("retail-sources", List.of(row), List.of(), List.of());
    BusMatrixLayout layout = BusMatrixLayout.measure(matrix, s -> s.length() * 8, 16);
    int margin = BusMatrixLayout.FROZEN_COLUMN_MARGIN;
    assertEquals("Business".length() * 8 + margin, layout.labelWidth(0));
    assertEquals("Inventory".length() * 8 + margin, layout.labelWidth(1));
    assertEquals("Level 2".length() * 8 + margin, layout.labelWidth(2));
    assertEquals("Inventory snapshot".length() * 8 + margin, layout.labelWidth(3));
    assertEquals("f_pit_inventory".length() * 8 + margin, layout.labelWidth(4));
    assertTrue(layout.labelWidth(4) >= layout.labelWidth(0));
    assertTrue(layout.frozenWidth() > 400);
    assertEquals(0, layout.labelX(0));
    assertEquals(layout.labelWidth(0), layout.labelX(1));
  }

  @Test
  void rotatedHeaderHeightGrowsWithLabelWidth() {
    int shortHeader = BusMatrixLayout.rotatedHeaderHeight(40, 16);
    int longHeader = BusMatrixLayout.rotatedHeaderHeight(400, 16);
    assertEquals(BusMatrixLayout.MIN_HEADER_HEIGHT, shortHeader);
    assertTrue(longHeader > shortHeader);
    assertTrue(longHeader <= BusMatrixLayout.MAX_HEADER_HEIGHT);
    assertTrue(BusMatrixLayout.maxLabelPxForHeader(longHeader, 16) >= 80);
  }

  @Test
  void firstTrapeziumStartsAtFrozenWidth() {
    BusMatrixLayout layout = new BusMatrixLayout(new int[] {80, 80, 80, 80, 100}, 52, 200, 26);
    assertEquals(420, layout.frozenWidth());
    int[] first = layout.headerTrapezium(0, 0, 0);
    assertEquals(layout.frozenWidth(), first[0]);
    assertEquals(layout.headerHeight(), first[1]);
  }

  @Test
  void headerLabelInsetKeepsTextInsideTheStrip() {
    assertTrue(BusMatrixLayout.headerLabelInset(200, 70) >= 12);
    assertTrue(BusMatrixLayout.headerLabelInset(200, 70) < 200);
  }

  @Test
  void headerLabelCenterSitsInsideTheParallelogram() {
    float cx = BusMatrixLayout.headerLabelCenterX(420, 52, 200);
    float cy = BusMatrixLayout.headerLabelCenterY(0, 200);
    assertEquals(420 + 26 + 100, cx, 0.1f);
    assertEquals(100, cy, 0.1f);
    int[] trap = BusMatrixLayout.headerTrapezium(420, 0, 52, 200);
    assertTrue(cx > trap[0] && cx < trap[4]);
    assertTrue(cy > trap[5] && cy < trap[1]);
  }

  @Test
  void headerTrapeziumsTessellateAtFortyFiveDegrees() {
    int[] first = BusMatrixLayout.headerTrapezium(100, 0, 36, 200);
    int[] second = BusMatrixLayout.headerTrapezium(136, 0, 36, 200);
    assertEquals(100, first[0]);
    assertEquals(200, first[1]);
    assertEquals(136, first[2]);
    assertEquals(200, first[3]);
    assertEquals(336, first[4]);
    assertEquals(0, first[5]);
    assertEquals(300, first[6]);
    assertEquals(0, first[7]);
    assertEquals(first[2], second[0]);
    assertEquals(first[3], second[1]);
    assertEquals(first[4], second[6]);
    assertEquals(first[5], second[7]);
  }

  @Test
  void widthIncludesHeaderShear() {
    BusMatrixColumn column =
        new BusMatrixColumn("d_date", "d_date", "d_date", "DIMENSION", "star.hdm", "d_date");
    BusMatrix matrix = new BusMatrix("g", List.of(), List.of(column), List.of());
    BusMatrixLayout layout = BusMatrixLayout.measure(matrix, s -> s.length() * 8, 16);
    assertEquals(
        layout.frozenWidth() + layout.cellWidth() + layout.headerShear() + 1, layout.width(matrix));
    assertTrue(layout.headerHeight() >= 200);
  }

  @Test
  void measureKeepsNarrowCellsForTiltedHeaders() {
    BusMatrixColumn column =
        new BusMatrixColumn(
            "d_customer", "d_customer", "d_customer", "DIMENSION", "star.hdm", "d_customer");
    BusMatrixRow row =
        new BusMatrixRow(
            "f_orders",
            "f_orders",
            "one row per order",
            "FACT",
            "star.hdm",
            "star",
            "Retail",
            "Sales",
            "",
            "",
            Map.of());
    BusMatrix matrix = new BusMatrix("g", List.of(row), List.of(column), List.of());
    BusMatrixLayout layout = BusMatrixLayout.measure(matrix, s -> s.length() * 8, 16);
    assertEquals(BusMatrixLayout.MIN_CELL_WIDTH, layout.cellWidth());
    assertTrue(layout.headerHeight() >= BusMatrixLayout.MIN_HEADER_HEIGHT);
  }

  @Test
  void wrapHeaderSplitsOnUnderscoreWhenTooWide() {
    assertEquals(
        List.of("d_customer"), BusMatrixLayout.wrapHeader("d_customer", 20, String::length));
    assertEquals(
        List.of("d", "customer"), BusMatrixLayout.wrapHeader("d_customer", 8, String::length));
    assertTrue(BusMatrixLayout.wrapHeader("", 10, String::length).isEmpty());
  }

  @Test
  void startsGroupWhenBusinessOrLevel1Changes() {
    BusMatrixRow retailSales =
        new BusMatrixRow(
            "f_orders",
            "f_orders",
            "one row per order",
            "FACT",
            "orders.hdm",
            "orders",
            "Retail",
            "Sales",
            "Order management",
            "Orders",
            Map.of());
    BusMatrixRow retailSalesLine =
        new BusMatrixRow(
            "f_order_lines",
            "f_order_lines",
            "one row per line",
            "FACT",
            "lines.hdm",
            "lines",
            "Retail",
            "Sales",
            "Order management",
            "Order lines",
            Map.of());
    BusMatrixRow retailInventory =
        new BusMatrixRow(
            "f_inventory",
            "f_inventory",
            "one row per sku",
            "FACT",
            "inv.hdm",
            "inv",
            "Retail",
            "Inventory",
            "Stock",
            "Snapshot",
            Map.of());
    assertTrue(BusMatrixLayout.startsGroup(null, retailSales));
    assertFalse(BusMatrixLayout.startsGroup(retailSales, retailSalesLine));
    assertTrue(BusMatrixLayout.startsGroup(retailSales, retailInventory));
  }
}
