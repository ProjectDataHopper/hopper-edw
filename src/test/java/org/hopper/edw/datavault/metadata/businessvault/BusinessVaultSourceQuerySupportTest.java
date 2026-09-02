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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BusinessVaultSourceQuerySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void addAndRemoveSourceQueryHop() {
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("customer_360_bv");
    BvSourceQuery source = new BvSourceQuery();
    source.setName("sat_customer_corrected");

    assertTrue(BusinessVaultSourceQuerySupport.addSourceQuery(scd2, source));
    assertFalse(BusinessVaultSourceQuerySupport.addSourceQuery(scd2, source));
    assertTrue(BusinessVaultSourceQuerySupport.hasSourceQuery(scd2, "sat_customer_corrected"));
    assertTrue(BusinessVaultSourceQuerySupport.removeSourceQuery(scd2, "sat_customer_corrected"));
    assertFalse(BusinessVaultSourceQuerySupport.hasSourceQuery(scd2, "sat_customer_corrected"));
  }

  @Test
  void emptyConnectionUsesDvTarget() {
    BvSourceQuery source = new BvSourceQuery();
    source.setName("sat_customer_corrected");
    DataVaultModel dvModel = new DataVaultModel();
    dvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    assertEquals(
        "Vault",
        BusinessVaultSourceQuerySupport.resolveConnectionName(source, dvModel, new Variables()));

    source.setConnectionName("CRM");
    assertEquals(
        "CRM",
        BusinessVaultSourceQuerySupport.resolveConnectionName(source, dvModel, new Variables()));
  }

  @Test
  void xmlRoundTripPreservesSourceQueryAndHop() throws Exception {
    BusinessVaultModel original = new BusinessVaultModel();
    original.setName("customer-bv");
    BvSourceQuery source = new BvSourceQuery();
    source.setName("sat_customer_corrected");
    source.setTableName("sat_customer_v");
    source.setHashKeyField("customer_hk");
    source.setConnectionName("CRM");
    source.setSourceKind(BvSourceQueryKind.SQL);
    source.setSqlQuery("SELECT customer_hk, x_load_ts FROM sat_customer");
    BvSourceQueryColumn column = new BvSourceQueryColumn("customer_hk");
    column.setDataType("String");
    source.getColumns().add(column);
    original.getTables().add(source);

    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("sat_customer_hb");
    scd2.setTableName("sat_customer_hb");
    scd2.getSourceQueryRefs().add(new BvSourceQueryRef("sat_customer_corrected"));
    original.getTables().add(scd2);

    String xml =
        XmlHandler.aroundTag(
            HopBusinessVaultFileType.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);

    BusinessVaultModel restored = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, restored, null);

    assertEquals(2, restored.getTables().size());
    BvSourceQuery restoredSource = (BvSourceQuery) restored.findTable("sat_customer_corrected");
    assertEquals(BvTableType.SOURCE_QUERY, restoredSource.getTableType());
    assertEquals("CRM", restoredSource.getConnectionName());
    assertEquals(BvSourceQueryKind.SQL, restoredSource.getSourceKind());
    assertEquals("customer_hk", restoredSource.getHashKeyField());
    assertEquals(1, restoredSource.getColumns().size());
    BvScd2Table restoredScd2 = (BvScd2Table) restored.findTable("sat_customer_hb");
    assertEquals(1, restoredScd2.getSourceQueryRefs().size());
    assertEquals(
        "sat_customer_corrected", restoredScd2.getSourceQueryRefs().get(0).getSourceQueryName());
  }
}
