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
package org.hopper.edw.catalog.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecordDefinitionQueryTest {

  @Test
  void nameContainsMatchesRecordNameCaseInsensitively() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/project/sources", "E2E-customer-hub"));

    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNameContains("customer");

    assertTrue(query.matches(definition));
    query.setNameContains("CUSTOMER");
    assertTrue(query.matches(definition));
    query.setNameContains("orders");
    assertFalse(query.matches(definition));
  }

  @Test
  void nameContainsMatchesNamespaceKeyCaseInsensitively() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "CRM-customer"));

    RecordDefinitionQuery query = new RecordDefinitionQuery();
    query.setNameContains("retail-example/sources");

    assertTrue(query.matches(definition));
    query.setNameContains("hop/retail-example/sources::crm-customer");
    assertTrue(query.matches(definition));
  }
}
