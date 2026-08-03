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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvRecordSourceValidationTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void hubCheckFlagsEmptyRecordSources() {
    DvHub hub = new DvHub("h_invoice");
    BusinessKey num = new BusinessKey("NUM_0");
    num.setRecordSourceName("invoice-source");
    hub.setBusinessKeys(List.of(num));

    List<ICheckResult> remarks = new ArrayList<>();
    hub.check(remarks, null, new Variables(), DvModelCheckOptions.fastOnly(), new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("No record source")));
  }

  @Test
  void satelliteCheckFlagsMissingDefaultRecordSource() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("h_invoice");
    hub.setRecordSources(List.of("invoice-source"));
    model.getTables().add(hub);

    DvSatellite satellite = new DvSatellite("s_invoice");
    satellite.setHubName("h_invoice");
    model.getTables().add(satellite);

    List<ICheckResult> remarks = new ArrayList<>();
    satellite.check(remarks, null, new Variables(), DvModelCheckOptions.fastOnly(), model);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("No record source")));
  }

  @Test
  void satelliteCheckFlagsMissingSourceIndicator() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("h_invoice");
    hub.setRecordSources(List.of("invoice-source"));
    model.getTables().add(hub);

    DataVaultSource source = invoiceSource();
    source.setSourceIndicator(null);
    source.setSourceIndicatorField(null);

    TestSatellite satellite = new TestSatellite("s_invoice", source);
    satellite.setHubName("h_invoice");
    satellite.setRecordSourceName("invoice-source");
    satellite.setAttributes(List.of(attribute("CLE_0")));
    model.getTables().add(satellite);

    List<ICheckResult> remarks = new ArrayList<>();
    satellite.check(
        remarks,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        model);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("invoice-source")
                        && r.getText().contains("s_invoice")));
  }

  @Test
  void satelliteWarnsWhenRecordSourceNotListedOnParentHub() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("h_invoice");
    hub.setRecordSources(List.of("hub-source"));
    model.getTables().add(hub);

    DvSatellite satellite = new TestSatellite("s_invoice", invoiceSource());
    satellite.setHubName("h_invoice");
    satellite.setRecordSourceName("sat-source");
    satellite.setAttributes(List.of(attribute("CLE_0")));
    model.getTables().add(satellite);

    List<ICheckResult> remarks = new ArrayList<>();
    satellite.check(
        remarks,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        model);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_WARNING
                        && r.getText().contains("sat-source")
                        && r.getText().contains("h_invoice")));
  }

  @Test
  void hubSatellitePipelineUsesParentHubRecordSourceColumnName() throws Exception {
    DataVaultModel model = buildInvoiceModel();
    TestSatellite satellite = (TestSatellite) model.findTable("s_invoice");

    List<PipelineMeta> pipelines =
        satellite.generateUpdatePipelines(
            testMetadataProvider(), new Variables(), model, new Date(), null);

    assertEquals(1, pipelines.size());
    PipelineMeta pipeline = pipelines.get(0);

    SelectValuesMeta selectMeta =
        (SelectValuesMeta)
            pipeline.getTransforms().stream()
                .filter(t -> "hk plus attr".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(
        selectMeta.getSelectOption().getSelectFields().stream()
            .map(SelectField::getName)
            .anyMatch("src_invoice"::equals));

    TableInputMeta sourceMeta =
        (TableInputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().startsWith("source"))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(sourceMeta.getSql().contains("src_invoice"));

    assertTrue(
        satellite
            .getTargetTableLayout(testMetadataProvider(), new Variables(), model)
            .getValueMetaList()
            .stream()
            .anyMatch(meta -> "src_invoice".equals(meta.getName())));
  }

  @Test
  void resolveRecordSourceFieldNameForSatelliteUsesParentHubOverride() throws HopException {
    DataVaultModel model = buildInvoiceModel();
    DvSatellite satellite = (DvSatellite) model.findTable("s_invoice");

    assertEquals(
        "src_invoice",
        DvSourceFieldMappingSupport.resolveRecordSourceFieldNameForSatellite(
            model.getConfigurationOrDefault(), model, satellite, new Variables()));
  }

  private static DataVaultModel buildInvoiceModel() {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");
    config.setRecordSourceField("RECORD_SOURCE");

    DvHub hub = new DvHub("h_invoice");
    hub.setHashKeyFieldName("hk_invoice");
    hub.setRecordSourceFieldName("src_invoice");
    hub.setRecordSources(List.of("invoice-source"));
    hub.setBusinessKeys(
        List.of(
            businessKey("NUM_0", "NUM_0", "invoice-source"),
            businessKey("LIN_0", "LIN_0", "invoice-source")));

    TestSatellite satellite = new TestSatellite("s_invoice", invoiceSource());
    satellite.setHubName("h_invoice");
    satellite.setRecordSourceName("invoice-source");
    satellite.setAttributes(List.of(attribute("CLE_0"), attribute("CPY_0")));

    model.getTables().add(hub);
    model.getTables().add(satellite);
    return model;
  }

  private static DataVaultSource invoiceSource() {
    DataVaultSource source = new DataVaultSource("invoice-source");
    source.setSourceIndicator("X3-dbo-STA_XBISID_XBISDH");
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDatabaseName("X3");
    dbSource.setSchemaName("dbo");
    dbSource.setTableName("STA_XBISID_XBISDH");
    source.setSource(dbSource);
    List<SourceField> fields = new ArrayList<>();
    for (String name : List.of("NUM_0", "LIN_0", "CLE_0", "CPY_0")) {
      SourceField field = new SourceField();
      field.setName(name);
      field.setSourceDataType("String");
      field.setLength("20");
      field.setHopType(IValueMeta.TYPE_STRING);
      fields.add(field);
    }
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static BusinessKey businessKey(String name, String sourceField, String recordSource) {
    BusinessKey key = new BusinessKey(name);
    key.setSourceFieldName(sourceField);
    key.setRecordSourceName(recordSource);
    key.setDataType("String");
    key.setLength("20");
    return key;
  }

  private static SatelliteAttribute attribute(String name) {
    SatelliteAttribute attribute = new SatelliteAttribute();
    attribute.setName(name);
    attribute.setDataType("String");
    attribute.setLength("20");
    return attribute;
  }

  private static MemoryMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta vault = new DatabaseMeta();
    vault.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(vault);
    DatabaseMeta sourceDb = new DatabaseMeta();
    sourceDb.setName("X3");
    metadataProvider.getSerializer(DatabaseMeta.class).save(sourceDb);
    return metadataProvider;
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
