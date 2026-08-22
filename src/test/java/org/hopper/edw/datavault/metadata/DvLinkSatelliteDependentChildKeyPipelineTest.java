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
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSource;
import org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyField;
import org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Link satellites must use the same parent link-hash composition as the link load (hub hashes +
 * dependent child keys). Otherwise sat FK to the link table fails at bulk load.
 */
class DvLinkSatelliteDependentChildKeyPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void linkAndLinkSatelliteShareDependentChildKeyInSourceSqlAndFinalHash() throws Exception {
    DataVaultModel model = buildOrderLineModel();
    IHopMetadataProvider metadata = testMetadataProvider();
    Variables variables = new Variables();
    Date loadDate = new Date();

    DvLink link = model.findLink("lnk_order_line");
    DvSatellite satellite = (DvSatellite) model.findTable("sat_lnk_order_line");

    List<PipelineMeta> linkPipes =
        link.generateUpdatePipelines(metadata, variables, model, loadDate, null);
    List<PipelineMeta> satPipes =
        satellite.generateUpdatePipelines(metadata, variables, model, loadDate, null);

    assertEquals(1, linkPipes.size());
    assertEquals(1, satPipes.size());

    String linkSql = firstSourceSql(linkPipes.get(0));
    String satSql = firstSourceSql(satPipes.get(0));
    assertTrue(linkSql.contains("line_number"), "link SQL must select DCK: " + linkSql);
    assertTrue(satSql.contains("line_number"), "sat SQL must select DCK: " + satSql);

    List<String> linkFinalHashFields = finalLinkHashFields(linkPipes.get(0), "lnk_order_line_hk");
    List<String> satFinalHashFields = finalLinkHashFields(satPipes.get(0), "lnk_order_line_hk");

    assertEquals(
        List.of("order_hk", "product_hk", "line_number"),
        linkFinalHashFields,
        "link final hash fields");
    assertEquals(
        linkFinalHashFields,
        satFinalHashFields,
        "link satellite must use the same final link-hash field list as the link load");

