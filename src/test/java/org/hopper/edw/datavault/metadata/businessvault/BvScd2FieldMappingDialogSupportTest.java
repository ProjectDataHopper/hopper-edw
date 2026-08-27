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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2FieldMappingDialogSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void listsSatelliteDerivativesAndAttributes() throws Exception {
    BvScd2Table table = customer360Table();
    DataVaultModel dvModel = loadCustomer360DvModel();

    assertEquals(
        List.of(
            "sat_customer_demo",
            "sat_customer_contact",
            "sat_customer_address",
            "sat_customer_prefs"),
        BvScd2FieldMappingDialogSupport.satelliteDerivativeNames(table, dvModel));
    assertEquals(
        List.of("segment", "loyalty_tier", "demo_score"),
        BvScd2FieldMappingDialogSupport.satelliteAttributeNames("sat_customer_demo", dvModel));
  }

  @Test
  void suggestMappingsUsesPrefixedTargetsOnCollision() throws Exception {
    BvScd2Table table = customer360Table();
    DataVaultModel dvModel = loadCustomer360DvModel();

    List<BvScd2FieldMapping> suggestions =
        BvScd2FieldMappingDialogSupport.suggestMappings(table, dvModel);

    assertFalse(suggestions.isEmpty());
    Set<String> targets = new HashSet<>();
    for (BvScd2FieldMapping mapping : suggestions) {
      assertTrue(targets.add(mapping.getTargetFieldName()));
    }
    assertTrue(
        suggestions.stream()
            .anyMatch(
                mapping ->
                    "sat_customer_demo".equals(mapping.getSatelliteName())
                        && "segment".equals(mapping.getSourceFieldName())));
  }

  @Test
  void pruneMappingsAndConfigsRemovesOrphans() {
    BvScd2Table table = new BvScd2Table();
    table.getFieldMappings().add(new BvScd2FieldMapping("sat_a", "f1", "t1"));
    table.getFieldMappings().add(new BvScd2FieldMapping("sat_b", "f2", "t2"));
    table.getSatelliteConfigs().add(new BvScd2SatelliteConfig("sat_b"));
    table.getSatelliteConfigs().get(0).setSourceIndicatorValue("B");

    BvScd2FieldMappingDialogSupport.pruneMappingsAndConfigs(table, Set.of("sat_a"));

    assertEquals(1, table.getFieldMappings().size());
    assertEquals("sat_a", table.getFieldMappings().get(0).getSatelliteName());
    assertTrue(table.getSatelliteConfigs().isEmpty());
  }

  @Test
  void syncSatelliteConfigsPreservesExistingValues() {
    BvScd2Table table = new BvScd2Table();
    BvScd2SatelliteConfig existing = new BvScd2SatelliteConfig("sat_customer_demo");
    existing.setSourceIndicatorValue("DEMO");
    table.getSatelliteConfigs().add(existing);

    List<BvScd2SatelliteConfig> synced =
        BvScd2FieldMappingDialogSupport.syncSatelliteConfigs(
            table, List.of("sat_customer_demo", "sat_customer_contact"));

    assertEquals(2, synced.size());
    assertEquals("DEMO", synced.get(0).getSourceIndicatorValue());
    assertEquals("sat_customer_contact", synced.get(1).getSatelliteName());
    assertTrue(
        synced.get(1).getSourceIndicatorValue() == null
            || synced.get(1).getSourceIndicatorValue().isEmpty());
  }

  @Test
  void validateForDialogReportsMultiSatelliteMappingErrors() throws Exception {
    BvScd2Table table = customer360Table();
    DataVaultModel dvModel = loadCustomer360DvModel();

    List<org.apache.hop.core.ICheckResult> remarks =
        BvScd2FieldMappingDialogSupport.validateForDialog(
            table, new BusinessVaultModel(), dvModel, new Variables());

    assertTrue(BvScd2FieldMappingDialogSupport.hasValidationErrors(remarks));
    assertTrue(
        BvScd2FieldMappingDialogSupport.formatValidationErrors(remarks).contains("field mappings"));
  }

  private static BvScd2Table customer360Table() {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_360_bv");
    table.setTableName("customer_360_bv");
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_demo", DvTableType.SATELLITE));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_contact", DvTableType.SATELLITE));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_address", DvTableType.SATELLITE));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_prefs", DvTableType.SATELLITE));
    return table;
  }

  private static DataVaultModel loadCustomer360DvModel() throws Exception {
    Path dvPath =
        Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hdv")
            .toAbsolutePath()
            .normalize();
    Document document = XmlHandler.loadXmlFile(dvPath.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
