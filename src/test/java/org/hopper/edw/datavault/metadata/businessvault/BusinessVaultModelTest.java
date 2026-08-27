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

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BusinessVaultModelTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void checkAllowsEmptyLinkedPathWhenNoDvReferences() {
    BusinessVaultModel model = new BusinessVaultModel();
    // No required single DV model path; empty model is warning-only (no tables).
    var remarks = model.check(null, new Variables());
    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().toLowerCase().contains("data vault model file path")));
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_WARNING));
  }

  @Test
  void checkDvReferenceRequiresModelPath() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.getDvReferences().add(new BvDvTableReference("hub_customer", DvTableType.HUB));
    var remarks = model.check(null, new Variables());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("hub_customer")));
  }

  @Test
  void xmlRoundTripPreservesCoreFields() throws Exception {
    BusinessVaultModel original = new BusinessVaultModel();
    original.setName("customer-bv");
    original.setDescription("Customer business vault");
    original.setDataVaultModelPath("integration-tests/tests/basic/vault1.hdv");
    original.getConfigurationOrDefault().setTargetDatabase("Vault");
    original.getConfigurationOrDefault().setDataCatalogConnection("local-catalog");
    original.getTables().add(new BvScd2Table());
    original.getDvReferences().add(new BvDvTableReference("hub_customer", DvTableType.HUB));
    BvBvTableReference bvRef =
        new BvBvTableReference(
            "satb_product_hb",
            BvTableType.BUSINESS_TABLE,
            "${PROJECT_HOME}/models/bv-historize.hbv");
    bvRef.setPhysicalTableName("satb_product_hb");
    original.getBvReferences().add(bvRef);

    String xml =
        XmlHandler.aroundTag(
            HopBusinessVaultFileType.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);

    BusinessVaultModel restored = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, restored, null);

    assertEquals(original.getName(), restored.getName());
    assertEquals(original.getDescription(), restored.getDescription());
    assertEquals(original.getDataVaultModelPath(), restored.getDataVaultModelPath());
    assertEquals(
        original.getConfigurationOrDefault().getTargetDatabase(),
        restored.getConfigurationOrDefault().getTargetDatabase());
    assertEquals("local-catalog", restored.getConfigurationOrDefault().getDataCatalogConnection());
    assertEquals(1, restored.getTables().size());
    assertEquals(BvTableType.SCD2, restored.getTables().get(0).getTableType());
    assertEquals(1, restored.getDvReferences().size());
    assertEquals("hub_customer", restored.getDvReferences().get(0).getDvTableName());
    assertEquals(DvTableType.HUB, restored.getDvReferences().get(0).getDvTableType());
    assertEquals(1, restored.getBvReferences().size());
    assertEquals("satb_product_hb", restored.getBvReferences().get(0).getBvTableName());
    assertEquals(BvTableType.BUSINESS_TABLE, restored.getBvReferences().get(0).getBvTableType());
    assertEquals(
        "${PROJECT_HOME}/models/bv-historize.hbv",
        restored.getBvReferences().get(0).getReferencedModelFilename());
  }

  @Test
  void resolverLoadsReferencedDataVaultModel() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    BusinessVaultModel model = new BusinessVaultModel();
    model.setDataVaultModelPath(dvPath.toString());

    var remarks = model.check(null, new Variables());
    assertTrue(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_WARNING),
        "Empty BV tables should produce a warning when DV model loads");
    assertFalse(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("Unable to load")));
  }
}
