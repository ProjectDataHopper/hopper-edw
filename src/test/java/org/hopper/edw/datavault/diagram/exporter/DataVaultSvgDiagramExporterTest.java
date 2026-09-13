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
package org.hopper.edw.datavault.diagram.exporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.diagram.DiagramExportResult;
import org.apache.hop.core.diagram.ExportContext;
import org.apache.hop.core.diagram.IExportContext;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataVaultSvgDiagramExporterTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void exportsDataVaultModelToSvg() throws Exception {
    DataVaultSvgDiagramExporter exporter = new DataVaultSvgDiagramExporter();
    DataVaultModel model = new DataVaultModel();
    model.setName("demo");
    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.setLocation(new Point(100, 100));
    model.getTables().add(hub);

    assertTrue(exporter.supportsSubject(model));
    assertEquals("SVG", exporter.getFormat().getId());
    assertEquals("svg", exporter.getFileExtension());

    DiagramExportOptions options = new DiagramExportOptions(null, "SVG");
    IExportContext context =
        new ExportContext(new Variables(), null, null, IExportContext.ExportEnvironment.TEST);
    DiagramExportResult result = exporter.export(model, options, context);

    assertTrue(result.isSuccess());
    assertNotNull(result.getContent());
    assertTrue(result.getContent().contains("<svg"));
    assertTrue(result.getContent().contains("hub_customer"));
    assertEquals("image/svg+xml", result.getMimeType());
    assertFalse(exporter.supportsSubject("not-a-model"));
  }
}
