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
package org.apache.hop.datavault.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

class RecordSourceIndicatorSupportTest {

  @Test
  void suggestRecordSourceFieldMatchesCommonNames() {
    assertEquals(
        "x_record_source",
        RecordSourceIndicatorSupport.suggestRecordSourceField(
            List.of("customer_id", "x_record_source", "name")));
    assertEquals(
        "record_source",
        RecordSourceIndicatorSupport.suggestRecordSourceField(List.of("id", "record_source")));
    assertEquals(
        "RECORD_SOURCE",
        RecordSourceIndicatorSupport.suggestRecordSourceField(List.of("RECORD_SOURCE")));
  }

  @Test
  void staticModeSetsIndicatorAndClearsField() {
    DataVaultSource source = new DataVaultSource("CRM-customer");
    RecordSourceIndicatorSupport.applyRecordSource(
        source, RecordSourceIndicatorOptions.staticValue("CRM"));
    assertEquals("CRM", source.getSourceIndicator());
    assertNull(source.getSourceIndicatorField());
  }

  @Test
  void fieldModeSetsFieldAndClearsIndicator() {
    DataVaultSource source = new DataVaultSource("CRM-customer");
    RecordSourceIndicatorSupport.applyRecordSource(
        source, RecordSourceIndicatorOptions.fieldName("record_source"));
    assertNull(source.getSourceIndicator());
    assertEquals("record_source", source.getSourceIndicatorField());
  }

  @Test
  void resolveForTableFieldOrStaticUsesColumnWhenPresent() {
    List<SourceField> fields =
        List.of(new SourceField("customer_id"), new SourceField("x_record_source"));
    RecordSourceIndicatorOptions resolved =
        RecordSourceIndicatorSupport.resolveForTable(
            RecordSourceIndicatorOptions.fieldOrStatic("CRM-customer"), fields, "CRM-customer");
    assertEquals(RecordSourceIndicatorOptions.Mode.FIELD, resolved.getMode());
    assertEquals("x_record_source", resolved.getFieldName());
  }

  @Test
  void resolveForTableFieldOrStaticFallsBackToStaticWhenColumnMissing() {
    List<SourceField> fields = List.of(new SourceField("customer_id"));
    RecordSourceIndicatorOptions resolved =
        RecordSourceIndicatorSupport.resolveForTable(
            RecordSourceIndicatorOptions.fieldOrStatic(null), fields, "conn-customer");
    assertEquals(RecordSourceIndicatorOptions.Mode.STATIC, resolved.getMode());
    assertEquals("conn-customer", resolved.getStaticValue());
  }

  @Test
  void applyToDvSourceRecordClearsOppositeValue() {
    DvSourceRecord dvSource = new DvSourceRecord();
    RecordSourceIndicatorSupport.applyToDvSourceRecord(dvSource, "CRM", null);
    assertEquals("CRM", dvSource.getSourceIndicator());
    assertNull(dvSource.getSourceIndicatorField());

    RecordSourceIndicatorSupport.applyToDvSourceRecord(dvSource, null, "record_source");
    assertNull(dvSource.getSourceIndicator());
    assertEquals("record_source", dvSource.getSourceIndicatorField());
  }

  @Test
  void parseDeliveryTypeAcceptsFullSnapshotCodeAndDescription() {
    assertSame(
        DvSourceDeliveryType.FULL_SNAPSHOT,
        RecordSourceIndicatorSupport.parseDeliveryType("FULL_SNAPSHOT"));
    assertSame(
        DvSourceDeliveryType.FULL_SNAPSHOT,
        RecordSourceIndicatorSupport.parseDeliveryType(
            DvSourceDeliveryType.FULL_SNAPSHOT.getDescription()));
    assertSame(
        DvSourceDeliveryType.CHANGES_ONLY,
        RecordSourceIndicatorSupport.parseDeliveryType("CHANGES_ONLY"));
  }
}
