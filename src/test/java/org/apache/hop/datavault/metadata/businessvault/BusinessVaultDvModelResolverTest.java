/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.datavault.metadata.ModelConfigurationTestSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BusinessVaultDvModelResolverTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
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
