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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DimensionalModelConformedDimensionTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void removeConformedDimensionRefsForTableDropsMatchingRegistryEntry() {
    DimensionalModel model = new DimensionalModel();
    model.getConformedDimensions().add(new DmConformedDimensionRef("Customer", "dim_customer"));
    model.getConformedDimensions().add(new DmConformedDimensionRef("Product", "dim_product"));

    model.removeConformedDimensionRefsForTable("dim_customer");

    assertEquals(1, model.getConformedDimensionsOrEmpty().size());
    assertEquals("Product", model.getConformedDimensionsOrEmpty().get(0).getLogicalName());
    assertEquals(
        "dim_product", model.getConformedDimensionsOrEmpty().get(0).getDimensionTableName());
  }

  @Test
  void removeConformedDimensionRefsForTablesHandlesBulkDelete() {
    DimensionalModel model = new DimensionalModel();
    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    DmDimension product = new DmDimension();
    product.setName("dim_product");
    model.getConformedDimensions().add(new DmConformedDimensionRef("Customer", "dim_customer"));
    model.getConformedDimensions().add(new DmConformedDimensionRef("Product", "dim_product"));
    model.getConformedDimensions().add(new DmConformedDimensionRef("Date", "dim_date"));

    model.removeConformedDimensionRefsForTables(List.of(customer, product));

    assertEquals(1, model.getConformedDimensionsOrEmpty().size());
    assertEquals("dim_date", model.getConformedDimensionsOrEmpty().get(0).getDimensionTableName());
  }

  @Test
  void validatePassesWhenOrphanConformedRefRemoved() {
    DimensionalModel model = new DimensionalModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    DmDimension date = new DmDimension();
    date.setName("dim_date");
    date.getNaturalKeys().add(new DmNaturalKeyField("date_key"));
    model.getTables().add(date);
    model.getConformedDimensions().add(new DmConformedDimensionRef("Customer", "dim_customer"));

    model.removeConformedDimensionRefsForTable("dim_customer");

    List<org.apache.hop.core.ICheckResult> remarks = new java.util.ArrayList<>();
    DmValidationSupport.validateConfiguration(remarks, model, null, null);
    assertTrue(
        remarks.stream()
            .noneMatch(r -> r.getText() != null && r.getText().contains("dim_customer")));
  }
}