    // Intermediate hub hashes (order_hk / product_hk) must hash the same source BK fields.
    assertEquals(
        hashInputFields(linkPipes.get(0), "order_hk"),
        hashInputFields(satPipes.get(0), "order_hk"),
        "order hub hash inputs");
    assertEquals(
        hashInputFields(linkPipes.get(0), "product_hk"),
        hashInputFields(satPipes.get(0), "product_hk"),
        "product hub hash inputs");
  }

  private static String firstSourceSql(PipelineMeta pipeline) {
    for (TransformMeta transform : pipeline.getTransforms()) {
      if (transform.getTransform() instanceof TableInputMeta tableInput) {
        return tableInput.getSql();
      }
    }
    throw new AssertionError("No TableInput in pipeline " + pipeline.getName());
  }

  private static List<String> finalLinkHashFields(PipelineMeta pipeline, String resultField) {
    return hashInputFields(pipeline, resultField);
  }

  private static List<String> hashInputFields(PipelineMeta pipeline, String resultField) {
    for (TransformMeta transform : pipeline.getTransforms()) {
      if (transform.getTransform() instanceof DvHashKeyMeta hashMeta
          && resultField.equals(hashMeta.getResultFieldName())) {
        List<String> names = new ArrayList<>();
        for (DvHashKeyField field : hashMeta.getFields()) {
          names.add(field.getName());
        }
        return names;
      }
    }
    throw new AssertionError(
        "No DvHashKey producing " + resultField + " in pipeline " + pipeline.getName());
  }

  private static DataVaultModel buildOrderLineModel() {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");
    config.setHashKeyDataType(HashKeyDataType.STRING.name());
    config.setHashAlgorithm(HashAlgorithm.MD5.name());
    config.setHashContentCasing(HashContentCasing.UPPER.name());

    DvHub hubOrder = new DvHub("hub_order");
    hubOrder.setHashKeyFieldName("order_hk");
    BusinessKey orderId = new BusinessKey("order_id");
    orderId.setDataType("String");
    orderId.setSourceFieldName("order_id");
    hubOrder.setBusinessKeys(List.of(orderId));

    DvHub hubProduct = new DvHub("hub_product");
    hubProduct.setHashKeyFieldName("product_hk");
    BusinessKey productId = new BusinessKey("product_id");
    productId.setDataType("String");
    productId.setSourceFieldName("product_id");
    hubProduct.setBusinessKeys(List.of(productId));

    DvLink link = new DvLink("lnk_order_line");
    link.setLinkHashKeyFieldName("lnk_order_line_hk");
    link.setTableName("lnk_order_line");
    link.getHubNames().add("hub_order");
    link.getHubNames().add("hub_product");
    DependentChildKey lineNumber = new DependentChildKey("line_number");
    lineNumber.setSourceFieldName("line_number");
    lineNumber.setDataType("Integer");
    link.getDependentChildKeys().add(lineNumber);

    TestLinkHubSource linkHubSource = new TestLinkHubSource(orderLineSource());
    linkHubSource.setSourceName("E2E-order-line");
    DvLink.HubSourceKeyField orderKeys = new DvLink.HubSourceKeyField();
    orderKeys.setHubName("hub_order");
    orderKeys.getSourceBusinessKeyFields().add(new BusinessKeySource("order_id", "order_id"));
    DvLink.HubSourceKeyField productKeys = new DvLink.HubSourceKeyField();
    productKeys.setHubName("hub_product");
    productKeys.getSourceBusinessKeyFields().add(new BusinessKeySource("product_id", "product_id"));
    linkHubSource.getHubSourceKeyFields().addAll(List.of(orderKeys, productKeys));
    link.getLinkHubSources().add(linkHubSource);

    TestLinkSatelliteSource linkSatSource = new TestLinkSatelliteSource(orderLineSource());
    linkSatSource.setSourceName("E2E-order-line");
    DvLink.SatelliteSourceKeyField satKeys = new DvLink.SatelliteSourceKeyField();
    satKeys.setSatelliteName("sat_lnk_order_line");
    satKeys.getAttributeSources().add(attributeSource("quantity", "quantity"));
    satKeys.getAttributeSources().add(attributeSource("unit_price", "unit_price"));
    linkSatSource.getSatelliteSourceKeyFields().add(satKeys);
    link.getLinkSatelliteSources().add(linkSatSource);

    TestSatellite satellite = new TestSatellite("sat_lnk_order_line", orderLineSource());
    satellite.setLinkName("lnk_order_line");
    satellite.setTableName("sat_lnk_order_line");
    satellite.setRecordSourceName("E2E-order-line");
    SatelliteAttribute qty = new SatelliteAttribute();
    qty.setName("quantity");
    qty.setDataType("Integer");
    SatelliteAttribute price = new SatelliteAttribute();
    price.setName("unit_price");
    price.setDataType("Number");
    satellite.setAttributes(List.of(qty, price));

    model.getTables().addAll(List.of(hubOrder, hubProduct, link, satellite));
    return model;
  }

  private static AttributeSource attributeSource(String attribute, String sourceField) {
    AttributeSource as = new AttributeSource();
    as.setAttributeField(attribute);
    as.setSourceFieldName(sourceField);
    return as;
  }

  private static DataVaultSource orderLineSource() {
    DataVaultSource source = new DataVaultSource("E2E-order-line");
    source.setSourceIndicator("E2E-order-line");
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDatabaseName("CRM");
    dbSource.setSchemaName("public");
    dbSource.setTableName("order_line");
    source.setSource(dbSource);
    List<SourceField> fields = new ArrayList<>();
    for (String name : List.of("order_id", "product_id", "line_number", "quantity", "unit_price")) {
      SourceField field = new SourceField();
      field.setName(name);
      field.setSourceDataType(
          name.contains("line") || name.equals("quantity") ? "Integer" : "String");
      field.setHopType(
          name.contains("line") || name.equals("quantity")
              ? IValueMeta.TYPE_INTEGER
              : IValueMeta.TYPE_STRING);
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
    DatabaseMeta crm = new DatabaseMeta();
    crm.setName("CRM");
    metadataProvider.getSerializer(DatabaseMeta.class).save(crm);
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

  private static final class TestLinkSatelliteSource extends DvLink.DvLinkSatelliteSource {
    private final DataVaultSource recordSource;

    private TestLinkSatelliteSource(DataVaultSource recordSource) {
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
