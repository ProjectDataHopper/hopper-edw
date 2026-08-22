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
package org.apache.hop.datavault.metadata.dimensional.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionScdType;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactlessFact;
import org.apache.hop.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.apache.hop.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvToDimensionalPublishTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void vault1PublishesDraftStarSchemaTables() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvPublishResult result = DvToDimensionalPublish.publish(dvModel);

    DimensionalModel published = result.getDimensionalModel();
    assertNotNull(published);
    assertEquals("vault1-draft", published.getName());
    assertTrue(published.getDescription().contains("vault1"));
    assertEquals("Vault", published.getConfigurationOrDefault().getTargetDatabase());
    assertEquals(1, published.getNotes().size());
    assertFalse(result.getWarningsOrEmpty().isEmpty());

    Set<String> tableNames =
        published.getTables().stream().map(IDmTable::getName).collect(Collectors.toSet());
    assertEquals(
        Set.of("dim_customer", "dim_product", "dim_order", "fact_order", "factless_customer_order"),
        tableNames);

    DmDimension dimCustomer = findDimension(published, "dim_customer");
    assertEquals("d_customer", dimCustomer.getTableName());
    assertEquals(DmDimensionScdType.TYPE2, dimCustomer.getScdTypeOrDefault());
    assertEquals(DmSurrogateKeyStrategy.USE_SOURCE_FIELD, dimCustomer.getSurrogateKeyStrategy());
    assertEquals("customer_hk", dimCustomer.getSurrogateKeyField());
    assertEquals("customer_hk", dimCustomer.getSurrogateKeySourceField());
    assertEquals(List.of("customer_id"), naturalKeyNames(dimCustomer));
    assertEquals(
        Set.of("name", "email", "address", "city"),
        dimCustomer.getAttributesOrEmpty().stream()
            .map(attr -> attr.getFieldName())
            .collect(Collectors.toSet()));
    assertEquals(
        DmScdUpdatePolicy.TYPE2, dimCustomer.getAttributesOrEmpty().get(0).getScdUpdatePolicy());
    assertTrue(dimCustomer.getSourceOrDefault().getSourceSql().contains("hub_customer"));

    DmDimension dimProduct = findDimension(published, "dim_product");
    assertEquals(DmDimensionScdType.TYPE2, dimProduct.getScdTypeOrDefault());
    assertEquals(
        Set.of("product_name", "category", "unit_price"),
        dimProduct.getAttributesOrEmpty().stream()
            .map(attr -> attr.getFieldName())
            .collect(Collectors.toSet()));

    DmDimension dimOrder = findDimension(published, "dim_order");
    assertEquals(List.of("order_id"), naturalKeyNames(dimOrder));
    assertEquals(Set.of("order_date"), attributeNames(dimOrder));

    DmFact factOrder = findFact(published, "fact_order");
    assertEquals("f_order", factOrder.getTableName());
    assertEquals(DmTableType.FACT, factOrder.getTableType());
    assertEquals(
        Set.of("dim_order", "dim_customer", "dim_product"),
        factOrder.getDimensionRolesOrEmpty().stream()
            .map(role -> role.getDimensionTableName())
            .collect(Collectors.toSet()));
    assertEquals(
        Set.of("quantity", "unit_price"),
        factOrder.getMeasuresOrEmpty().stream()
            .map(measure -> measure.getFieldName())
            .collect(Collectors.toSet()));
    assertTrue(factOrder.getSourceOrDefault().getSourceSql().contains("sat_order"));

    DmFactlessFact factless =
        published.getTables().stream()
            .filter(DmFactlessFact.class::isInstance)
            .map(DmFactlessFact.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals("factless_customer_order", factless.getName());
    assertEquals("f_factless_customer_order", factless.getTableName());
    assertEquals(3, factless.getDimensionRolesOrEmpty().size());
    assertTrue(factless.getSourceOrDefault().getSourceSql().contains("lnk_customer_order"));
  }

  @Test
  void publishCanRoundTripThroughXmlSerialization() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvPublishResult result = DvToDimensionalPublish.publish(dvModel);
    String xml =
        org.apache.hop.metadata.serializer.xml.XmlMetadataUtil.serializeObjectToXml(
            result.getDimensionalModel());
    org.w3c.dom.Document document =
        org.apache.hop.core.xml.XmlHandler.loadXmlString(
            "<dimensional-model>" + xml + "</dimensional-model>");
    org.w3c.dom.Node rootNode = document.getDocumentElement();
    DimensionalModel reloaded = new DimensionalModel();
    org.apache.hop.metadata.serializer.xml.XmlMetadataUtil.deSerializeFromXml(
        rootNode, DimensionalModel.class, reloaded, null);

    assertEquals(result.getDimensionalModel().getName(), reloaded.getName());
    assertEquals(result.getDimensionalModel().getTables().size(), reloaded.getTables().size());
  }

  private static DataVaultModel loadVault1Model() throws Exception {
    Path fixture = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    return DvPublishModelSupport.loadDataVaultModel(fixture.toString(), new Variables(), null);
  }

  private static DmDimension findDimension(DimensionalModel model, String name) {
    IDmTable table = model.findTable(name);
    assertNotNull(table, "Missing table " + name);
    return assertInstanceOf(DmDimension.class, table);
  }

  private static DmFact findFact(DimensionalModel model, String name) {
    IDmTable table = model.findTable(name);
    assertNotNull(table, "Missing table " + name);
    return assertInstanceOf(DmFact.class, table);
  }

  private static List<String> naturalKeyNames(DmDimension dimension) {
    return dimension.getNaturalKeysOrEmpty().stream()
        .map(key -> key.getFieldName())
        .collect(Collectors.toList());
  }

  private static Set<String> attributeNames(DmDimension dimension) {
    return dimension.getAttributesOrEmpty().stream()
        .map(attr -> attr.getFieldName())
        .collect(Collectors.toSet());
  }
}
