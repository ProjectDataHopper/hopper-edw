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
package org.hopper.edw.datavault.ai.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.hopper.edw.datavault.ai.DvAiProposal;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceType;
import org.junit.jupiter.api.Test;

class DmAiStructuralProposalTest {

  @Test
  void appliesDimensionFactAndBindSource() throws Exception {
    DimensionalModel model = new DimensionalModel();

    DvAiProposal dim = new DvAiProposal();
    dim.setType(DvAiProposal.Type.ADD_DIMENSION);
    dim.setParameters(
        Map.of(
            "name",
            "dim_customer",
            "surrogateKeyField",
            "customer_key",
            "naturalKeys",
            "customer_id",
            "attributes",
            "customer_name"));
    DmAiProposalApplier.apply(model, List.of(dim), null, null);

    DmDimension dimension = (DmDimension) model.findTable("dim_customer");
    assertNotNull(dimension);
    assertEquals("customer_key", dimension.getSurrogateKeyField());
    assertEquals(1, dimension.getNaturalKeys().size());
    assertEquals(1, dimension.getAttributes().size());

    DvAiProposal fact = new DvAiProposal();
    fact.setType(DvAiProposal.Type.ADD_FACT);
    fact.setParameters(
        Map.of(
            "name",
            "fact_orders",
            "grain",
            "one row per order",
            "measures",
            "quantity,amount",
            "dimensionTableNames",
            "dim_customer"));
    DmAiProposalApplier.apply(model, List.of(fact), null, null);

    DmFact orders = (DmFact) model.findTable("fact_orders");
    assertNotNull(orders);
    assertEquals("one row per order", orders.getGrain());
    assertEquals(2, orders.getMeasures().size());
    assertEquals(1, orders.getDimensionRoles().size());
    assertEquals("dim_customer", orders.getDimensionRoles().get(0).getDimensionTableName());

    DvAiProposal bind = new DvAiProposal();
    bind.setType(DvAiProposal.Type.BIND_SOURCE);
    bind.setParameters(
        Map.of(
            "tableName",
            "fact_orders",
            "sourceType",
            "SQL",
            "sourceSql",
            "select * from staging.orders"));
    DmAiProposalApplier.apply(model, List.of(bind), null, null);

    assertEquals(DmSourceType.SQL, orders.getSourceOrDefault().resolveSourceType());
    assertEquals("select * from staging.orders", orders.getSourceOrDefault().getSourceSql());
  }

  @Test
  void blocksDuplicateDimension() {
    DimensionalModel model = new DimensionalModel();
    DmDimension existing = new DmDimension();
    existing.setName("dim_customer");
    model.getTables().add(existing);

    DvAiProposal dim = new DvAiProposal();
    dim.setType(DvAiProposal.Type.ADD_DIMENSION);
    dim.setParameters(Map.of("name", "dim_customer"));

    DmAiProposalValidator.ValidationResult result =
        DmAiProposalValidator.validate(model, List.of(dim), null, null).get(0);
    assertEquals(DmAiProposalValidator.Status.BLOCKED, result.getStatus());
  }

  @Test
  void appliesJunkAndLocation() throws Exception {
    DimensionalModel model = new DimensionalModel();
    DvAiProposal junk = new DvAiProposal();
    junk.setType(DvAiProposal.Type.ADD_JUNK_DIMENSION);
    junk.setParameters(Map.of("name", "dim_flags", "keyFields", "is_gift,channel"));
    DmAiProposalApplier.apply(model, List.of(junk), null, null);

    DvAiProposal move = new DvAiProposal();
    move.setType(DvAiProposal.Type.SET_TABLE_LOCATION);
    move.setParameters(Map.of("tableName", "dim_flags", "locationX", "10", "locationY", "20"));
    DmAiProposalApplier.apply(model, List.of(move), null, null);

    assertEquals(10, model.findTable("dim_flags").getLocation().x);
    assertEquals(20, model.findTable("dim_flags").getLocation().y);
  }
}
