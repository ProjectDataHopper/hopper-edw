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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

class RecordDefinitionSchemaDiffSupportTest {

  @Test
  void diff_reportsNoChangesWhenContractsMatch() {
    List<SourceField> stored = List.of(field("customer_id", "Integer", IValueMeta.TYPE_INTEGER));
    List<SourceField> discovered =
        List.of(field("customer_id", "Integer", IValueMeta.TYPE_INTEGER));

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(stored, discovered);

    assertTrue(diff.isInSync());
    assertFalse(diff.hasChanges());
  }

  @Test
  void diff_reportsAddedRemovedAndChangedFields() {
    List<SourceField> stored =
        List.of(
            field("customer_id", "Integer", IValueMeta.TYPE_INTEGER),
            field("legacy_code", "String", IValueMeta.TYPE_STRING));
    List<SourceField> discovered =
        List.of(
            field("customer_id", "String", IValueMeta.TYPE_STRING),
            field("email", "String", IValueMeta.TYPE_STRING));

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(stored, discovered);

    assertTrue(diff.hasChanges());
    assertEquals(3, diff.changes().size());
    assertEquals(
        RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED, diff.changes().get(0).kind());
    assertEquals("customer_id", diff.changes().get(0).fieldName());
    assertEquals(
        RecordDefinitionSchemaDiffSupport.ChangeKind.REMOVED, diff.changes().get(1).kind());
    assertEquals("legacy_code", diff.changes().get(1).fieldName());
    assertEquals(RecordDefinitionSchemaDiffSupport.ChangeKind.ADDED, diff.changes().get(2).kind());
    assertEquals("email", diff.changes().get(2).fieldName());
  }

  @Test
  void diff_ignoresTimestampLengthDifferences() {
    SourceField stored = field("load_date", "Timestamp", IValueMeta.TYPE_TIMESTAMP);
    stored.setLength("");
    SourceField discovered = field("load_date", "Timestamp", IValueMeta.TYPE_TIMESTAMP);
    discovered.setLength("6");

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(List.of(stored), List.of(discovered));

    assertTrue(diff.isInSync());
  }

  @Test
  void diff_ignoresIntegerPrecisionZeroVsEmpty() {
    SourceField stored = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER);
    stored.setLength("9");
    stored.setPrecision("0");
    SourceField discovered = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER);
    discovered.setLength("9");
    discovered.setPrecision("");

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(List.of(stored), List.of(discovered));

    assertTrue(diff.isInSync());
  }

  @Test
  void diff_reportsStringLengthChanges() {
    SourceField stored = field("segment", "String", IValueMeta.TYPE_STRING);
    stored.setLength("50");
    SourceField discovered = field("segment", "String", IValueMeta.TYPE_STRING);
    discovered.setLength("100");

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(List.of(stored), List.of(discovered));

    assertTrue(diff.hasChanges());
    assertEquals(1, diff.changes().size());
    assertEquals("segment", diff.changes().get(0).fieldName());
  }

  @Test
  void diff_reportsPrimaryKeyCompositionChanges() {
    SourceField storedPk = field("customer_id", "Integer", IValueMeta.TYPE_INTEGER);
    storedPk.setPrimaryKeyPosition(1);
    SourceField discoveredPk = field("cust_id", "Integer", IValueMeta.TYPE_INTEGER);
    discoveredPk.setPrimaryKeyPosition(1);

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(List.of(storedPk), List.of(discoveredPk));

    assertTrue(diff.hasChanges());
    assertEquals(3, diff.changes().size());
    assertEquals(
        RecordDefinitionSchemaDiffSupport.ChangeKind.PRIMARY_KEY_CHANGED,
        diff.changes().getLast().kind());
    assertEquals("[customer_id] -> [cust_id]", diff.changes().getLast().details());
  }

  @Test
  void diffTypesOnly_ignoresLengthAndPrecisionDifferences() {
    SourceField stored = field("name", "String", IValueMeta.TYPE_STRING);
    stored.setLength("50");
    SourceField discovered = field("name", "String", IValueMeta.TYPE_STRING);
    discovered.setLength("");

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diffTypesOnly(List.of(stored), List.of(discovered));

    assertTrue(diff.isInSync());
  }

  @Test
  void diff_ignoresSingleStoreVarcharDisplaySizeNoise() {
    SourceField catalog = field("customer_name", "VARCHAR", IValueMeta.TYPE_STRING);
    catalog.setLength("150");
    SourceField jdbc = field("customer_name", "VARCHAR", IValueMeta.TYPE_STRING);
    jdbc.setLength("255");

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(List.of(catalog), List.of(jdbc));

    assertTrue(diff.isInSync(), () -> "Unexpected changes: " + diff.changes());
  }

  @Test
  void diff_matchesFieldsCaseInsensitively() {
    SourceField catalog = field("Customer_Name", "VARCHAR", IValueMeta.TYPE_STRING);
    catalog.setLength("150");
    SourceField jdbc = field("customer_name", "VARCHAR", IValueMeta.TYPE_STRING);
    jdbc.setLength("150");

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(List.of(catalog), List.of(jdbc));

    assertTrue(diff.isInSync(), () -> "Unexpected changes: " + diff.changes());
  }

  private static SourceField field(String name, String sourceDataType, int hopType) {
    SourceField field = new SourceField(name);
    field.setSourceDataType(sourceDataType);
    field.setHopType(hopType);
    return field;
  }
}
