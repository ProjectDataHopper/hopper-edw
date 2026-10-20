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
package org.hopper.edw.datavault.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.apache.hop.core.search.SearchQuery;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.HopSourceModelFileType;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultModelSearchAnalyser;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModelSearchAnalyser;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModelSearchAnalyser;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelSearchAnalyser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class ModelContentSearchTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void dataVaultSearchFindsTablesAndNestedRecordSources() throws Exception {
    DataVaultModel model =
        load(
            "integration-tests/tests/hub-alias-role-playing/hub-alias-role-playing.hdv",
            HopVaultFileType.XML_TAG,
            DataVaultModel.class);
    assertFalse(model.getTables().isEmpty());

    DataVaultModelSearchAnalyser analyser = new DataVaultModelSearchAnalyser();
    ISearchable<DataVaultModel> searchable = searchable(model);

    assertTrue(
        hits(analyser.search(searchable, query("hub-alias-role-playing"))).stream()
            .anyMatch(hit -> "hub-alias-role-playing".equals(hit.getMatchingString())));
    assertTrue(
        component(
            analyser.search(searchable, query("lnk_party_transfer_hrp")),
            "lnk_party_transfer_hrp"));
    assertTrue(
        component(
            analyser.search(searchable, query("CRM-party-transfer-hrp")), "lnk_party_transfer_hrp"));
    assertTrue(
        component(analyser.search(searchable, query("hub_party_hrp")), "hub_party_hrp"));
  }

  @Test
  void sourceSearchFindsTablesAndColumns() throws Exception {
    SourceModel model =
        load(
            "retail-example/models/source-tables-crm.hsm",
            HopSourceModelFileType.XML_TAG,
            SourceModel.class);
    SourceModelSearchAnalyser analyser = new SourceModelSearchAnalyser();
    ISearchable<SourceModel> searchable = searchable(model);

    assertTrue(component(analyser.search(searchable, query("order_header")), "order_header"));
    assertTrue(component(analyser.search(searchable, query("address_line1")), "customer_address"));
  }

  @Test
  void businessVaultSearchFindsTablesAndMappedFields() throws Exception {
    BusinessVaultModel model =
        load(
            "integration-tests/tests/multi-satellite-bv/customer-360.hbv",
            HopBusinessVaultFileType.XML_TAG,
            BusinessVaultModel.class);
    BusinessVaultModelSearchAnalyser analyser = new BusinessVaultModelSearchAnalyser();
    ISearchable<BusinessVaultModel> searchable = searchable(model);

    assertTrue(component(analyser.search(searchable, query("customer_360_bv")), "customer_360_bv"));
    assertTrue(component(analyser.search(searchable, query("cust_segment")), "customer_360_bv"));
  }

  @Test
  void dimensionalSearchFindsTablesAndSourceFields() throws Exception {
    DimensionalModel model =
        load(
            "retail-example/models/retail-f-orders.hdm",
            HopDimensionalFileType.XML_TAG,
            DimensionalModel.class);
    DimensionalModelSearchAnalyser analyser = new DimensionalModelSearchAnalyser();
    ISearchable<DimensionalModel> searchable = searchable(model);

    assertTrue(component(analyser.search(searchable, query("f_orders")), "f_orders"));
    assertTrue(component(analyser.search(searchable, query("shipping_date")), "f_orders"));
  }

  private static SearchQuery query(String text) {
    return new SearchQuery(text, false, false);
  }

  private static List<ISearchResult> hits(List<ISearchResult> results) {
    assertFalse(results.isEmpty());
    return results;
  }

  private static boolean component(List<ISearchResult> results, String component) {
    return hits(results).stream().anyMatch(hit -> component.equals(hit.getComponent()));
  }

  private static <T> T load(String relativePath, String xmlTag, Class<T> type) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toString());
    Node rootNode = XmlHandler.getSubNode(document, xmlTag);
    if (rootNode == null) {
      rootNode = document.getDocumentElement();
    }
    T model = type.getDeclaredConstructor().newInstance();
    XmlMetadataUtil.deSerializeFromXml(rootNode, type, model, null);
    return model;
  }

  private static <T> ISearchable<T> searchable(T model) {
    return new ISearchable<>() {
      @Override
      public String getLocation() {
        return "test";
      }

      @Override
      public String getName() {
        return "model";
      }

      @Override
      public String getType() {
        return "model";
      }

      @Override
      public String getFilename() {
        return "model";
      }

      @Override
      public T getSearchableObject() {
        return model;
      }

      @Override
      public ISearchableCallback getSearchCallback() {
        return null;
      }
    };
  }
}
