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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BusinessVaultDvModelResolverTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void clearCache() {
    DvModelLoadSupport.clearCache();
  }

  @AfterEach
  void tearDown() {
    DvModelLoadSupport.clearCache();
  }

  @Test
  void buildEffectiveModelFromCanvasAliasWithoutLinkedPath() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    BusinessVaultModel bv = new BusinessVaultModel();
    bv.setFilename(Path.of("integration-tests/tests/basic/vault1.hbv").toAbsolutePath().toString());
    // No dataVaultModelPath — multi-model style.
    BvDvTableReference hubRef = new BvDvTableReference("hub_customer", DvTableType.HUB);
    hubRef.setReferencedModelFilename(dvPath.toString());
    bv.getDvReferences().add(hubRef);

    DataVaultModel effective =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bv, new Variables(), null);
    assertNotNull(effective);
    assertNotNull(effective.findTable("hub_customer"));
    assertTrue(effective.getTables().size() >= 1);
  }

  @Test
  void resolveDvTableUsesAliasPath() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    BusinessVaultModel bv = new BusinessVaultModel();
    bv.setFilename(Path.of("integration-tests/tests/basic/vault1.hbv").toAbsolutePath().toString());
    BvDvTableReference satRef = new BvDvTableReference("sat_customer", DvTableType.SATELLITE);
    satRef.setReferencedModelFilename(dvPath.toString());
    bv.getDvReferences().add(satRef);

    IDvTable table =
        BusinessVaultDvModelResolver.resolveDvTable(bv, "sat_customer", new Variables(), null);
    assertNotNull(table);
    assertEquals("sat_customer", table.getName());
  }

  @Test
  void linkedSatellitePickerSeesSavedHdvEditsAfterCacheInvalidate() throws Exception {
    Path fixture = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Path tempModel = tempDir.resolve("vault1.hdv");
    Files.copy(fixture, tempModel);

    BusinessVaultModel bv = new BusinessVaultModel();
    bv.setFilename(tempDir.resolve("vault1.hbv").toString());
    BvDvTableReference satRef = new BvDvTableReference("sat_product", DvTableType.SATELLITE);
    satRef.setReferencedModelFilename(tempModel.toString());
    bv.getDvReferences().add(satRef);

    Variables variables = new Variables();
    DataVaultModel first =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bv, variables, null);
    List<String> firstChoices =
        BusinessVaultDvReferenceSupport.listAvailableDvTableNames(
            first, bv, DvTableType.SATELLITE, tempModel.toString());
    assertTrue(firstChoices.contains("sat_customer"));
    assertFalse(firstChoices.contains("sat_customer_new"));

    String xml = Files.readString(tempModel);
    Files.writeString(
        tempModel, xml.replace("<name>sat_customer</name>", "<name>sat_customer_new</name>"));

    DataVaultModel stale =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bv, variables, null);
    List<String> staleChoices =
        BusinessVaultDvReferenceSupport.listAvailableDvTableNames(
            stale, bv, DvTableType.SATELLITE, tempModel.toString());
    assertTrue(staleChoices.contains("sat_customer"));
    assertFalse(staleChoices.contains("sat_customer_new"));

    BusinessVaultDvModelResolver.invalidateReferencedModelCaches(bv, variables);
    DataVaultModel fresh =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bv, variables, null);
    List<String> freshChoices =
        BusinessVaultDvReferenceSupport.listAvailableDvTableNames(
            fresh, bv, DvTableType.SATELLITE, tempModel.toString());
    assertFalse(freshChoices.contains("sat_customer"));
    assertTrue(freshChoices.contains("sat_customer_new"));
  }

  @Test
  void resolveDvTableReturnsNullWithoutAliasOrPath() throws Exception {
    BusinessVaultModel bv = new BusinessVaultModel();
    assertNull(
        BusinessVaultDvModelResolver.resolveDvTable(bv, "hub_customer", new Variables(), null));
  }

  @Test
  void emptyModelWhenNoRefsAndNoPath() throws Exception {
    BusinessVaultModel bv = new BusinessVaultModel();
    DataVaultModel effective =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bv, new Variables(), null);
    assertNotNull(effective);
    assertTrue(effective.getTables().isEmpty());
  }

  @Test
  void copyDvConfigurationKeepsNamedLoadDateWhenInlineConfigIsAbsent() throws Exception {
    IHopMetadataProvider metadataProvider = retailMetadataProvider();
    DataVaultModel source = new DataVaultModel();
    source.setConfigurationName("data-vault");
    source.setConfiguration(null);
    ModelConfigurationResolver.attach(source, metadataProvider);

    DataVaultModel target = new DataVaultModel();
    BusinessVaultDvModelResolver.copyDvConfiguration(source, target, metadataProvider);

    assertEquals("data-vault", target.getConfigurationName());
    assertEquals("x_load_ts", target.getConfigurationOrDefault().getLoadDateField());
    assertEquals("x_load_ts", target.getConfiguration().getLoadDateField());
  }

  @Test
  void effectiveRetail360ModelKeepsNamedLoadDateField() throws Exception {
    IHopMetadataProvider metadataProvider = retailMetadataProvider();
    Variables variables = retailVariables();
    BusinessVaultModel bv = loadRetailBusinessVault(metadataProvider);

    DataVaultModel effective =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bv, variables, metadataProvider);

    assertEquals("data-vault", effective.getConfigurationName());
    assertEquals("x_load_ts", effective.getConfigurationOrDefault().getLoadDateField());
    assertNotNull(effective.findTable("sat_customer_demo"));
  }

  @Test
  void retail360Scd2CheckResolvesSatelliteLoadTimestamp() throws Exception {
    IHopMetadataProvider metadataProvider = retailMetadataProvider();
    Variables variables = retailVariables();
    BusinessVaultModel bv = loadRetailBusinessVault(metadataProvider);

    List<ICheckResult> remarks = bv.check(metadataProvider, variables);

    assertFalse(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && remark.getText().contains("cannot resolve functional timestamp")),
        () -> remarks.stream().map(ICheckResult::getText).toList().toString());
  }

  private static Variables retailVariables() {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", Path.of("retail-example").toAbsolutePath().toString().replace('\\', '/'));
    return variables;
  }

  private static IHopMetadataProvider retailMetadataProvider() throws Exception {
    return ModelConfigurationTestSupport.prepare(
        new MemoryMetadataProvider(), ModelConfigurationTestSupport.RETAIL_EXAMPLE);
  }

  private static BusinessVaultModel loadRetailBusinessVault(IHopMetadataProvider metadataProvider)
      throws Exception {
    Path hbv = Path.of("retail-example/models/retail-360.hbv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(hbv.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, metadataProvider);
    ModelConfigurationResolver.attach(model, metadataProvider);
    model.setFilename(hbv.toString());
    return model;
  }
}
