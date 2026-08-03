/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.junit.jupiter.api.Test;

class SourceFieldPrimaryKeySupportTest {

  @Test
  void appliesAndReadsOrderedPrimaryKeyFieldNames() {
    SourceField first = field("tenant_id");
    SourceField second = field("customer_id");
    SourceField other = field("name");
    List<SourceField> fields = List.of(first, second, other);

    SourceFieldPrimaryKeySupport.applyPrimaryKeyFieldNames(
        fields, List.of("customer_id", "tenant_id"));

    assertEquals(
        List.of("customer_id", "tenant_id"),
        SourceFieldPrimaryKeySupport.primaryKeyFieldNames(fields));
    assertEquals(2, first.getPrimaryKeyPosition());
    assertEquals(1, second.getPrimaryKeyPosition());
    assertEquals(0, other.getPrimaryKeyPosition());
  }

  @Test
  void buildsBusinessKeysFromPrimaryKeyMetadata() {
    SourceField customerId = field("customer_id");
    customerId.setPrimaryKeyPosition(1);
    customerId.setSourceDataType("Integer");
    customerId.setLength("9");

    List<BusinessKey> businessKeys =
        SourceFieldPrimaryKeySupport.businessKeysFromPrimaryKeyFields(
            List.of(customerId), "CRM-customer");

    assertEquals(1, businessKeys.size());
    assertEquals("customer_id", businessKeys.getFirst().getName());
    assertEquals("customer_id", businessKeys.getFirst().getSourceFieldName());
    assertEquals("CRM-customer", businessKeys.getFirst().getRecordSourceName());
    assertEquals("Integer", businessKeys.getFirst().getDataType());
    assertEquals("9", businessKeys.getFirst().getLength());
  }

  @Test
  void detectsPrimaryKeyCompositionDifferences() {
    SourceField storedPk = field("id");
    storedPk.setPrimaryKeyPosition(1);
    SourceField discoveredPk = field("customer_id");
    discoveredPk.setPrimaryKeyPosition(1);

    assertFalse(
        SourceFieldPrimaryKeySupport.primaryKeyCompositionEquals(
            List.of(storedPk), List.of(discoveredPk)));
    assertEquals(
        "[id] -> [customer_id]",
        SourceFieldPrimaryKeySupport.describePrimaryKeyCompositionDifference(
            List.of(storedPk), List.of(discoveredPk)));
  }

  private static SourceField field(String name) {
    SourceField field = new SourceField(name);
    field.setHopType(IValueMeta.TYPE_STRING);
    return field;
  }
}
