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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSource;
import org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyField;
import org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvLinkUpdatePipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void linkPipelineUsesHubBusinessKeyNamesWhenMappingLeftEmpty() throws Exception {
    DataVaultModel model = buildCustomerInvoiceLinkModelWithImplicitMappings();

    DvLink link = model.findLink("lnk_customer_invoice");
    List<PipelineMeta> pipelines =
        link.generateUpdatePipelines(
            testMetadataProvider(), new Variables(), model, new Date(), null);

    assertEquals(1, pipelines.size());
    TableInputMeta sourceMeta =
        (TableInputMeta)
            pipelines.get(0).getTransforms().stream()
                .filter(t -> t.getName().startsWith("source"))
                .findFirst()
                .orElseThrow()
                .getTransform();

    assertTrue(sourceMeta.getSql().contains("NUM_0"));
    assertTrue(sourceMeta.getSql().contains("LIN_0"));
    assertTrue(sourceMeta.getSql().contains("BPCINV_0"));
    assertFalse(sourceMeta.getSql().contains("SELECT ,"));
  }

  @Test
  void linkPipelineUsesMappedSourceFieldsForHubHashKeys() throws Exception {
    DataVaultModel model = buildCustomerInvoiceLinkModel();

    DvLink link = model.findLink("lnk_customer_invoice");
    List<PipelineMeta> pipelines =
        link.generateUpdatePipelines(
            testMetadataProvider(), new Variables(), model, new Date(), null);

    assertEquals(1, pipelines.size());
    PipelineMeta pipeline = pipelines.get(0);

    DvHashKeyMeta customerHashMeta =
        (DvHashKeyMeta)
            pipeline.getTransforms().stream()
                .filter(t -> "calc_customer_hk".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(
        List.of("BPCINV_0"),
        customerHashMeta.getFields().stream().map(DvHashKeyField::getName).toList());

    DvHashKeyMeta invoiceHashMeta =
        (DvHashKeyMeta)
            pipeline.getTransforms().stream()
                .filter(t -> "calc_invoice_hk".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals(
        List.of("NUM_0", "LIN_0"),
        invoiceHashMeta.getFields().stream().map(DvHashKeyField::getName).toList());

    TableInputMeta sourceMeta =
        (TableInputMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getName().startsWith("source"))
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertTrue(sourceMeta.getSql().contains("BPCINV_0"));
    assertTrue(sourceMeta.getSql().contains("NUM_0"));
    assertTrue(sourceMeta.getSql().contains("LIN_0"));
  }

  private static DataVaultModel buildCustomerInvoiceLinkModelWithImplicitMappings() {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");

    DvHub hubCustomer = new DvHub("hub_customer");
    hubCustomer.setHashKeyFieldName("customer_hk");
    hubCustomer.setBusinessKeys(List.of(businessKey("BPCINV_0")));

    DvHub hubInvoice = new DvHub("hub_invoice");
    hubInvoice.setHashKeyFieldName("invoice_hk");
    hubInvoice.setBusinessKeys(List.of(businessKey("NUM_0"), businessKey("LIN_0")));

    DvLink link = new DvLink("lnk_customer_invoice");
    link.setLinkHashKeyFieldName("lnk_customer_invoice_hk");
    link.setTableName("lnk_customer_invoice");
    link.getHubNames().add("hub_invoice");
    link.getHubNames().add("hub_customer");

    TestLinkHubSource linkSource = new TestLinkHubSource(invoiceSource());
    linkSource.setSourceName("invoice-source");

    DvLink.HubSourceKeyField invoiceKeys = new DvLink.HubSourceKeyField();
    invoiceKeys.setHubName("hub_invoice");
    DvLink.HubSourceKeyField customerKeys = new DvLink.HubSourceKeyField();
    customerKeys.setHubName("hub_customer");
    linkSource.getHubSourceKeyFields().addAll(List.of(invoiceKeys, customerKeys));
    link.getLinkHubSources().add(linkSource);

    model.getTables().addAll(List.of(hubCustomer, hubInvoice, link));
    return model;
  }

  private static DataVaultModel buildCustomerInvoiceLinkModel() {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");

    DvHub hubCustomer = new DvHub("hub_customer");
    hubCustomer.setHashKeyFieldName("customer_hk");
    hubCustomer.setBusinessKeys(List.of(businessKey("BPCNUM_0")));

    DvHub hubInvoice = new DvHub("hub_invoice");
    hubInvoice.setHashKeyFieldName("invoice_hk");
    hubInvoice.setBusinessKeys(List.of(businessKey("NUM_0"), businessKey("LIN_0")));

    DvLink link = new DvLink("lnk_customer_invoice");
    link.setLinkHashKeyFieldName("lnk_customer_invoice_hk");
    link.setTableName("lnk_customer_invoice");
    link.getHubNames().add("hub_invoice");
    link.getHubNames().add("hub_customer");

    TestLinkHubSource linkSource = new TestLinkHubSource(invoiceSource());
    linkSource.setSourceName("invoice-source");

    DvLink.HubSourceKeyField invoiceKeys = new DvLink.HubSourceKeyField();
    invoiceKeys.setHubName("hub_invoice");
    invoiceKeys
        .getSourceBusinessKeyFields()
        .addAll(
            List.of(
                new BusinessKeySource("NUM_0", "NUM_0"), new BusinessKeySource("LIN_0", "LIN_0")));

    DvLink.HubSourceKeyField customerKeys = new DvLink.HubSourceKeyField();
    customerKeys.setHubName("hub_customer");
    customerKeys.getSourceBusinessKeyFields().add(new BusinessKeySource("BPCNUM_0", "BPCINV_0"));

    linkSource.getHubSourceKeyFields().addAll(List.of(invoiceKeys, customerKeys));
    link.getLinkHubSources().add(linkSource);

    model.getTables().addAll(List.of(hubCustomer, hubInvoice, link));
    return model;
  }

  private static BusinessKey businessKey(String name) {
    BusinessKey key = new BusinessKey(name);
    key.setDataType("String");
    key.setLength("20");
    return key;
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
    for (String name : List.of("NUM_0", "LIN_0", "BPCINV_0")) {
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

  private static final class TestLinkHubSource extends DvLink.DvLinkHubSource {
    private final DataVaultSource recordSource;

    private TestLinkHubSource(DataVaultSource recordSource) {
      this.recordSource = recordSource;
    }

    @Override
    public DataVaultSource resolveSource(
        org.apache.hop.core.variables.IVariables variables,
        IHopMetadataProvider metadataProvider,
        DataVaultModel model) {
      return recordSource;
    }
  }
}
