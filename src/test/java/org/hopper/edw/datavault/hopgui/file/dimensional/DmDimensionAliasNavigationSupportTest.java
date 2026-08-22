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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.dimensional.DmDimensionAliasNavigationSupport.DimensionPipelineSource;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmModelLoadSupport;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmDimensionAliasNavigationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void clearModelCache() {
    DmModelLoadSupport.clearCache();
  }

  @Test
  void inventoryProductAliasResolvesToConformedDimension() throws Exception {
    Path retailHome = Path.of("retail-example").toAbsolutePath().normalize();
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", retailHome.toString().replace('\\', '/'));

    Path inventoryPath = retailHome.resolve("models/retail-f-inventory.hdm");
    DimensionalModel inventory = loadModel(inventoryPath);
    DmDimensionAlias alias =
        assertInstanceOf(DmDimensionAlias.class, inventory.findTable("d_product"));

    DimensionPipelineSource source =
        DmDimensionAliasNavigationSupport.resolvePipelineSource(inventory, alias, variables, null);

    assertFalse(source.sameModel());
    assertEquals("d_product", source.dimensionName());
    assertTrue(source.modelPath().replace('\\', '/').endsWith("/models/retail-conformed-dims.hdm"));

    DimensionalModel conformed = loadModel(retailHome.resolve("models/retail-conformed-dims.hdm"));
    assertInstanceOf(DmDimension.class, conformed.findTable("d_product"));
    assertTrue(alias.generateUpdatePipelines(null, variables, inventory, null).isEmpty());
  }

  private static DimensionalModel loadModel(Path path) throws Exception {
    Document document = XmlHandler.loadXmlFile(path.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    model.setFilename(path.toString().replace('\\', '/'));
    return model;
  }
}
