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
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmFactRolePlayingValidationTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void factValidationDoesNotFailWhenRolePlayingDateHasExplicitLoadDateAttribute()
      throws HopException {
    DimensionalModel model = new DimensionalModel();

    DmDimension dateDimension = new DmDimension();
    dateDimension.setName("d_date");
    dateDimension.setScdType(DmDimensionScdType.TYPE1);
    dateDimension.getNaturalKeys().add(new DmNaturalKeyField("date_key"));
    dateDimension
        .getAttributes()
        .add(new DmDimensionAttribute("full_date", DmScdUpdatePolicy.TYPE1));
    dateDimension.getAttributes().add(new DmDimensionAttribute("load_dt", DmScdUpdatePolicy.TYPE1));
    model.getTables().add(dateDimension);

    DmDimensionAlias orderDateAlias = new DmDimensionAlias();
    orderDateAlias.setName("d_order_date");
    orderDateAlias.setReferencedDimensionName("d_date");
    model.getTables().add(orderDateAlias);

    DmDimensionAlias shippingDateAlias = new DmDimensionAlias();
    shippingDateAlias.setName("d_shipping_date");
    shippingDateAlias.setReferencedDimensionName("d_date");
    model.getTables().add(shippingDateAlias);

    DmDimensionAlias deliveryDateAlias = new DmDimensionAlias();
    deliveryDateAlias.setName("d_delivery_date");
    deliveryDateAlias.setReferencedDimensionName("d_date");
    model.getTables().add(deliveryDateAlias);

    DmFact fact = new DmFact();
    fact.setName("f_orders");
    fact.getDimensionRoles().add(new DmFactDimensionRole("d_order_date", "order_date_key"));
    fact.getDimensionRoles().add(new DmFactDimensionRole("d_shipping_date", "shipping_date_key"));
    fact.getDimensionRoles().add(new DmFactDimensionRole("d_delivery_date", "delivery_date_key"));
    fact.getMeasures().add(new DmFactMeasure("total_amount", true));
    model.getTables().add(fact);

    IRowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaTimestamp("order_date"));
    sourceRowMeta.addValueMeta(new ValueMetaTimestamp("shipping_date"));
    sourceRowMeta.addValueMeta(new ValueMetaTimestamp("delivery_date"));
    sourceRowMeta.addValueMeta(new ValueMetaTimestamp("total_amount"));

    List<ICheckResult> remarks = new ArrayList<>();
    DmValidationSupport.validateFact(
        remarks, fact, model, new MemoryMetadataProvider(), new Variables());

    assertFalse(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && remark.getText().contains("Duplicate target column 'load_dt'")));
  }
}
