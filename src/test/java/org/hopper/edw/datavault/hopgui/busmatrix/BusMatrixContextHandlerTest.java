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
package org.hopper.edw.datavault.hopgui.busmatrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.busmatrix.BusMatrixCell;
import org.hopper.edw.datavault.busmatrix.BusMatrixColumn;
import org.hopper.edw.datavault.busmatrix.BusMatrixHit;
import org.hopper.edw.datavault.busmatrix.BusMatrixRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusMatrixContextHandlerTest {

  private BusMatrixRow factRow;
  private BusMatrixRow sqlFactRow;
  private BusMatrixColumn dimCol;
  private BusMatrixCell cell;

  @BeforeEach
  void setUp() {
    factRow =
        new BusMatrixRow(
            "f_orders",
            "f_orders",
            "one row per order",
            "FACT",
            "star.hdm",
            "star",
            "Retail",
            "Sales",
            "Orders",
            "Headers",
            List.of("total_amount"),
            "RECORD_DEFINITION",
            "raw_pos/orders",
            Map.of());

    sqlFactRow =
        new BusMatrixRow(
            "f_inventory",
            "f_inventory",
            "daily stock",
            "FACT",
            "inventory.hdm",
            "inv",
            "Retail",
            "Inventory",
            "Stock",
            "Daily",
            List.of("quantity"),
            "SQL",
            "db_conn",
            Map.of());

    dimCol =
        new BusMatrixColumn(
            "d_customer",
            "Customer",
            "d_customer",
            "DIMENSION",
            "star.hdm",
            "d_customer",
            List.of("customer_id"),
            "SCD Type 2",
            "RECORD_DEFINITION",
            "raw_pos/customer");

    cell = new BusMatrixCell(List.of("order_placed_by"));
  }

  @Test
  void factHitCreatesAllFiveActionsWhenCatalogSourcePresent() {
    BusMatrixHit hit = BusMatrixHit.fact(0, factRow);
    BusMatrixContextHandler handler =
        new BusMatrixContextHandler(null, null, new Variables(), null, hit);
    List<GuiAction> actions = handler.getSupportedActions();

    assertEquals(5, actions.size());
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-fact-open-")));
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-fact-lineage-")));
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-fact-doc-")));
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-fact-ops-")));
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-fact-catalog-")));
  }

  @Test
  void factHitOmitsCatalogActionWhenNotRecordDefinition() {
    BusMatrixHit hit = BusMatrixHit.fact(0, sqlFactRow);
    BusMatrixContextHandler handler =
        new BusMatrixContextHandler(null, null, new Variables(), null, hit);
    List<GuiAction> actions = handler.getSupportedActions();

    assertEquals(4, actions.size());
    assertFalse(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-fact-catalog-")));
  }

  @Test
  void dimensionHitCreatesAllFiveActionsWhenCatalogSourcePresent() {
    BusMatrixHit hit = BusMatrixHit.dimension(0, dimCol);
    BusMatrixContextHandler handler =
        new BusMatrixContextHandler(null, null, new Variables(), null, hit);
    List<GuiAction> actions = handler.getSupportedActions();

    assertEquals(5, actions.size());
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-dim-open-")));
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-dim-lineage-")));
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-dim-doc-")));
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-dim-ops-")));
    assertTrue(actions.stream().anyMatch(a -> a.getId().startsWith("bus-matrix-dim-catalog-")));
  }

  @Test
  void cellHitCombinesFactAndDimensionActionsIntoDistinctCategories() {
    BusMatrixHit hit = BusMatrixHit.cell(0, 0, factRow, dimCol, cell);
    BusMatrixContextHandler handler =
        new BusMatrixContextHandler(null, null, new Variables(), null, hit);
    List<GuiAction> actions = handler.getSupportedActions();

    // 5 fact actions + 5 dimension actions = 10 actions
    assertEquals(10, actions.size());

    long factActions =
        actions.stream().filter(a -> a.getId().startsWith("bus-matrix-fact-")).count();
    long dimActions =
        actions.stream().filter(a -> a.getId().startsWith("bus-matrix-dim-")).count();

    assertEquals(5, factActions);
    assertEquals(5, dimActions);

    // Each group has a category set
    actions.forEach(a -> assertTrue(a.getCategory() != null && !a.getCategory().isBlank()));
  }

  @Test
  void noneHitProducesNoActions() {
    BusMatrixHit hit = BusMatrixHit.none();
    BusMatrixContextHandler handler =
        new BusMatrixContextHandler(null, null, new Variables(), null, hit);
    assertTrue(handler.getSupportedActions().isEmpty());
  }
}
