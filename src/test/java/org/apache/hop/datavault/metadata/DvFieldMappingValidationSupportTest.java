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
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvFieldMappingValidationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void validateSatelliteMappingsFlagsMissingParentBusinessKeyColumnInSatelliteSource()
      throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_syn_order");
    BusinessKey orderId = new BusinessKey("order_id");
    orderId.setSourceFieldName("order_id");
    orderId.setRecordSourceName("syn-order");
    orderId.setDataType("Integer");
    orderId.setLength("15");
    hub.setBusinessKeys(List.of(orderId));
    List<IDvTable> tables = new ArrayList<>();
    tables.add(hub);

    // Sat source lacks parent BK column order_id.
    DvSatellite satellite =
        new TestSatellite("sat_syn_order", sourceWithFields("syn-order", "product_id", "amount"));
    satellite.setHubName("hub_syn_order");
    satellite.setRecordSourceName("syn-order");
    satellite.setAttributes(List.of(attribute("amount")));
    tables.add(satellite);
    model.setTables(tables);

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
                        && r.getText().contains("order_id")
                        && r.getText().contains("hub_syn_order")
                        && r.getText().contains("syn-order")),
        "Expected missing parent BK column on sat source, got: " + remarks);
  }

  @Test
  void validateSatelliteMappingsAcceptsIndependentSourceWhenParentBkColumnPresent()
      throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    BusinessKey customerId = new BusinessKey("customer_id");
    customerId.setSourceFieldName("customer_id");
    customerId.setRecordSourceName("E2E-customer-hub");
    customerId.setDataType("Integer");
    customerId.setLength("9");
    hub.setBusinessKeys(List.of(customerId));
    List<IDvTable> tables = new ArrayList<>();
    tables.add(hub);

    // Satellite uses a different source than the hub; parent BK column name matches hub BK.
    DvSatellite satellite =
        new TestSatellite(
            "sat_customer", sourceWithFields("all-customer-info", "customer_id", "email"));
    satellite.setHubName("hub_customer");
    satellite.setRecordSourceName("all-customer-info");
    satellite.setAttributes(List.of(attribute("email")));
    tables.add(satellite);
    model.setTables(tables);

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
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        "Independent sat source with matching parent BK column should pass, got: " + remarks);
  }

  @Test
  void validateSatelliteMappingsUsesOrderedParentKeySourceFields() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    BusinessKey customerId = new BusinessKey("customer_id");
    customerId.setSourceFieldName("customer_id");
    customerId.setRecordSourceName("E2E-customer-hub");
    customerId.setDataType("Integer");
    customerId.setLength("9");
    hub.setBusinessKeys(List.of(customerId));
    List<IDvTable> tables = new ArrayList<>();
    tables.add(hub);

    DvSatellite satellite =
        new TestSatellite(
            "sat_customer", sourceWithFields("all-customer-info", "cust_no", "email"));
    satellite.setHubName("hub_customer");
    satellite.setRecordSourceName("all-customer-info");
    satellite.setParentKeySourceFields(List.of("cust_no"));
    satellite.setAttributes(List.of(attribute("email")));
    tables.add(satellite);
    model.setTables(tables);

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
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        "Ordered parent key source field cust_no should resolve, got: " + remarks);
  }

  @Test
  void validateSatelliteMappingsAcceptsHubBusinessKeyPresentInSatelliteSource()
      throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_syn_order");
    BusinessKey orderId = new BusinessKey("order_id");
    orderId.setSourceFieldName("order_id");
    orderId.setRecordSourceName("syn-order");
    orderId.setDataType("Integer");
    orderId.setLength("15");
    hub.setBusinessKeys(List.of(orderId));
    List<IDvTable> tables = new ArrayList<>();
    tables.add(hub);

    DvSatellite satellite =
        new TestSatellite("sat_syn_order", sourceWithFields("syn-order", "order_id", "amount"));
    satellite.setHubName("hub_syn_order");
    satellite.setRecordSourceName("syn-order");
    satellite.setAttributes(List.of(attribute("amount")));
    tables.add(satellite);
    model.setTables(tables);

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
                        && (r.getText().contains("Parent business key")
                            || r.getText().contains("Hub business key"))));
  }

  @Test
  void validateHubBusinessKeysFlagsMissingMappingForHubLoadSource() throws HopException {
    DvHub hub = new DvHub("hub_customer");
    BusinessKey customerId = new BusinessKey("customer_id");
    customerId.setSourceFieldName("customer_id");
    customerId.setRecordSourceName("E2E-customer-hub");
    customerId.setDataType("Integer");
    customerId.setLength("9");
    hub.setBusinessKeys(List.of(customerId));

    // Hub load source without a BK mapping for that source still fails (hub loads only).
    DataVaultSource otherHubSource = sourceWithFields("crm-other", "customer_id", "email");
    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateHubBusinessKeys(
        hub,
        otherHubSource,
        null,
        null,
        DvModelCheckOptions.fastOnly(),
        null,
        new Variables(),
        hub,
        remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("hub_customer")
                        && r.getText().contains("crm-other")
                        && r.getText().contains("no business key mapped")),
        "Expected hub missing BK-for-source error, got: " + remarks);
  }

  private static DataVaultSource sourceWithFields(String name, String... fieldNames) {
    DataVaultSource source = new DataVaultSource(name);
    List<SourceField> fields = new ArrayList<>();
    for (String fieldName : fieldNames) {
      SourceField field = new SourceField();
      field.setName(fieldName);
      field.setSourceDataType("Integer");
      field.setLength("15");
      field.setHopType(IValueMeta.TYPE_INTEGER);
      fields.add(field);
    }
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static SatelliteAttribute attribute(String name) {
    SatelliteAttribute attribute = new SatelliteAttribute();
    attribute.setName(name);
    attribute.setDataType("Integer");
    attribute.setLength("15");
    return attribute;
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

  @Test
  void effectiveStringCapacityExpandsSqlServerOnly() {
    DatabaseMeta sqlServer = databaseMeta(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID);
    DatabaseMeta postgres = databaseMeta("POSTGRESQL");
    assertEquals(50, DvDdlSupport.effectiveStringCapacity(null, 50));
    assertEquals(50, DvDdlSupport.effectiveStringCapacity(postgres, 50));
    assertEquals(150, DvDdlSupport.effectiveStringCapacity(sqlServer, 50));
  }

  /**
   * Design: model field length 50 (characters) → vault VARCHAR(150). A physical source that also
   * reports 150 (or NVARCHAR-equivalent capacity) must not fail as "exceeds model field settings
   * length 50".
   */
  @Test
  void validateMappingUsesVaultUtf8CapacityNotRawModelLengthOnSqlServer() throws Exception {
    org.apache.hop.core.row.value.ValueMetaString source =
        new org.apache.hop.core.row.value.ValueMetaString("name");
    source.setLength(150);
    org.apache.hop.core.row.value.ValueMetaString target =
        new org.apache.hop.core.row.value.ValueMetaString("name");
    target.setLength(50); // model character length

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(
        source,
        target,
        "sat name",
        databaseMeta(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID),
        null,
        remarks);

    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> remarks.toString());
  }

  @Test
  void validateMappingErrorsWhenSourceExceedsSqlServerUtf8Capacity() throws Exception {
    org.apache.hop.core.row.value.ValueMetaString source =
        new org.apache.hop.core.row.value.ValueMetaString("name");
    source.setLength(200);
    org.apache.hop.core.row.value.ValueMetaString target =
        new org.apache.hop.core.row.value.ValueMetaString("name");
    target.setLength(50);

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(
        source,
        target,
        "sat name",
        databaseMeta(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID),
        null,
        remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("capacity")
                        && r.getText().contains("150")
                        && r.getText().contains("50")),
        () -> remarks.toString());
  }

  @Test
  void validateMappingPostgresComparesCharacterLengthsWithoutExpansion() throws Exception {
    org.apache.hop.core.row.value.ValueMetaString source =
        new org.apache.hop.core.row.value.ValueMetaString("name");
    source.setLength(60);
    org.apache.hop.core.row.value.ValueMetaString target =
        new org.apache.hop.core.row.value.ValueMetaString("name");
    target.setLength(50);

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateMapping(
        source, target, "sat name", databaseMeta("POSTGRESQL"), null, remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("exceeds model field settings length")),
        () -> remarks.toString());
  }

  private static DatabaseMeta databaseMeta(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }
    };
  }
}
