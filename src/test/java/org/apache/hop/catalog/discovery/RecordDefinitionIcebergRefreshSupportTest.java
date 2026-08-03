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
package org.apache.hop.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

class RecordDefinitionIcebergRefreshSupportTest {

  @Test
  void mergeDiscoveredFields_preservesStoredLengthAndPrecision() {
    SourceField stored = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER, "9", "0");
    SourceField discovered = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER, "15", "");

    List<SourceField> merged =
        RecordDefinitionIcebergRefreshSupport.mergeDiscoveredFields(
            List.of(stored), List.of(discovered));

    assertEquals(1, merged.size());
    assertEquals("Integer", merged.get(0).getSourceDataType());
    assertEquals("9", merged.get(0).getLength());
    assertEquals("0", merged.get(0).getPrecision());
  }

  @Test
  void mergeDiscoveredFields_updatesTypeAndKeepsProjectStringLength() {
    SourceField stored = field("name", "String", IValueMeta.TYPE_STRING, "50", "");
    SourceField discovered = field("name", "String", IValueMeta.TYPE_STRING, "", "");

    List<SourceField> merged =
        RecordDefinitionIcebergRefreshSupport.mergeDiscoveredFields(
            List.of(stored), List.of(discovered));

    assertEquals("50", merged.get(0).getLength());
  }

  @Test
  void mergeDiscoveredFields_usesDiscoveryDefaultsForNewFields() {
    SourceField discovered = field("email", "String", IValueMeta.TYPE_STRING, "", "");

    List<SourceField> merged =
        RecordDefinitionIcebergRefreshSupport.mergeDiscoveredFields(List.of(), List.of(discovered));

    assertEquals("", merged.get(0).getLength());
    assertEquals("", merged.get(0).getPrecision());
  }

  private static SourceField field(
      String name, String sourceDataType, int hopType, String length, String precision) {
    SourceField field = new SourceField(name);
    field.setSourceDataType(sourceDataType);
    field.setHopType(hopType);
    field.setLength(length);
    field.setPrecision(precision);
    return field;
  }
}
