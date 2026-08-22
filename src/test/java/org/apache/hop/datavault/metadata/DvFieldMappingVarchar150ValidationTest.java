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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SingleStore reverse-import case: source catalog and target model both describe VARCHAR(150).
 * Validation must not treat them as different types or lengths.
 */
class DvFieldMappingVarchar150ValidationTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void catalogVarchar150MatchesTargetString150() throws Exception {
    // Catalog field — matches SingleStore table exactly.
    SourceField catalogField = varchar150("customer_name");

    IValueMeta source =
        DvFieldMappingValidationSupport.valueMetaFromSourceField(catalogField, null);

    // Satellite attribute after "Get attributes" (Hop type label + catalog length).
    SatelliteAttribute attr = new SatelliteAttribute();
    attr.setName("customer_name");
    attr.setDataType(DvDataTypeSupport.preferredDataTypeLabel(catalogField));
    attr.setLength("150");

    IValueMeta target =
        DvFieldMappingValidationSupport.buildTargetValueMetaForSatelliteAttribute(
            attr, catalogField, null);

    assertEquals(IValueMeta.TYPE_STRING, source.getType());
    assertEquals(IValueMeta.TYPE_STRING, target.getType());
    assertEquals(150, source.getLength());
    assertEquals(150, target.getLength());

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(
        source, target, "customer_name", singleStore(), null, remarks);

    assertFalse(
        hasError(remarks),
        () -> "VARCHAR(150) catalog source vs VARCHAR(150) target must not error: " + remarks);
  }

  @Test
  void singleStoreLiveDisplay255ReconciledToCatalog150DoesNotError() throws Exception {
    SourceField catalogField = varchar150("customer_name");

    // What getTableFieldsMeta often returns on SingleStore before/without reliable getColumns.
    IValueMeta liveJdbc =
        ValueMetaFactory.createValueMeta("customer_name", IValueMeta.TYPE_STRING, 255, -1);
    liveJdbc.setOriginalColumnTypeName("VARCHAR");
    // Driver may also put 255 in precision/display-related fields.
    liveJdbc.setOriginalPrecision(255);

    IValueMeta source =
        DvFieldMappingValidationSupport.reconcileLiveWithStoredCatalog(
            liveJdbc, catalogField, null);

    assertEquals(150, source.getLength(), "catalog declared length must win over display size 255");

    SatelliteAttribute attr = new SatelliteAttribute();
    attr.setName("customer_name");
    attr.setDataType("String");
    attr.setLength("150");
    IValueMeta target =
        DvFieldMappingValidationSupport.buildTargetValueMetaForSatelliteAttribute(
            attr, catalogField, null);

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(
        source, target, "customer_name", singleStore(), null, remarks);

    assertFalse(
        hasError(remarks), () -> "Reconciled live VARCHAR must match target 150: " + remarks);
  }

  @Test
  void fullSatellitePathWithStoredCatalogVarchar150() throws Exception {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    BusinessKey bk = new BusinessKey("customer_id");
    bk.setSourceFieldName("customer_id");
    bk.setRecordSourceName("src_customer");
    bk.setDataType("Integer");
    bk.setLength("9");
    hub.setBusinessKeys(List.of(bk));

    DataVaultSource recordSource = new DataVaultSource("src_customer");
    SourceField id = new SourceField("customer_id");
    id.setSourceDataType("INT");
    id.setHopType(IValueMeta.TYPE_INTEGER);
    id.setLength("9");
    SourceField name = varchar150("customer_name");
    recordSource.getDvSourceOrDefault().setFields(List.of(id, name));

    DvSatellite satellite =
        new DvSatellite("sat_customer") {
          @Override
          public DataVaultSource resolveRecordSource(
              org.apache.hop.core.variables.IVariables variables,
              org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider,
              DataVaultModel m) {
            return recordSource;
          }
        };
    satellite.setHubName("hub_customer");
    satellite.setRecordSourceName("src_customer");
    SatelliteAttribute attr = new SatelliteAttribute();
    attr.setName("customer_name");
    attr.setDataType("String");
    attr.setLength("150");
    satellite.setAttributes(List.of(attr));
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
                        && r.getText().contains("customer_name")),
        () -> "Unexpected customer_name errors: " + remarks);
  }

  @Test
  void classicDisplaySizeNoiseDetected() {
    assertTrue(DvFieldMappingValidationSupport.isClassicJdbcDisplaySizeNoise(255, 150));
    assertTrue(DvFieldMappingValidationSupport.isClassicJdbcDisplaySizeNoise(450, 150)); // *3
    assertTrue(DvFieldMappingValidationSupport.isClassicJdbcDisplaySizeNoise(600, 150)); // *4
    assertFalse(DvFieldMappingValidationSupport.isClassicJdbcDisplaySizeNoise(150, 150));
    assertFalse(DvFieldMappingValidationSupport.isClassicJdbcDisplaySizeNoise(200, 150));
  }

  @Test
  void singleStoreLiveDatetimeAsStringReconciledToCatalogTimestamp() throws Exception {
    SourceField catalogField = new SourceField("load_dts");
    catalogField.setSourceDataType("DATETIME(6)");
    catalogField.setHopType(IValueMeta.TYPE_TIMESTAMP);
    catalogField.setLength("6");
    catalogField.setPrecision("6");

    // Live JDBC noise: DATETIME reported as String with display size 255.
    IValueMeta liveJdbc =
        ValueMetaFactory.createValueMeta("load_dts", IValueMeta.TYPE_STRING, 255, -1);

    IValueMeta source =
        DvFieldMappingValidationSupport.reconcileLiveWithStoredCatalog(
            liveJdbc, catalogField, null);

    assertEquals(IValueMeta.TYPE_TIMESTAMP, source.getType());
    assertEquals(6, DvFieldMappingValidationSupport.temporalFractionalDigits(source));

    SatelliteAttribute attr = new SatelliteAttribute();
    attr.setName("load_dts");
    attr.setDataType("Timestamp");
    attr.setLength("6");
    attr.setPrecision("6");
    IValueMeta target =
        DvFieldMappingValidationSupport.buildTargetValueMetaForSatelliteAttribute(
            attr, catalogField, null);

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(
        source, target, "load_dts", singleStore(), null, remarks);

    assertFalse(
        hasError(remarks), () -> "Reconciled DATETIME must match Timestamp target: " + remarks);
  }

  private static SourceField varchar150(String name) {
    SourceField field = new SourceField(name);
    field.setSourceDataType("VARCHAR");
    field.setLength("150");
    field.setHopType(IValueMeta.TYPE_STRING);
    return field;
  }

  private static boolean hasError(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  private static DatabaseMeta singleStore() {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID;
      }
    };
  }
}
