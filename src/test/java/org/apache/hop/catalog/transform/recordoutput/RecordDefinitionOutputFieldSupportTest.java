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
package org.apache.hop.catalog.transform.recordoutput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.SourceField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordDefinitionOutputFieldSupportTest {

  private RowMeta rowMeta;

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @BeforeEach
  void setUp() {
    rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("field_name"));
    rowMeta.addValueMeta(new ValueMetaString("field_type"));
    rowMeta.addValueMeta(new ValueMetaString("field_length"));
    rowMeta.addValueMeta(new ValueMetaString("field_precision"));
    rowMeta.addValueMeta(new ValueMetaInteger("field_pk"));
    rowMeta.addValueMeta(new ValueMetaString("field_format"));
    rowMeta.addValueMeta(new ValueMetaString("field_decimal"));
    rowMeta.addValueMeta(new ValueMetaString("field_grouping"));
    rowMeta.addValueMeta(new ValueMetaString("group_key"));
  }

  @Test
  void mapsAllFieldAttributesIncludingCsvOptions() throws Exception {
    Object[] row =
        new Object[] {"customer_id", "Integer", "9", "0", 1L, "yyyy-MM-dd", ".", ",", "customer"};

    SourceField field =
        RecordDefinitionOutputFieldSupport.sourceFieldFromRow(rowMeta, row, 0, 1, 2, 3, 4, 5, 6, 7);

    assertEquals("customer_id", field.getName());
    assertEquals("Integer", field.getSourceDataType());
    assertEquals(IValueMeta.TYPE_INTEGER, field.getHopType());
    assertEquals("9", field.getLength());
    assertEquals("0", field.getPrecision());
    assertEquals(1, field.getPrimaryKeyPosition());
    assertNotNull(field.getInputOptions());
    assertNotNull(field.getInputOptions().getCsv());
    assertEquals("yyyy-MM-dd", field.getInputOptions().getCsv().getFormat());
    assertEquals(".", field.getInputOptions().getCsv().getDecimalSymbol());
    assertEquals(",", field.getInputOptions().getCsv().getGroupingSymbol());
  }

  @Test
  void omitsCsvOptionsWhenAllBlank() throws Exception {
    Object[] row = new Object[] {"name", "String", "50", "", 0L, "", "", "", "customer"};

    SourceField field =
        RecordDefinitionOutputFieldSupport.sourceFieldFromRow(rowMeta, row, 0, 1, 2, 3, 4, 5, 6, 7);

    assertEquals("name", field.getName());
    assertEquals(IValueMeta.TYPE_STRING, field.getHopType());
    assertEquals("50", field.getLength());
    assertNull(field.getInputOptions());
    assertEquals(0, field.getPrimaryKeyPosition());
  }

  @Test
  void rejectsEmptyFieldName() {
    Object[] row = new Object[] {"", "String", "10", "", 0L, "", "", "", "g"};
    assertThrows(
        HopException.class,
        () ->
            RecordDefinitionOutputFieldSupport.sourceFieldFromRow(
                rowMeta, row, 0, 1, 2, 3, 4, 5, 6, 7));
  }

  @Test
  void rejectsUnknownFieldType() {
    Object[] row = new Object[] {"x", "NotAType", "", "", 0L, "", "", "", "g"};
    assertThrows(
        HopException.class,
        () ->
            RecordDefinitionOutputFieldSupport.sourceFieldFromRow(
                rowMeta, row, 0, 1, 2, 3, 4, 5, 6, 7));
  }

  @Test
  void groupChangedDetectsBreaksOnConsecutiveKeys() {
    assertFalse(RecordDefinitionOutputFieldSupport.groupChanged(null, "a"));
    assertFalse(RecordDefinitionOutputFieldSupport.groupChanged("a", "a"));
    assertTrue(RecordDefinitionOutputFieldSupport.groupChanged("a", "b"));
    assertTrue(RecordDefinitionOutputFieldSupport.groupChanged("a", null));
  }

  @Test
  void groupingValueUsesEmptyWhenIndexMissing() throws Exception {
    Object[] row = new Object[] {"n", "String", "", "", 0L, "", "", "", "customer"};
    assertEquals("", RecordDefinitionOutputFieldSupport.groupingValue(rowMeta, row, -1));
    assertEquals("customer", RecordDefinitionOutputFieldSupport.groupingValue(rowMeta, row, 8));
  }

  @Test
  void resolveDeliveryTypePrefersStreamThenFallback() {
    assertEquals(
        DvSourceDeliveryType.FULL_SNAPSHOT,
        RecordDefinitionOutputFieldSupport.resolveDeliveryType(
            "FULL_SNAPSHOT", DvSourceDeliveryType.CHANGES_ONLY));
    assertEquals(
        DvSourceDeliveryType.CHANGES_ONLY,
        RecordDefinitionOutputFieldSupport.resolveDeliveryType(
            null, DvSourceDeliveryType.CHANGES_ONLY));
    assertEquals(
        DvSourceDeliveryType.FULL_SNAPSHOT,
        RecordDefinitionOutputFieldSupport.resolveDeliveryType(
            "", DvSourceDeliveryType.FULL_SNAPSHOT));
  }
}
