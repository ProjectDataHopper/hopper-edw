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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.datavault.metadata.ModelConfigurationTestSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvPitVault1FixtureTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void vault1HbvContainsPitCustomerWithFixedSnapshotRange() throws Exception {
    BusinessVaultModel bvModel = loadVault1BusinessVaultModel();

    BvPitTable pitTable = findPitTable(bvModel);
    assertEquals("pit_customer", pitTable.getTableName());
    assertEquals(
        BvPitRangeStart.FIXED_DATE, pitTable.getSnapshotScheduleOrDefault().getRangeStart());
    assertEquals("2024-01-31", pitTable.getSnapshotScheduleOrDefault().getRangeStartFixed());
    assertEquals(BvPitRangeEnd.FIXED_DATE, pitTable.getSnapshotScheduleOrDefault().getRangeEnd());
    assertEquals("2024-02-01", pitTable.getSnapshotScheduleOrDefault().getRangeEndFixed());
    assertTrue(
        pitTable.getDerivatives().stream()
            .anyMatch(
                ref ->
                    ref != null
                        && DvTableType.HUB == ref.getDvTableType()
                        && "hub_customer".equals(ref.getDvTableName())));
    assertTrue(
        pitTable.getDerivatives().stream()
            .anyMatch(
                ref ->
                    ref != null
                        && DvTableType.SATELLITE == ref.getDvTableType()
                        && "sat_customer".equals(ref.getDvTableName())));
  }

  @Test
  void vault1PitCustomerGeneratesBuildPipeline() throws Exception {
    BusinessVaultModel bvModel = loadVault1BusinessVaultModel();
    DataVaultModel dvModel = loadVault1DataVaultModel();
    BvPitTable pitTable = findPitTable(bvModel);
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        pitTable.generateBuildPipelines(metadataProvider, new Variables(), bvModel, dvModel);

    assertEquals(1, pipelines.size());
    assertEquals("bv-pit-pit_customer", pipelines.get(0).getName());
    assertTrue(
        BvPitPipelineSupport.buildPitTableInputSql(
                BvPitPipelineSupport.createContext(
                    metadataProvider, new Variables(), bvModel, dvModel, pitTable))
            .contains("DATE '2024-01-31'"));
  }

  private static BvPitTable findPitTable(BusinessVaultModel bvModel) {
    for (IBvTable table : bvModel.getTables()) {
      if (table instanceof BvPitTable pitTable) {
        return pitTable;
      }
    }
    throw new AssertionError("pit_customer table not found in vault1.hbv");
  }

  private static BusinessVaultModel loadVault1BusinessVaultModel() throws Exception {
    Path path = Path.of("integration-tests/tests/basic/vault1.hbv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(path.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    IHopMetadataProvider provider = testMetadataProvider();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    return model;
  }

  private static DataVaultModel loadVault1DataVaultModel() throws Exception {
    Path path = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(path.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    IHopMetadataProvider provider = testMetadataProvider();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    return model;
  }

  private static IHopMetadataProvider testMetadataProvider() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    metadataProvider.getSerializer(DatabaseMeta.class).save(new TestDatabaseMeta("Vault"));
    return ModelConfigurationTestSupport.prepare(
        metadataProvider, ModelConfigurationTestSupport.INTEGRATION_TESTS);
  }
}
