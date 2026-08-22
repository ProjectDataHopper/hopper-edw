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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.dimensional.publish.DvPublishModelSupport;
import org.hopper.edw.datavault.metadata.dimensional.publish.DvToDimensionalPublish;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Golden-file style checks for shipped dimensional fixtures and publish output. */
class DmGoldenFixtureTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void basicStarFixtureChecksClean() throws Exception {
    DimensionalModel model = loadFixture("integration-tests/tests/basic/basic-star.hdm");
    assertEquals(3, model.getTables().size());
    assertModelChecksWithoutErrors(model);
  }

  @Test
  void extendedCatalogFixtureChecksClean() throws Exception {
    DimensionalModel model = loadFixture("integration-tests/tests/basic/extended-catalog.hdm");
    assertEquals(10, model.getTables().size());
    assertEquals("Customer", model.getConformedDimensionsOrEmpty().get(0).getLogicalName());
    assertModelChecksWithoutErrors(model);
  }

  @Test
  void vault1PublishProducesStableDraftShape() throws Exception {
    DataVaultModel dvModel =
        DvPublishModelSupport.loadDataVaultModel(
            Path.of("integration-tests/tests/basic/vault1.hdv")
                .toAbsolutePath()
                .normalize()
                .toString(),
            new Variables(),
            null);
    DimensionalModel published = DvToDimensionalPublish.publish(dvModel).getDimensionalModel();

    assertEquals("vault1-draft", published.getName());
    assertEquals(5, published.getTables().size());
    assertEquals(1, published.getNotes().size());
    assertTrue(published.findTable("dim_customer") instanceof DmDimension);
    assertTrue(published.findTable("fact_order") instanceof DmFact);
    assertTrue(published.findTable("factless_customer_order") instanceof DmFactlessFact);
    assertFalse(published.getDescription().isBlank());
  }

  private static void assertModelChecksWithoutErrors(DimensionalModel model) throws Exception {
    var remarks = model.check(testMetadataProvider(), new Variables());
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_OK));
    assertFalse(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  private static DimensionalModel loadFixture(String relativePath) throws Exception {
    Path fixture = Path.of(relativePath).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    IHopMetadataProvider provider = testMetadataProvider();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, provider);
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
