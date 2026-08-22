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
package org.hopper.edw.datavault.layout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElkGraphLayoutDimensionalTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void layoutsBasicStarWithoutError() {
    DimensionalModel model = new DimensionalModel();
    model.setName("basic-star");

    DmDimension customer = new DmDimension();
    customer.setName("dim_customer");
    customer.setLocation(64, 64);
    customer.getNaturalKeys().add(new DmNaturalKeyField("customer_id"));
    model.getTables().add(customer);

    DmFact fact = new DmFact();
    fact.setName("fact_sales");
    fact.setLocation(320, 144);
    fact.getDimensionRoles()
        .add(new DmFactDimensionRole("dim_customer", "Customer", "customer_key"));
    model.getTables().add(fact);

    assertDoesNotThrow(
        () -> ElkGraphLayout.fromDimensionalModel(model).layout(ElkLayout.createDefault()));
  }
}
