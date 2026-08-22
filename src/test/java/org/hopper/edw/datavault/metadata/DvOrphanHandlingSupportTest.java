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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.Condition;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

class DvOrphanHandlingSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void modelPolicyDefaultsToPass() {
    assertEquals(DvOrphanPolicy.PASS, DvOrphanHandlingSupport.resolveModelPolicy(null));
    assertEquals(
        DvOrphanPolicy.PASS,
        DvOrphanHandlingSupport.resolveModelPolicy(new DataVaultConfiguration()));
  }

  @Test
  void hubAllowInferredInsertRoundTripsThroughXml() throws Exception {
    DataVaultModel original = new DataVaultModel();
    original.setName("retail");
    DvHub allowed = new DvHub("hub_customer");
    allowed.setAllowInferredInsert(true);
    DvHub denied = new DvHub("hub_external");
    denied.setAllowInferredInsert(false);
    original.getTables().add(allowed);
    original.getTables().add(denied);

    String xml =
        XmlHandler.aroundTag(
            HopVaultFileType.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    DataVaultModel loaded = new DataVaultModel();
    Node root = XmlHandler.loadXmlString(xml, HopVaultFileType.XML_TAG);
    XmlMetadataUtil.deSerializeFromXml(root, DataVaultModel.class, loaded, null);

    assertTrue(loaded.findHub("hub_customer").isAllowInferredInsert());
    assertEquals(false, loaded.findHub("hub_external").isAllowInferredInsert());
  }

  @Test
  void inheritUsesModelDefault() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setOrphanPolicy(DvOrphanPolicy.INFER.name());
    assertEquals(DvOrphanPolicy.INFER, DvOrphanHandlingSupport.resolveEffective(null, config));
    assertEquals(
        DvOrphanPolicy.INFER,
        DvOrphanHandlingSupport.resolveEffective(DvOrphanPolicy.INHERIT.name(), config));
    assertEquals(
        DvOrphanPolicy.QUARANTINE,
        DvOrphanHandlingSupport.resolveEffective(DvOrphanPolicy.QUARANTINE.name(), config));
  }

  @Test
  void seedHubAddsSourceAndMappingOnce() {
    DvHub hub = new DvHub("hub_customer");
    hub.getRecordSources().add("customer");
    BusinessKey existing = new BusinessKey("customer_id");
    existing.setDataType("String");
    existing.setLength("20");
    existing.setRecordSourceName("customer");
    existing.setSourceFieldName("customer_id");
    hub.getBusinessKeys().add(existing);

    List<BusinessKeySource> mappings = List.of(new BusinessKeySource("customer_id", "customer_fk"));
    int added =
        DvOrphanHandlingSupport.seedHubFromChildSource(hub, "order", mappings, new Variables());

    assertEquals(1, added);
    assertTrue(hub.getRecordSources().contains("order"));
    assertEquals(2, hub.getBusinessKeys().size());
    BusinessKey seeded = hub.getBusinessKeys().get(1);
    assertEquals("customer_id", seeded.getName());
    assertEquals("order", seeded.getRecordSourceName());
    assertEquals("customer_fk", seeded.getSourceFieldName());

    int second =
        DvOrphanHandlingSupport.seedHubFromChildSource(hub, "order", mappings, new Variables());
    assertEquals(0, second);
    assertEquals(2, hub.getBusinessKeys().size());
  }

  @Test
  void checkPassDoesNotFlagChildLinkSource() {
    DataVaultModel model = customerOrderModel();
    List<ICheckResult> remarks = new ArrayList<>();
    DvOrphanHandlingSupport.checkLink(
        model.findLink("lnk_order"),
        model,
        model.getConfigurationOrDefault(),
        new Variables(),
        remarks);
    assertTrue(remarks.isEmpty());
  }

  @Test
  void checkPassDoesNotFlagChildLinkSourceWhenForeignKeysEnabled() {
    DataVaultModel model = customerOrderModel();
    model.getConfigurationOrDefault().setGenerateForeignKeys(true);
    List<ICheckResult> remarks = new ArrayList<>();
    DvOrphanHandlingSupport.checkLink(
        model.findLink("lnk_order"),
        model,
        model.getConfigurationOrDefault(),
        new Variables(),
        remarks);
    assertTrue(remarks.isEmpty());
  }

  @Test
  void checkErrorsWhenInferIsRefusedOnHub() {
    DataVaultModel model = customerOrderModel();
    model.getConfigurationOrDefault().setOrphanPolicy(DvOrphanPolicy.INFER.name());
    model.findHub("hub_customer").setAllowInferredInsert(false);
    List<ICheckResult> remarks = new ArrayList<>();
    DvOrphanHandlingSupport.checkLink(
        model.findLink("lnk_order"),
        model,
        model.getConfigurationOrDefault(),
        new Variables(),
        remarks);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("INFER")));
  }

  @Test
  void passPolicyDoesNotChangeLinkPipeline() throws Exception {
    DataVaultModel model = invoiceLinkModel();
    IHopMetadataProvider provider = invoiceMetadataProvider();
    DvLink link = model.findLink("lnk_customer_invoice");
    List<PipelineMeta> baseline =
        link.generateUpdatePipelines(provider, new Variables(), model, new Date(), null);
    model.getConfigurationOrDefault().setOrphanPolicy(DvOrphanPolicy.PASS.name());
    List<PipelineMeta> again =
        link.generateUpdatePipelines(provider, new Variables(), model, new Date(), null);
    assertEquals(baseline.get(0).nrTransforms(), again.get(0).nrTransforms());
    assertTrue(
        again.get(0).getTransforms().stream()
            .noneMatch(t -> t.getName().startsWith("filter_null_")));
  }

  @Test
  void inferPolicyAddsInsertUpdate() throws Exception {
    DataVaultModel model = invoiceLinkModel();
    model.getConfigurationOrDefault().setOrphanPolicy(DvOrphanPolicy.INFER.name());
    IHopMetadataProvider provider = invoiceMetadataProvider();
    List<PipelineMeta> pipelines =
        model
            .findLink("lnk_customer_invoice")
            .generateUpdatePipelines(provider, new Variables(), model, new Date(), null);
    assertTrue(
        pipelines.get(0).getTransforms().stream()
            .anyMatch(t -> t.getName().startsWith("infer_hub_")));
    assertTrue(
        pipelines.get(0).getTransforms().stream()
            .anyMatch(t -> t.getName().startsWith("filter_null_")));
  }

  @Test
  void nullKeyConditionUsesOrForMultipleFields() throws Exception {
    Condition condition =
        DvOrphanHandlingSupport.buildNullKeyCondition(List.of("customer_fk", "store_fk"));
    assertTrue(condition.isComposite());
    assertEquals(2, condition.getChildren().size());
  }

  private static DataVaultModel customerOrderModel() {
    DataVaultModel model = new DataVaultModel();
    DvHub customer = new DvHub("hub_customer");
    customer.setTableName("hub_customer");
    customer.setHashKeyFieldName("customer_hk");
    customer.getRecordSources().add("customer");
    BusinessKey bk = new BusinessKey("customer_id");
    bk.setDataType("String");
    bk.setLength("20");
    bk.setSourceFieldName("customer_id");
    bk.setRecordSourceName("customer");
    customer.getBusinessKeys().add(bk);
    model.getTables().add(customer);

    DvHub order = new DvHub("hub_order");
    order.setTableName("hub_order");
    order.setHashKeyFieldName("order_hk");
    order.getRecordSources().add("order");
    BusinessKey orderBk = new BusinessKey("order_id");
    orderBk.setDataType("String");
    orderBk.setLength("20");
    orderBk.setSourceFieldName("order_id");
    orderBk.setRecordSourceName("order");
    order.getBusinessKeys().add(orderBk);
    model.getTables().add(order);

    DvLink link = new DvLink("lnk_order");
    link.setTableName("lnk_order");
    link.setLinkHashKeyFieldName("lnk_order_hk");
    link.setHubNames(List.of("hub_customer", "hub_order"));
    DvLink.DvLinkHubSource source = new DvLink.DvLinkHubSource();
    source.setSource("order");
    DvLink.HubSourceKeyField customerMap = new DvLink.HubSourceKeyField();
    customerMap.setHubName("hub_customer");
    customerMap
        .getSourceBusinessKeyFields()
        .add(new BusinessKeySource("customer_id", "customer_fk"));
    source.getHubSourceKeyFields().add(customerMap);
    DvLink.HubSourceKeyField orderMap = new DvLink.HubSourceKeyField();
    orderMap.setHubName("hub_order");
    orderMap.getSourceBusinessKeyFields().add(new BusinessKeySource("order_id", "order_id"));
    source.getHubSourceKeyFields().add(orderMap);
    link.getLinkHubSources().add(source);
    model.getTables().add(link);
    return model;
  }

  private static DataVaultModel invoiceLinkModel() {
    DataVaultModel model = new DataVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");

    DvHub hubCustomer = new DvHub("hub_customer");
    hubCustomer.setHashKeyFieldName("customer_hk");
    hubCustomer.setBusinessKeys(List.of(namedKey("BPCNUM_0")));

    DvHub hubInvoice = new DvHub("hub_invoice");
    hubInvoice.setHashKeyFieldName("invoice_hk");
    hubInvoice.setBusinessKeys(List.of(namedKey("NUM_0"), namedKey("LIN_0")));

    DvLink link = new DvLink("lnk_customer_invoice");
    link.setLinkHashKeyFieldName("lnk_customer_invoice_hk");
    link.setTableName("lnk_customer_invoice");
    link.getHubNames().add("hub_invoice");
    link.getHubNames().add("hub_customer");

    TestLinkHubSource linkSource = new TestLinkHubSource(invoiceSource());
    linkSource.setSource("invoice-source");

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

  private static BusinessKey namedKey(String name) {
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

  private static MemoryMetadataProvider invoiceMetadataProvider() throws HopException {
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
