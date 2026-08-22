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
package org.hopper.edw.datavault.command.svg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class SvgExportServiceTest {

  private static final Path DV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hdv")
          .toAbsolutePath()
          .normalize();
  private static final Path BV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hbv")
          .toAbsolutePath()
          .normalize();
  private static final Path PROJECT_HOME = Path.of("project").toAbsolutePath().normalize();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void dataVaultModelSvgContainsTableNames() throws Exception {
    Variables variables = variables();
    SvgRenderOptions options = SvgRenderOptions.defaults();

    String svg = SvgExportService.generateSvg(DV_PATH.toString(), options, variables, null);

    assertNotNull(svg);
    assertFalse(svg.isBlank());
    assertTrue(svg.contains("hub_customer"), () -> "SVG should contain hub_customer");
    assertTrue(svg.contains("<svg"), () -> "Output should be SVG XML");
  }

  @Test
  void businessVaultModelSvgContainsTableNames() throws Exception {
    Variables variables = variables();
    SvgRenderOptions options = SvgRenderOptions.defaults();

    String svg = SvgExportService.generateSvg(BV_PATH.toString(), options, variables, null);

    assertNotNull(svg);
    assertFalse(svg.isBlank());
    assertTrue(svg.contains("customer_360_bv"), () -> "SVG should contain customer_360_bv");
  }

  @Test
  void noNotesProducesSmallerBoundsForBusinessVaultModel() throws Exception {
    BusinessVaultModel model = loadBusinessVaultModel(BV_PATH);

    Point withNotes = ModelBoundsSupport.getMaximum(model, true);
    Point withoutNotes = ModelBoundsSupport.getMaximum(model, false);

    assertTrue(
        withoutNotes.x < withNotes.x || withoutNotes.y < withNotes.y,
        () ->
            "Excluding notes should reduce canvas bounds: with="
                + withNotes
                + " without="
                + withoutNotes);

    SvgRenderOptions noNotes = SvgRenderOptions.fromCli(true, 1.0f, false);
    String svg = SvgExportService.generateSvg(BV_PATH.toString(), noNotes, variables(), null);
    assertNotNull(svg);
    assertFalse(svg.isBlank());
  }

  private static Variables variables() {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", PROJECT_HOME.toString());
    return variables;
  }

  private static BusinessVaultModel loadBusinessVaultModel(Path path) throws Exception {
    Document document = org.apache.hop.core.xml.XmlHandler.loadXmlFile(path.toFile());
    Node rootNode =
        org.apache.hop.core.xml.XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, null);
    return model;
  }
}
