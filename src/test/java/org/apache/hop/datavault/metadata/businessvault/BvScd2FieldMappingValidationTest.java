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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvScd2FieldMappingValidationTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void multiSatelliteRequiresExplicitMappings() throws Exception {
    BvScd2Table table = multiSatTable();
    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertTrue(hasError(remarks, "requires explicit field mappings"));
    assertFalse(hasOkOnlyMappingRemarks(remarks));
  }

  @Test
  void rejectsDuplicateTargetFieldNames() throws Exception {
    BvScd2Table table = multiSatTable();
    table.getFieldMappings().add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_demo", "demo_score", "customer_name"));

    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertTrue(hasError(remarks, "target column 'customer_name'"));
  }

  @Test
  void rejectsUnknownSourceField() throws Exception {
    BvScd2Table table = multiSatTable();
    table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer", "missing_attr", "customer_name"));
    table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_demo", "demo_score", "demo_score"));

    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertTrue(hasError(remarks, "unknown source field 'missing_attr'"));
  }

  @Test
  void rejectsSatelliteWithoutMappings() throws Exception {
    BvScd2Table table = multiSatTable();
    table.getFieldMappings().add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));

    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertTrue(hasError(remarks, "does not map any attributes from satellite 'sat_customer_demo'"));
  }

  @Test
  void validMultiSatelliteMappingsPassValidation() throws Exception {
    BvScd2Table table = multiSatTable();
    table.getFieldMappings().add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_demo", "demo_score", "demo_score"));

    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertFalse(hasError(remarks, "requires explicit field mappings"));
    assertFalse(hasError(remarks, "target column"));
    assertFalse(hasError(remarks, "unknown source field"));
    assertFalse(hasError(remarks, "does not map any attributes"));
    assertFalse(hasError(remarks, "functional timestamp column"));
  }

  @Test
  void targetLayoutUsesMappedColumnsForMultiSatelliteTable() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    BvScd2Table table = multiSatTable();
    table.setFunctionalTimestampField("x_load_ts");
    table.getFieldMappings().add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    table
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_demo", "demo_score", "demo_score"));

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            table, new BusinessVaultConfiguration(), dvModel, new Variables());

    assertEquals("customer_hk", layout.getValueMeta(0).getName());
    assertEquals("customer_name", layout.getValueMeta(1).getName());
    assertEquals("demo_score", layout.getValueMeta(2).getName());
    assertEquals("x_record_source", layout.getValueMeta(layout.size() - 4).getName());
    assertEquals("x_load_ts", layout.getValueMeta(layout.size() - 3).getName());
    assertEquals("valid_from", layout.getValueMeta(layout.size() - 2).getName());
    assertEquals("valid_to", layout.getValueMeta(layout.size() - 1).getName());
    assertEquals(7, layout.size());
  }

  @Test
  void xmlRoundTripPreservesFieldMappings() throws Exception {
    BvScd2Table original = multiSatTable();
    original.setName("customer_bv");
    original.setTableName("customer_bv");
    original
        .getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer", "name", "customer_name"));
    original.getSatelliteConfigs().add(new BvScd2SatelliteConfig("sat_customer_demo"));
    original.getSatelliteConfigs().get(0).setSourceIndicatorValue("DEMO");

    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");

    BvScd2Table restored = new BvScd2Table();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvScd2Table.class, restored, null);

    assertEquals(1, restored.getFieldMappings().size());
    assertEquals("sat_customer", restored.getFieldMappings().get(0).getSatelliteName());
    assertEquals("name", restored.getFieldMappings().get(0).getSourceFieldName());
    assertEquals("customer_name", restored.getFieldMappings().get(0).getTargetFieldName());
    assertEquals(1, restored.getSatelliteConfigs().size());
    assertEquals("sat_customer_demo", restored.getSatelliteConfigs().get(0).getSatelliteName());
    assertEquals("DEMO", restored.getSatelliteConfigs().get(0).getSourceIndicatorValue());
  }

  private static BvScd2Table multiSatTable() throws Exception {
    BvScd2Table table = new BvScd2Table();
    table.setName("customer_bv");
    table.setTableName("customer_bv");
    table.setFunctionalTimestampField("x_load_ts");
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer_demo", DvTableType.SATELLITE));
    return table;
  }

  private static List<ICheckResult> check(BvScd2Table table, DataVaultModel dvModel) {
    List<ICheckResult> remarks = new ArrayList<>();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    table.check(remarks, null, new Variables(), bvModel, dvModel);
    return remarks;
  }

  private static boolean hasError(List<ICheckResult> remarks, String fragment) {
    return remarks.stream()
        .anyMatch(
            remark ->
                remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                    && remark.getText() != null
                    && remark.getText().contains(fragment));
  }

  private static boolean hasOkOnlyMappingRemarks(List<ICheckResult> remarks) {
    return remarks.stream()
        .anyMatch(
            remark ->
                remark.getType() == ICheckResult.TYPE_RESULT_OK && remark instanceof CheckResult);
  }

  private static DataVaultModel loadVault1Model() throws Exception {
    DataVaultModel model = loadVault1ModelFromFile();
    DvSatellite demoSatellite = new DvSatellite();
    demoSatellite.setName("sat_customer_demo");
    demoSatellite.setTableName("sat_customer_demo");
    demoSatellite.setHubName("hub_customer");
    SatelliteAttribute demoScore = new SatelliteAttribute();
    demoScore.setName("demo_score");
    demoScore.setDataType("Integer");
    demoScore.setLength("9");
    demoSatellite.getAttributes().add(demoScore);
    model.getTables().add(demoSatellite);
    return model;
  }

  private static DataVaultModel loadVault1ModelFromFile() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(dvPath.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
