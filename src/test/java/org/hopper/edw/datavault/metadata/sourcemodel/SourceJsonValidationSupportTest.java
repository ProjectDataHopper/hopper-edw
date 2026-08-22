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
package org.hopper.edw.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceJsonValidationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void flagsMissingDataTypesAsError() {
    SourceModel model = new SourceModel();
    model.setName("crm");
    SourceTable orders = new SourceTable("orders");
    SourceColumn orderId = new SourceColumn("order_id");
    orderId.setHopType(IValueMeta.TYPE_INTEGER);
    orderId.setPrimaryKeyPosition(1);
    orders.getColumns().add(orderId);
    model.getTables().add(orders);

    SourceJson json = new SourceJson("order_events");
    json.setParentSourceKind(SourceJsonParentKind.TABLE);
    json.setParentSourceName("orders");
    json.setJsonFieldName("payload");
    SourceJsonField extracted = new SourceJsonField("event_type", "$.type");
    // hopType left unset — should error
    SourceJsonField passThrough = SourceJsonField.passThroughField("order_id");
    passThrough.setPrimaryKeyPosition(1);
    // pass-through can resolve type from parent
    json.getFields().add(extracted);
    json.getFields().add(passThrough);

    List<ICheckResult> remarks = SourceJsonValidationSupport.check(json, model, null);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("without a Hop data type")),
        () -> "Expected missing-type error, got: " + remarks);

    // Pass-through type resolution still works for publisher/dialog.
    assertEquals(
        IValueMeta.TYPE_INTEGER,
        SourceJsonFieldSupport.resolveEffectiveHopType(model, json, passThrough));
  }

  @Test
  void passThroughInheritsParentTypeWhenUnset() {
    SourceModel model = new SourceModel();
    model.setName("crm");
    SourceTable customers = new SourceTable("customers");
    SourceColumn customerId = new SourceColumn("customer_id");
    customerId.setHopType(IValueMeta.TYPE_INTEGER);
    customers.getColumns().add(customerId);
    model.getTables().add(customers);

    SourceJson json = new SourceJson("customer_json");
    json.setParentSourceKind(SourceJsonParentKind.TABLE);
    json.setParentSourceName("customers");
    json.setJsonFieldName("doc");
    SourceJsonField passThrough = SourceJsonField.passThroughField("customer_id");
    passThrough.setPrimaryKeyPosition(1);
    SourceJsonField extracted = new SourceJsonField("email", "$.email");
    extracted.setHopType(IValueMeta.TYPE_STRING);
    json.getFields().add(passThrough);
    json.getFields().add(extracted);

    SourceJsonFieldSupport.applyMissingPassThroughTypes(model, json);
    assertEquals(IValueMeta.TYPE_INTEGER, passThrough.getHopType());

    List<ICheckResult> remarks = SourceJsonValidationSupport.check(json, model, null);
    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("without a Hop data type")),
        () -> "Unexpected missing-type errors: " + remarks);
  }
}
