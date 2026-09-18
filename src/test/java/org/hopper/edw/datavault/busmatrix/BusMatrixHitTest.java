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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusMatrixHitTest {

  private BusMatrix matrix;
  private BusMatrixLayout layout;

  @BeforeEach
  void setUp() {
    BusMatrixColumn dimDate =
        new BusMatrixColumn(
            "d_date",
            "d_date",
            "d_date",
            "DIMENSION",
            "star.hdm",
            "d_date",
            List.of("date_key"),
            "SCD Type 1",
            "RECORD_DEFINITION",
            "raw_pos/date");
    BusMatrixColumn dimCustomer =
        new BusMatrixColumn(
            "d_customer",
            "d_customer",
            "d_customer",
            "DIMENSION",
            "star.hdm",
            "d_customer",
            List.of("customer_id"),
            "SCD Type 2",
            "RECORD_DEFINITION",
            "raw_pos/customer");

    BusMatrixCell cellDate = new BusMatrixCell(List.of("order_date", "ship_date"));
    BusMatrixCell cellCustomer = new BusMatrixCell(List.of("buyer"));

    BusMatrixRow rowOrders =
        new BusMatrixRow(
            "f_orders",
            "f_orders",
            "one row per order header",
            "FACT",
            "star.hdm",
            "star",
            "Retail",
            "Sales",
            "Orders",
            "",
            List.of("order_total", "tax_amount"),
            "RECORD_DEFINITION",
            "raw_pos/orders",
            Map.of("d_date", cellDate, "d_customer", cellCustomer));

    matrix = new BusMatrix("retail", List.of(rowOrders), List.of(dimDate, dimCustomer), List.of());
    // frozenWidth: 100*5 = 500, cellWidth: 50, headerHeight: 200, rowHeight: 30
    layout = new BusMatrixLayout(new int[] {100, 100, 100, 100, 100}, 50, 200, 30);
  }

  @Test
  void headerHitDetectsColumnsAlongFortyFiveDegreeShear() {
    // layout.frozenWidth() = 500, headerHeight = 200, cellWidth = 50
    // At y = 200 (bottom of header): headerShear = 0.
    // col 0 (d_date) starts at x = 500, col 1 (d_customer) starts at x = 550.
    // But y = 200 is row zone, so test at y = 199: headerShear = 1.
    // col 0: 501 .. 550.
    BusMatrixHit hit0Bottom = BusMatrixHit.hitAt(520, 199, matrix, layout);
    assertTrue(hit0Bottom.isDimension());
    assertEquals("d_date", hit0Bottom.getColumn().dimensionName());

    BusMatrixHit hit1Bottom = BusMatrixHit.hitAt(570, 199, matrix, layout);
    assertTrue(hit1Bottom.isDimension());
    assertEquals("d_customer", hit1Bottom.getColumn().dimensionName());

    // At y = 100 (mid-height): headerShear = 200 - 100 = 100.
    // col 0 is now shifted right by 100: x from 600 to 650!
    // At x = 520, y = 100: this is in the empty space to the left of the sheared columns -> NONE!
    BusMatrixHit hitEmpty = BusMatrixHit.hitAt(520, 100, matrix, layout);
    assertFalse(hitEmpty.isDimension());
    assertEquals(BusMatrixHit.HitType.NONE, hitEmpty.getType());

    // At x = 620, y = 100: exactly in col 0 (d_date)!
    BusMatrixHit hit0Mid = BusMatrixHit.hitAt(620, 100, matrix, layout);
    assertTrue(hit0Mid.isDimension());
    assertEquals("d_date", hit0Mid.getColumn().dimensionName());

    // At x = 670, y = 100: exactly in col 1 (d_customer)!
    BusMatrixHit hit1Mid = BusMatrixHit.hitAt(670, 100, matrix, layout);
    assertTrue(hit1Mid.isDimension());
    assertEquals("d_customer", hit1Mid.getColumn().dimensionName());

    // At y = 0 (top of header): headerShear = 200.
    // col 0 is at x = 700 to 750.
    // col 1 is at x = 750 to 800.
    BusMatrixHit hit0Top = BusMatrixHit.hitAt(720, 0, matrix, layout);
    assertTrue(hit0Top.isDimension());
    assertEquals("d_date", hit0Top.getColumn().dimensionName());

    BusMatrixHit hit1Top = BusMatrixHit.hitAt(770, 0, matrix, layout);
    assertTrue(hit1Top.isDimension());
    assertEquals("d_customer", hit1Top.getColumn().dimensionName());

    // To the right of all columns -> NONE
    BusMatrixHit hitPastRight = BusMatrixHit.hitAt(850, 0, matrix, layout);
    assertEquals(BusMatrixHit.HitType.NONE, hitPastRight.getType());
  }

  @Test
  void factHitDetectsRowInFrozenColumns() {
    // Row 0 is at y = 200 .. 229, frozen columns at x = 0 .. 499
    BusMatrixHit hitFact = BusMatrixHit.hitAt(250, 210, matrix, layout);
    assertTrue(hitFact.isFact());
    assertFalse(hitFact.isDimension());
    assertFalse(hitFact.isCell());
    assertEquals("f_orders", hitFact.getRow().factName());
    assertEquals(0, hitFact.getRowIndex());
  }

  @Test
  void cellHitDetectsIntersectionOfFactAndDimension() {
    // Row 0 (y = 200..229)
    // Col 0 (d_date): x = 500..549
    BusMatrixHit hitCell0 = BusMatrixHit.hitAt(525, 210, matrix, layout);
    assertTrue(hitCell0.isCell());
    assertEquals(0, hitCell0.getRowIndex());
    assertEquals(0, hitCell0.getColumnIndex());
    assertEquals("f_orders", hitCell0.getRow().factName());
    assertEquals("d_date", hitCell0.getColumn().dimensionName());
    assertEquals(List.of("order_date", "ship_date"), hitCell0.getCell().roleNames());

    // Col 1 (d_customer): x = 550..599
    BusMatrixHit hitCell1 = BusMatrixHit.hitAt(575, 210, matrix, layout);
    assertTrue(hitCell1.isCell());
    assertEquals(0, hitCell1.getRowIndex());
    assertEquals(1, hitCell1.getColumnIndex());
    assertEquals("d_customer", hitCell1.getColumn().dimensionName());
    assertEquals(List.of("buyer"), hitCell1.getCell().roleNames());

    // Past columns: x >= 600
    BusMatrixHit hitPastGrid = BusMatrixHit.hitAt(650, 210, matrix, layout);
    assertEquals(BusMatrixHit.HitType.NONE, hitPastGrid.getType());
  }

  @Test
  void outOfBoundsReturnsNone() {
    assertEquals(BusMatrixHit.HitType.NONE, BusMatrixHit.hitAt(-10, 50, matrix, layout).getType());
    assertEquals(BusMatrixHit.HitType.NONE, BusMatrixHit.hitAt(50, -10, matrix, layout).getType());
    assertEquals(BusMatrixHit.HitType.NONE, BusMatrixHit.hitAt(50, 500, matrix, layout).getType());
    assertEquals(BusMatrixHit.HitType.NONE, BusMatrixHit.hitAt(50, 50, null, layout).getType());
  }

  @Test
  void tooltipsProvideRichDetails() {
    BusMatrixHit hitDim = BusMatrixHit.hitAt(520, 199, matrix, layout);
    String dimTooltip = hitDim.tooltip();
    assertNotNull(dimTooltip);
    assertTrue(dimTooltip.contains("Dimension: d_date"));
    assertTrue(dimTooltip.contains("SCD Nature: SCD Type 1"));
    assertTrue(dimTooltip.contains("Natural Key(s): date_key"));
    assertTrue(dimTooltip.contains("Source: RECORD_DEFINITION"));

    BusMatrixHit hitFact = BusMatrixHit.hitAt(250, 210, matrix, layout);
    String factTooltip = hitFact.tooltip();
    assertNotNull(factTooltip);
    assertTrue(factTooltip.contains("Fact: f_orders"));
    assertTrue(factTooltip.contains("Granularity: one row per order header"));
    assertTrue(factTooltip.contains("Measures (2): order_total, tax_amount"));
    assertTrue(factTooltip.contains("Source: RECORD_DEFINITION"));

    BusMatrixHit hitCell = BusMatrixHit.hitAt(525, 210, matrix, layout);
    String cellTooltip = hitCell.tooltip();
    assertNotNull(cellTooltip);
    assertTrue(cellTooltip.contains("Fact: f_orders"));
    assertTrue(cellTooltip.contains("Granularity: one row per order header"));
    assertTrue(cellTooltip.contains("Dimension: d_date"));
    assertTrue(cellTooltip.contains("Roles (2): order_date, ship_date"));

    // Verify undefined grain fallback
    BusMatrixRow emptyGrainRow =
        new BusMatrixRow(
            "f_empty", "f_empty", "", "FACT", "star.hdm", "star", "", "", "", "", Map.of());
    assertTrue(emptyGrainRow.tooltip().contains("Granularity: (not defined)"));
  }

  @Test
  void targetNameFormatsCorrectly() {
    BusMatrixHit hitDim = BusMatrixHit.hitAt(520, 199, matrix, layout);
    assertEquals("d_date", hitDim.targetName());

    BusMatrixHit hitFact = BusMatrixHit.hitAt(250, 210, matrix, layout);
    assertEquals("f_orders", hitFact.targetName());

    BusMatrixHit hitCell = BusMatrixHit.hitAt(525, 210, matrix, layout);
    assertEquals("f_orders \u00D7 d_date", hitCell.targetName());
  }
}
