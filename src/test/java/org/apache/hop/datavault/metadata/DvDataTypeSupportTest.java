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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvDataTypeSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveHopTypeIdUsesHopTypeName() {
    assertEquals(IValueMeta.TYPE_INTEGER, DvDataTypeSupport.resolveHopTypeId("Integer", null));
    assertEquals(IValueMeta.TYPE_STRING, DvDataTypeSupport.resolveHopTypeId("String", null));
  }

  @Test
  void resolveHopTypeIdFallsBackToSourceFieldWhenSqlTypeLabel() {
    SourceField field = new SourceField();
    field.setName("demo_score");
    field.setSourceDataType("int2");
    field.setHopType(IValueMeta.TYPE_INTEGER);

    assertEquals(IValueMeta.TYPE_INTEGER, DvDataTypeSupport.resolveHopTypeId("int2", field));
    assertEquals(IValueMeta.TYPE_STRING, DvDataTypeSupport.resolveHopTypeId("varchar", null));
  }

  @Test
  void resolveHopTypeIdMapsDatetimeAndTimestampSqlNames() {
    assertEquals(
        IValueMeta.TYPE_TIMESTAMP, DvDataTypeSupport.resolveHopTypeId("DATETIME(6)", null));
    assertEquals(IValueMeta.TYPE_TIMESTAMP, DvDataTypeSupport.resolveHopTypeId("datetime", null));
    assertEquals(
        IValueMeta.TYPE_TIMESTAMP, DvDataTypeSupport.resolveHopTypeId("TIMESTAMP", null));
    assertEquals(IValueMeta.TYPE_DATE, DvDataTypeSupport.resolveHopTypeId("DATE", null));
  }

  @Test
  void resolveHopTypeIdUsesSourceDataTypeWhenHopTypeUnset() {
    SourceField field = new SourceField();
    field.setName("load_dts");
    field.setSourceDataType("DATETIME(6)");
    field.setHopType(0);

    assertEquals(
        IValueMeta.TYPE_TIMESTAMP, DvDataTypeSupport.resolveHopTypeId(null, field));
    assertEquals(
        IValueMeta.TYPE_TIMESTAMP, DvDataTypeSupport.resolveHopTypeId("", field));
  }

  @Test
  void satelliteValidationAcceptsDatetimeSqlLabelAgainstTimestampSource() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    BusinessKey customerId = new BusinessKey("customer_id");
    customerId.setSourceFieldName("customer_id");
    customerId.setRecordSourceName("all-customer-info");
    customerId.setDataType("Integer");
    hub.setBusinessKeys(List.of(customerId));

    DataVaultSource source = new DataVaultSource("all-customer-info");
    SourceField customerIdField = field("customer_id", "int4", IValueMeta.TYPE_INTEGER, "9");
    SourceField loadDts =
        field("load_dts", "DATETIME(6)", IValueMeta.TYPE_TIMESTAMP, "");
    source.getDvSourceOrDefault().setFields(List.of(customerIdField, loadDts));

    DvSatellite satellite = new TestSatellite("sat_customer", source);
    satellite.setHubName("hub_customer");
    satellite.setRecordSourceName("all-customer-info");
    SatelliteAttribute loadAttr = new SatelliteAttribute();
    loadAttr.setName("load_dts");
    // Native SingleStore / MySQL type as often stored on attributes after import.
    loadAttr.setDataType("DATETIME(6)");
    satellite.setAttributes(List.of(loadAttr));

    model.setTables(List.of(hub, satellite));

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateSatelliteMappings(
        satellite,
        model,
        DvModelCheckOptions.fastOnly(),
        null,
        new Variables(),
        satellite,
        remarks);

    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("load_dts")
                        && r.getText().contains("does not match")),
        () -> "Unexpected type mismatch remarks: " + remarks);
  }

  @Test
  void preferredDataTypeLabelPrefersHopTypeName() {
    SourceField field = new SourceField();
    field.setSourceDataType("int2");
    field.setHopType(IValueMeta.TYPE_INTEGER);

    assertEquals("Integer", DvDataTypeSupport.preferredDataTypeLabel(field));
  }

  @Test
  void preferredDataTypeLabelResolvesKnownSqlTypeWhenHopTypeUnset() {
    SourceField field = new SourceField();
    field.setSourceDataType("int2");
    field.setHopType(0);

    // int2 is mapped to Integer so attributes store a Hop-facing type name.
    assertEquals("Integer", DvDataTypeSupport.preferredDataTypeLabel(field));
  }

  @Test
  void preferredDataTypeLabelFallsBackToUnknownSqlType() {
    SourceField field = new SourceField();
    field.setSourceDataType("weird_custom_type");
    field.setHopType(0);

    assertEquals("weird_custom_type", DvDataTypeSupport.preferredDataTypeLabel(field));
  }

  @Test
  void satelliteValidationAcceptsSqlTypeLabelMatchingSourceHopType() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    BusinessKey customerId = new BusinessKey("customer_id");
    customerId.setSourceFieldName("customer_id");
    customerId.setRecordSourceName("all-customer-info");
    customerId.setDataType("Integer");
    customerId.setLength("9");
    hub.setBusinessKeys(List.of(customerId));

    DataVaultSource source = new DataVaultSource("all-customer-info");
    SourceField customerIdField = field("customer_id", "int4", IValueMeta.TYPE_INTEGER, "9");
    SourceField demoScoreField = field("demo_score", "int2", IValueMeta.TYPE_INTEGER, "4");
    source.getDvSourceOrDefault().setFields(List.of(customerIdField, demoScoreField));

    DvSatellite satellite = new TestSatellite("sat_customer", source);
    satellite.setHubName("hub_customer");
    satellite.setRecordSourceName("all-customer-info");
    SatelliteAttribute demoScore = new SatelliteAttribute();
    demoScore.setName("demo_score");
    // SQL type label as produced by older "Get attributes" (native source type).
    demoScore.setDataType("int2");
    demoScore.setLength("4");
    demoScore.setPrecision("0");
    satellite.setAttributes(List.of(demoScore));

    model.setTables(List.of(hub, satellite));

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateSatelliteMappings(
        satellite,
        model,
        DvModelCheckOptions.fastOnly(),
        null,
        new Variables(),
        satellite,
        remarks);

    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("demo_score")
                        && r.getText().contains("does not match")),
        () -> "Unexpected type mismatch remarks: " + remarks);
  }

  @Test
  void satelliteValidationStillFlagsRealHopTypeMismatch() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    BusinessKey customerId = new BusinessKey("customer_id");
    customerId.setSourceFieldName("customer_id");
    customerId.setRecordSourceName("all-customer-info");
    customerId.setDataType("Integer");
    hub.setBusinessKeys(List.of(customerId));

    DataVaultSource source = new DataVaultSource("all-customer-info");
    SourceField customerIdField = field("customer_id", "int4", IValueMeta.TYPE_INTEGER, "9");
    SourceField demoScoreField = field("demo_score", "int2", IValueMeta.TYPE_INTEGER, "4");
    source.getDvSourceOrDefault().setFields(List.of(customerIdField, demoScoreField));

    DvSatellite satellite = new TestSatellite("sat_customer", source);
    satellite.setHubName("hub_customer");
    satellite.setRecordSourceName("all-customer-info");
    SatelliteAttribute demoScore = new SatelliteAttribute();
    demoScore.setName("demo_score");
    demoScore.setDataType("String");
    demoScore.setLength("4");
    satellite.setAttributes(List.of(demoScore));

    model.setTables(List.of(hub, satellite));

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateSatelliteMappings(
        satellite,
        model,
        DvModelCheckOptions.fastOnly(),
        null,
        new Variables(),
        satellite,
        remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("demo_score")
                        && r.getText().contains("does not match")));
  }

  private static SourceField field(String name, String sourceDataType, int hopType, String length) {
    SourceField field = new SourceField();
    field.setName(name);
    field.setSourceDataType(sourceDataType);
    field.setHopType(hopType);
    field.setLength(length);
    return field;
  }

  private static final class TestSatellite extends DvSatellite {
    private final DataVaultSource recordSource;

    private TestSatellite(String name, DataVaultSource recordSource) {
      super(name);
      this.recordSource = recordSource;
    }

    @Override
    public DataVaultSource resolveRecordSource(
        org.apache.hop.core.variables.IVariables variables,
        IHopMetadataProvider metadataProvider,
        DataVaultModel model) {
      return recordSource;
    }
  }
}
