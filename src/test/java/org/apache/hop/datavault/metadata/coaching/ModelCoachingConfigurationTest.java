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
package org.apache.hop.datavault.metadata.coaching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.ModelCoachPanelAuditSupport;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class ModelCoachingConfigurationTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void dataVaultModelRoundTripsCoachingSources() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setName("coach-test");
    CoachingSourceRef ref =
        CoachingSourceRef.forRecordDefinition("local-catalog", "hop/test/sources", "CRM-customer");
    model.getCoachingOrDefault().addCoachingSource(ref);

    DataVaultModel loaded = roundTrip(model, DataVaultModel.class, "data-vault-model");
    assertEquals(1, loaded.getCoachingOrDefault().getCoachingSourcesOrEmpty().size());
    CoachingSourceRef loadedRef =
        loaded.getCoachingOrDefault().getCoachingSourcesOrEmpty().getFirst();
    assertEquals("CRM-customer", loadedRef.getRecordName());
    assertEquals("local-catalog", loadedRef.getCatalogConnection());
  }

  @Test
  void businessVaultModelRoundTripsCoachingSources() throws Exception {
    BusinessVaultModel model = new BusinessVaultModel();
    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/test/sources", "E2E-order"));
    BusinessVaultModel loaded = roundTrip(model, BusinessVaultModel.class, "business-vault-model");
    assertEquals(
        "E2E-order",
        loaded.getCoachingOrDefault().getCoachingSourcesOrEmpty().getFirst().getRecordName());
  }

  @Test
  void dimensionalModelRoundTripsCoachingSources() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/test/sources", "dim-source"));
    DimensionalModel loaded = roundTrip(model, DimensionalModel.class, "dimensional-model");
    assertEquals(
        "dim-source",
        loaded.getCoachingOrDefault().getCoachingSourcesOrEmpty().getFirst().getRecordName());
  }

  @Test
  void coachingSourceRefDedupesByIdentity() {
    ModelCoachingConfiguration coaching = ModelCoachingConfiguration.createEmpty();
    CoachingSourceRef ref =
        CoachingSourceRef.forRecordDefinition("local-catalog", "hop/test/sources", "CRM-customer");
    coaching.addCoachingSource(ref);
    coaching.addCoachingSource(
        CoachingSourceRef.forRecordDefinition("local-catalog", "hop/test/sources", "CRM-customer"));
    assertEquals(1, coaching.getCoachingSourcesOrEmpty().size());
    assertTrue(coaching.removeCoachingSource(ref));
    assertTrue(coaching.getCoachingSourcesOrEmpty().isEmpty());
  }

  @Test
  void coachPanelAuditDefaultsHiddenForUnknownFile() {
    assertFalse(ModelCoachPanelAuditSupport.retrievePanelVisible("/tmp/unknown-model.hdv"));
  }

  @Test
  void coachPanelAuditStoresVisibility() {
    String file = "/tmp/coach-audit-test-" + System.nanoTime() + ".hdv";
    ModelCoachPanelAuditSupport.storePanelVisible(file, false);
    assertFalse(ModelCoachPanelAuditSupport.retrievePanelVisible(file));
    ModelCoachPanelAuditSupport.storePanelVisible(file, true);
    assertTrue(ModelCoachPanelAuditSupport.retrievePanelVisible(file));
  }

  @Test
  void refreshOnModelLoadDefaultsToFalseAndPersists() {
    String file = "/tmp/coach-panel-refresh-on-load-test.hdv";
    assertFalse(ModelCoachPanelAuditSupport.retrieveRefreshOnModelLoad(file));
    ModelCoachPanelAuditSupport.storeRefreshOnModelLoad(file, true);
    assertTrue(ModelCoachPanelAuditSupport.retrieveRefreshOnModelLoad(file));
    ModelCoachPanelAuditSupport.storeRefreshOnModelLoad(file, false);
    assertFalse(ModelCoachPanelAuditSupport.retrieveRefreshOnModelLoad(file));
  }

  private static <T> T roundTrip(T original, Class<T> type, String rootTag) throws Exception {
    String xml = XmlMetadataUtil.serializeObjectToXml(original);
    Document document =
        XmlHandler.loadXmlString(XmlHandler.openTag(rootTag) + xml + XmlHandler.closeTag(rootTag));
    Node rootNode = XmlHandler.getSubNode(document, rootTag);
    T loaded = type.getDeclaredConstructor().newInstance();
    XmlMetadataUtil.deSerializeFromXml(rootNode, type, loaded, null);
    return loaded;
  }
}
