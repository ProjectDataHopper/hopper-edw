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
package org.hopper.edw.datavault.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class DdlDeltaClassifierTest {

  @Test
  void classifiesCreateTable() {
    List<DdlDelta> deltas =
        DdlDeltaClassifier.classify(
            List.of("CREATE TABLE hub_customer\n(\n  customer_id INTEGER\n)\n;"));
    assertEquals(1, deltas.size());
    assertEquals(DdlDeltaType.CREATE_TABLE, deltas.get(0).getType());
    assertEquals("hub_customer", deltas.get(0).getTableName());
    assertNull(deltas.get(0).getColumnName());
  }

  @Test
  void classifiesAddColumn() {
    List<DdlDelta> deltas =
        DdlDeltaClassifier.classify(
            List.of("ALTER TABLE sat_customer_demo ADD segment VARCHAR(50);"));
    assertEquals(1, deltas.size());
    assertEquals(DdlDeltaType.ADD_COLUMN, deltas.get(0).getType());
    assertEquals("sat_customer_demo", deltas.get(0).getTableName());
    assertEquals("segment", deltas.get(0).getColumnName());
  }

  @Test
  void classifiesAlterColumn() {
    List<DdlDelta> deltas =
        DdlDeltaClassifier.classify(
            List.of("ALTER TABLE sat_customer_demo ALTER COLUMN segment TYPE VARCHAR(50);"));
    assertEquals(1, deltas.size());
    assertEquals(DdlDeltaType.ALTER_COLUMN, deltas.get(0).getType());
    assertEquals("segment", deltas.get(0).getColumnName());
  }

  @Test
  void stripsSchemaAndQuotes() {
    DdlDelta delta =
        DdlDeltaClassifier.classifyOne(
            "ALTER TABLE \"public\".\"hub_customer\" ADD \"new_col\" INT");
    assertEquals(DdlDeltaType.ADD_COLUMN, delta.getType());
    assertEquals("hub_customer", delta.getTableName());
    assertEquals("new_col", delta.getColumnName());
  }
}
