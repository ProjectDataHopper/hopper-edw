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
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** The scd2-calculations fixture hub is hub_customer_calc, not hub_customer. */
class BvScd2CalculationsFixtureTest {

  private static final Path BV_PATH =
      Path.of("integration-tests/tests/scd2-calculations/scd2-calculations.hbv")
          .toAbsolutePath()
          .normalize();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @AfterEach
  void clearModelCache() {
    DvModelLoadSupport.clearCache();
  }

  @Test
  void modelCheckResolvesHubCustomerCalc() throws Exception {
    Variables variables = new Variables();
    variables.setVariable(
        "PROJECT_HOME", ModelConfigurationTestSupport.INTEGRATION_TESTS.toString());
    variables.setVariable("OUTPUT_COPIES", "1");

    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    IHopMetadataProvider provider =
        ModelConfigurationTestSupport.prepare(
            metadataProvider, ModelConfigurationTestSupport.INTEGRATION_TESTS);

    BusinessVaultModel bvModel = loadBusinessVaultModel(provider);
    List<ICheckResult> remarks = bvModel.check(provider, variables);
    assertFalse(
        remarks.stream().anyMatch(remark -> remark.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () ->
            remarks.stream()
                .map(remark -> remark.getTypeDesc() + " " + remark.getText())
                .toList()
                .toString());

    for (IBvTable table : bvModel.getTables()) {
      if (table instanceof BvScd2Table scd2Table) {
        assertEquals("hub_customer_calc", scd2Table.getParentHubName(), scd2Table.getName());
      }
    }
    assertTrue(
        bvModel.getDvReferences().stream()
            .anyMatch(reference -> "hub_customer_calc".equals(reference.getDvTableName())));
    assertFalse(
        bvModel.getDvReferences().stream()
            .anyMatch(reference -> "hub_customer".equals(reference.getDvTableName())));

    DataVaultModel dvModel =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(bvModel, variables, provider);
    IBvTable hubTable = bvModel.findTable("sat_customer_calc_hub_bv");
    assertTrue(hubTable instanceof BvScd2Table);
    IRowMeta layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            (BvScd2Table) hubTable,
            bvModel.getConfigurationOrDefault(),
            dvModel,
            bvModel,
            variables);
    assertTrue(hasField(layout, "customer_id"));
    assertTrue(hasField(layout, "x_date"));
    assertTrue(hasField(layout, "some_description"));
    assertTrue(hasField(layout, "id_label"));
    assertFalse(hasField(layout, "deleted_flag"));
  }

  private static boolean hasField(IRowMeta layout, String fieldName) {
    return layout.getValueMetaList().stream().anyMatch(valueMeta -> fieldName.equals(valueMeta.getName()));
  }

  private static BusinessVaultModel loadBusinessVaultModel(IHopMetadataProvider provider)
      throws Exception {
    Document document = XmlHandler.loadXmlFile(BV_PATH.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    model.setFilename(BV_PATH.toString());
    return model;
  }
}
