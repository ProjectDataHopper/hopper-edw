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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvLinkValidationTest {

  private static final Class<?> PKG = DvLink.class;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveLinkHashKeyFieldNameDefaultsToNamePlusLk() {
    DvLink link = new DvLink("lnk_order_shipment");
    assertEquals("lnk_order_shipment_LK", link.resolveLinkHashKeyFieldName());
    assertEquals("lnk_order_shipment_LK", link.resolveLinkHashKeyFieldName(new Variables()));
  }

  @Test
  void resolveLinkHashKeyFieldNameUsesStoredValue() {
    DvLink link = new DvLink("lnk_order_shipment");
    link.setLinkHashKeyFieldName("lnk_order_shipment_hk");
    assertEquals("lnk_order_shipment_hk", link.resolveLinkHashKeyFieldName());
  }

  @Test
  void resolveLinkHashKeyFieldNameResolvesVariables() {
    DvLink link = new DvLink("lnk_order");
    link.setLinkHashKeyFieldName("${PREFIX}_lk");
    Variables variables = new Variables();
    variables.setVariable("PREFIX", "order");
    assertEquals("order_lk", link.resolveLinkHashKeyFieldName(variables));
  }

  @Test
  void checkCommentsWhenLinkHashKeyBlankButNamePresent() {
    DvLink link = new DvLink("lnk_order_shipment");
    link.setIntegrationMode(DvIntegrationMode.EXTERNAL_READ);
    link.getHubNames().add("hub_order");
    link.getHubNames().add("hub_order_shipment");

    List<ICheckResult> remarks = new ArrayList<>();
    link.check(
        remarks, null, new Variables(), DvModelCheckOptions.defaults(), new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_COMMENT
                        && r.getText()
                            .equals(
                                BaseMessages.getString(
                                    PKG,
                                    "DvLink.CheckResult.NoLinkHashKeyFieldName",
                                    "lnk_order_shipment_LK"))));
    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .contains(
                                BaseMessages.getString(
                                    PKG, "DvLink.CheckResult.MissingResolvableLinkHashKey"))));
  }

  @Test
  void checkErrorsWhenLinkHashKeyUnresolvable() {
    DvLink link = new DvLink();
    link.setName("");
    link.setLinkHashKeyFieldName("");
    link.setIntegrationMode(DvIntegrationMode.EXTERNAL_READ);

    List<ICheckResult> remarks = new ArrayList<>();
    link.check(
        remarks, null, new Variables(), DvModelCheckOptions.defaults(), new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .equals(
                                BaseMessages.getString(
                                    PKG, "DvLink.CheckResult.MissingResolvableLinkHashKey"))));
  }

  @Test
  void targetTableLayoutDefaultsBlankLinkHashKey() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hubOrder = new DvHub("hub_order");
    hubOrder.setHashKeyFieldName("order_hk");
    DvHub hubShipment = new DvHub("hub_order_shipment");
    hubShipment.setHashKeyFieldName("shipment_hk");

    DvLink link = new DvLink("lnk_order_shipment");
    // blank stored name — must default, not throw at layout/generation time
    link.setLinkHashKeyFieldName("");
    link.getHubNames().add("hub_order");
    link.getHubNames().add("hub_order_shipment");
    model.getTables().addAll(List.of(hubOrder, hubShipment, link));

    IRowMeta layout =
        link.getTargetTableLayout(new MemoryMetadataProvider(), new Variables(), model);

    assertTrue(
        layout.getValueMetaList().stream()
            .anyMatch(meta -> "lnk_order_shipment_LK".equals(meta.getName())));
  }

  @Test
  void checkErrorsWhenLinkHubSourcesMissing() {
    DvLink link = new DvLink("lnk_order");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");

    List<ICheckResult> remarks = new ArrayList<>();
    link.check(
        remarks, null, new Variables(), DvModelCheckOptions.defaults(), new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .equals(
                                BaseMessages.getString(
                                    PKG, "DvLink.CheckResult.NoLinkHubSources"))));
  }

  @Test
  void externalReadLinkSkipsLinkHubSourceRequirement() {
    DvLink link = new DvLink("lnk_order");
    link.setIntegrationMode(DvIntegrationMode.EXTERNAL_READ);
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");

    List<ICheckResult> remarks = new ArrayList<>();
    link.check(
        remarks, null, new Variables(), DvModelCheckOptions.defaults(), new DataVaultModel());

    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .equals(
                                BaseMessages.getString(
                                    PKG, "DvLink.CheckResult.NoLinkHubSources"))));
  }

  @Test
  void generateUpdatePipelinesThrowsWhenLinkHubSourcesMissing() {
    DataVaultModel model = new DataVaultModel();
    DvLink link = new DvLink("lnk_order");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");
    model.getTables().add(link);

    IHopMetadataProvider metadataProvider = new MemoryMetadataProvider();

    HopException ex =
        assertThrows(
            HopException.class,
            () ->
                link.generateUpdatePipelines(
                    metadataProvider, new Variables(), model, new Date(), null));

    assertTrue(
        ex.getMessage()
            .contains(
                BaseMessages.getString(PKG, "DvLink.Error.NoLinkHubSources", link.getName())));
  }

  @Test
  void targetTableLayoutUsesPerLinkRecordSourceFieldOverride() throws HopException {
    DataVaultModel model = new DataVaultModel();
    model.getConfigurationOrDefault().setRecordSourceField("x_record_source");

    DvHub hubCustomer = new DvHub("hub_customer");
    hubCustomer.setHashKeyFieldName("customer_hk");
    DvHub hubOrder = new DvHub("hub_order");
    hubOrder.setHashKeyFieldName("order_hk");

    DvLink link = new DvLink("lnk_order");
    link.setRecordSourceFieldName("record_source");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");
    model.getTables().addAll(List.of(hubCustomer, hubOrder, link));

    IRowMeta layout =
        link.getTargetTableLayout(new MemoryMetadataProvider(), new Variables(), model);

    assertTrue(
        layout.getValueMetaList().stream()
            .anyMatch(meta -> "record_source".equals(meta.getName())));
    assertFalse(
        layout.getValueMetaList().stream()
            .anyMatch(meta -> "x_record_source".equals(meta.getName())));
  }

  @Test
  void resolveDependentChildSourceFieldNamesUsesSourceFieldOrName() throws HopException {
    DvLink link = new DvLink("lnk_order_line");
    DependentChildKey lineNumber = new DependentChildKey("line_number");
    lineNumber.setSourceFieldName("line_number");
    DependentChildKey seq = new DependentChildKey("reading_seq");
    // no explicit sourceFieldName → falls back to name
    link.getDependentChildKeys().add(lineNumber);
    link.getDependentChildKeys().add(seq);

    assertEquals(
        List.of("line_number", "reading_seq"),
        link.resolveDependentChildSourceFieldNames(new Variables()));
  }

  @Test
  void targetTableLayoutIncludesDependentChildKeysAfterHubHashes() throws HopException {
    DataVaultModel model = new DataVaultModel();
    model.getConfigurationOrDefault().setRecordSourceField("x_record_source");
    model.getConfigurationOrDefault().setLoadDateField("x_load_ts");

    DvHub hubSensor = new DvHub("hub_sensor");
    hubSensor.setHashKeyFieldName("sensor_hk");
    DvHub hubMachine = new DvHub("hub_machine");
    hubMachine.setHashKeyFieldName("machine_hk");

    DvLink link = new DvLink("lnk_measurement");
    link.setLinkHashKeyFieldName("measurement_lk");
    link.getHubNames().add("hub_sensor");
    link.getHubNames().add("hub_machine");

    DependentChildKey ts = new DependentChildKey("measurement_ts");
    ts.setSourceFieldName("reading_ts");
    ts.setDataType("Timestamp");
    DependentChildKey seq = new DependentChildKey("reading_seq");
    seq.setDataType("Integer");
    link.getDependentChildKeys().add(ts);
    link.getDependentChildKeys().add(seq);

    model.getTables().addAll(List.of(hubSensor, hubMachine, link));

    IRowMeta layout =
        link.getTargetTableLayout(new MemoryMetadataProvider(), new Variables(), model);

    List<String> names = layout.getValueMetaList().stream().map(m -> m.getName()).toList();
    assertTrue(names.contains("measurement_lk"));
    assertTrue(names.contains("sensor_hk"));
    assertTrue(names.contains("machine_hk"));
    assertTrue(names.contains("measurement_ts"));
    assertTrue(names.contains("reading_seq"));
    assertTrue(names.indexOf("measurement_ts") > names.indexOf("machine_hk"));
    assertTrue(names.indexOf("reading_seq") > names.indexOf("measurement_ts"));
    assertTrue(names.indexOf("x_record_source") > names.indexOf("reading_seq"));
  }

  @Test
  void checkReportsDependentChildKeysAndErrorsOnMissingName() {
    DvLink link = new DvLink("lnk_measurement");
    link.getHubNames().add("hub_sensor");
    link.getHubNames().add("hub_machine");
    link.setIntegrationMode(DvIntegrationMode.EXTERNAL_READ);

    DependentChildKey ok = new DependentChildKey("measurement_ts");
    ok.setSourceFieldName("reading_ts");
    DependentChildKey bad = new DependentChildKey();
    link.getDependentChildKeys().add(ok);
    link.getDependentChildKeys().add(bad);

    List<ICheckResult> remarks = new ArrayList<>();
    link.check(
        remarks, null, new Variables(), DvModelCheckOptions.defaults(), new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_OK
                        && r.getText()
                            .equals(
                                BaseMessages.getString(
                                    PKG, "DvLink.CheckResult.HasDependentChildKeys", 2))));
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText()
                            .equals(
                                BaseMessages.getString(
                                    PKG, "DvLink.CheckResult.DependentChildKeyNoName"))));
  }

  @Test
  void validateLinkHubKeyFieldsFlagsMissingHubMappingRow() {
    DataVaultModel model = new DataVaultModel();
    DvHub hubCustomer = new DvHub("hub_customer");
    hubCustomer.setBusinessKeys(List.of(businessKey("customer_id")));
    DvHub hubOrder = new DvHub("hub_order");
    hubOrder.setBusinessKeys(List.of(businessKey("order_id")));

    DvLink link = new DvLink("lnk_order");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");

    DataVaultSource source = sourceWithFields("order-header", "customer_id", "order_id");
    DvLink.DvLinkHubSource linkSource = testLinkHubSource(source);
    linkSource.setSourceName("order-header");

    DvLink.HubSourceKeyField customerMapping = new DvLink.HubSourceKeyField();
    customerMapping.setHubName("hub_customer");
    linkSource.getHubSourceKeyFields().add(customerMapping);

    link.getLinkHubSources().add(linkSource);
    model.getTables().addAll(List.of(hubCustomer, hubOrder, link));

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateLinkHubKeyFields(
        link,
        model,
        DvModelCheckOptions.fastOnly(),
        new MemoryMetadataProvider(),
        new Variables(),
        link,
        remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("hub_order")
                        && r.getText().contains("order-header")
                        && r.getText().contains("lnk_order")));
  }

  @Test
  void validateLinkHubKeyFieldsFlagsMissingSourceColumnForImplicitMapping() {
    DataVaultModel model = new DataVaultModel();
    DvHub hubCustomer = new DvHub("hub_customer");
    hubCustomer.setBusinessKeys(List.of(businessKey("customer_id")));
    DvHub hubOrder = new DvHub("hub_order");
    hubOrder.setBusinessKeys(List.of(businessKey("order_id")));

    DvLink link = new DvLink("lnk_order");
    link.getHubNames().add("hub_customer");
    link.getHubNames().add("hub_order");

    DataVaultSource source = sourceWithFields("order-header", "order_id");
    DvLink.DvLinkHubSource linkSource = testLinkHubSource(source);
    linkSource.setSourceName("order-header");

    for (String hubName : link.getHubNames()) {
      DvLink.HubSourceKeyField mapping = new DvLink.HubSourceKeyField();
      mapping.setHubName(hubName);
      linkSource.getHubSourceKeyFields().add(mapping);
    }
    link.getLinkHubSources().add(linkSource);
    model.getTables().addAll(List.of(hubCustomer, hubOrder, link));

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateLinkHubKeyFields(
        link,
        model,
        DvModelCheckOptions.fastOnly(),
        new MemoryMetadataProvider(),
        new Variables(),
        link,
        remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("customer_id")
                        && r.getText().contains("order-header")));
  }

  @Test
  void validateLinkRecordSourceFieldsFlagsMissingSourceIndicator() {
    DataVaultModel model = new DataVaultModel();
    DvLink link = new DvLink("lnk_order");
    link.setRecordSourceFieldName("record_source");

    DataVaultSource source = new DataVaultSource("order-header");
    source.getDvSourceOrDefault().setFields(List.of());

    DvLink.DvLinkHubSource linkSource = testLinkHubSource(source);
    linkSource.setSourceName("order-header");
    link.getLinkHubSources().add(linkSource);
    model.getTables().add(link);

    List<ICheckResult> remarks = new ArrayList<>();
    DvFieldMappingValidationSupport.validateLinkRecordSourceFields(
        link,
        model,
        DvModelCheckOptions.fastOnly(),
        new MemoryMetadataProvider(),
        new Variables(),
        link,
        remarks);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("order-header")
                        && r.getText().contains("lnk_order")));
  }

  private static BusinessKey businessKey(String name) {
    BusinessKey key = new BusinessKey(name);
    key.setDataType("String");
    key.setLength("20");
    return key;
  }

  private static DataVaultSource sourceWithFields(String name, String... fieldNames) {
    DataVaultSource source = new DataVaultSource(name);
    List<SourceField> fields = new ArrayList<>();
    for (String fieldName : fieldNames) {
      SourceField field = new SourceField();
      field.setName(fieldName);
      field.setSourceDataType("String");
      field.setLength("20");
      fields.add(field);
    }
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static DvLink.DvLinkHubSource testLinkHubSource(DataVaultSource source) {
    return new DvLink.DvLinkHubSource() {
      @Override
      public DataVaultSource resolveSource(
          org.apache.hop.core.variables.IVariables variables,
          IHopMetadataProvider metadataProvider,
          DataVaultModel model) {
        return source;
      }
    };
  }
}
