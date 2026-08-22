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
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dimensionlookup.DimensionLookupMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmCrossModelAliasPipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void clearModelCache() {
    DmModelLoadSupport.clearCache();
  }

  @Test
  void factPipelineResolvesExternalAliasesToSharedPhysicalTables() throws Exception {
    DimensionalModel model = loadCrossModelAliasFixture();
    model.setFilename(
        Path.of("integration-tests/tests/basic/cross-model-alias.hdm")
            .toAbsolutePath()
            .normalize()
            .toString());
    DmFact fact = (DmFact) model.findTable("fact_orders");
    IHopMetadataProvider metadataProvider = testMetadataProvider();

    List<PipelineMeta> pipelines =
        fact.generateUpdatePipelines(metadataProvider, new Variables(), model, new Date());

    assertEquals(1, pipelines.size());
    PipelineMeta pipelineMeta = pipelines.get(0);

    TransformMeta customerLookup =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "lookup_dim_customer".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta customerLookupMeta = (DimensionLookupMeta) customerLookup.getTransform();
    assertEquals("d_customer", customerLookupMeta.getTableName());
    assertEquals("customer_key", customerLookupMeta.getFields().getReturns().getKeyRename());

    TransformMeta productLookup =
        pipelineMeta.getTransforms().stream()
            .filter(t -> "lookup_dim_product".equals(t.getName()))
            .findFirst()
            .orElseThrow();
    DimensionLookupMeta productLookupMeta = (DimensionLookupMeta) productLookup.getTransform();
    assertEquals("d_product", productLookupMeta.getTableName());

    assertTrue(
        pipelineMeta.getTransforms().stream()
            .anyMatch(t -> "date_keys_format".equals(t.getName())));
    assertTrue(
        pipelineMeta.getTransforms().stream().anyMatch(t -> "date_keys_int".equals(t.getName())));
    assertEquals(
        2,
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof SelectValuesMeta)
            .count());
    assertEquals(
        2,
        pipelineMeta.getTransforms().stream()
            .filter(t -> t.getTransform() instanceof DimensionLookupMeta)
            .count());
  }

  private static IHopMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    metadataProvider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return metadataProvider;
  }

  private static DimensionalModel loadCrossModelAliasFixture() throws Exception {
    Path fixture =
        Path.of("integration-tests/tests/basic/cross-model-alias.hdm").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    return model;
  }
}
