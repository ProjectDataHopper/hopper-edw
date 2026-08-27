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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2PipelineSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DvIntegrationSupportTest {

  private static final Path DV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hdv")
          .toAbsolutePath()
          .normalize();
  private static final Path BV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hbv")
          .toAbsolutePath()
          .normalize();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesIntegrationModes() {
    DvHub hub = new DvHub("hub_customer");
    assertTrue(DvIntegrationSupport.isHopManaged(hub));

    hub.setIntegrationMode(DvIntegrationMode.EXTERNAL_READ);
    assertTrue(DvIntegrationSupport.isExternalRead(hub));
    assertTrue(DvIntegrationSupport.shouldSkipDdl(hub));
    assertTrue(DvIntegrationSupport.relaxesSourceValidation(hub));

    hub.setIntegrationMode(DvIntegrationMode.CUSTOM_PIPELINES);
    assertTrue(DvIntegrationSupport.isCustomPipelines(hub));
    assertEquals("custom", DvIntegrationSupport.integrationCanvasSuffix(hub));

    DataVaultConfiguration configuration = new DataVaultConfiguration();
    configuration.setReadOnlyExistingVault(true);
    DataVaultModel model = new DataVaultModel();
    model.setConfiguration(configuration);
    DvHub managed = new DvHub("hub_party");
    assertEquals("doc", DvIntegrationSupport.integrationCanvasSuffix(managed, model));
    assertTrue(DvIntegrationSupport.relaxesSourceValidation(managed, model));
  }

  @Test
  void externalSatelliteSkipsDdlAndUpdatePipelines() throws Exception {
    DataVaultModel model = loadDataVaultModel(DV_PATH);
    DvSatellite satellite = (DvSatellite) model.findTable("sat_customer_demo");
    satellite.setIntegrationMode(DvIntegrationMode.EXTERNAL_READ);

    IHopMetadataProvider metadataProvider = testMetadataProvider();
    Variables variables = new Variables();

    assertTrue(satellite.generateUpdateDdl(metadataProvider, variables, model).isEmpty());

    List<PipelineMeta> pipelines =
        satellite.generateUpdatePipelines(metadataProvider, variables, model, new Date(), null);
    assertTrue(pipelines.isEmpty());

    List<ICheckResult> remarks = new java.util.ArrayList<>();
    satellite.check(remarks, metadataProvider, variables, DvModelCheckOptions.defaults(), model);
    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> remarks.stream().map(ICheckResult::getText).toList().toString());
    assertTrue(
        remarks.stream().anyMatch(r -> r.getText().contains("External table layout defines")));
  }

  @Test
  void businessVaultScd2StillBuildsWhenDvSatelliteIsExternal() throws Exception {
    DataVaultModel dvModel = loadDataVaultModel(DV_PATH);
    DvSatellite satellite = (DvSatellite) dvModel.findTable("sat_customer_demo");
    satellite.setIntegrationMode(DvIntegrationMode.EXTERNAL_READ);

    BusinessVaultModel bvModel = loadBusinessVaultModel(BV_PATH);
    BvScd2Table scd2Table =
        bvModel.getTables().stream()
            .filter(BvScd2Table.class::isInstance)
            .map(BvScd2Table.class::cast)
            .filter(table -> "customer_360_bv".equals(table.getName()))
            .findFirst()
            .orElseThrow();

    Variables variables = new Variables();
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        scd2Table.generateBuildPipelines(metadataProvider, variables, bvModel, dvModel);
    assertEquals(1, pipelines.size());

    long tableInputs =
        pipelines.get(0).getTransforms().stream()
            .filter(t -> t.getTransform() instanceof TableInputMeta)
            .count();
    assertEquals(4, tableInputs);

    var layout =
        BvScd2PipelineSupport.buildTargetTableLayout(
            scd2Table, bvModel.getConfigurationOrDefault(), dvModel, variables);
    assertEquals(16, layout.size());
  }

  private static DataVaultModel loadDataVaultModel(Path path) throws Exception {
    Document document = XmlHandler.loadXmlFile(path.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    IHopMetadataProvider provider =
        ModelConfigurationTestSupport.prepare(
            null, ModelConfigurationTestSupport.INTEGRATION_TESTS);
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    return model;
  }

  private static BusinessVaultModel loadBusinessVaultModel(Path path) throws Exception {
    Document document = XmlHandler.loadXmlFile(path.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    IHopMetadataProvider provider =
        ModelConfigurationTestSupport.prepare(
            null, ModelConfigurationTestSupport.INTEGRATION_TESTS);
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, provider);
    ModelConfigurationResolver.attach(model, provider);
    model.setDataVaultModelPath(DV_PATH.toString());
    return model;
  }

  private static IHopMetadataProvider testMetadataProvider() throws Exception {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return ModelConfigurationTestSupport.prepare(
        metadataProvider, ModelConfigurationTestSupport.INTEGRATION_TESTS);
  }
}
