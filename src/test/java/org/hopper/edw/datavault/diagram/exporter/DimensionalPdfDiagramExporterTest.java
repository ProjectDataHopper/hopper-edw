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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.diagram.DiagramExportResult;
import org.apache.hop.core.diagram.ExportContext;
import org.apache.hop.core.diagram.IExportContext;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DimensionalPdfDiagramExporterTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void exportsPdfWithHeader() throws Exception {
    DimensionalPdfDiagramExporter exporter = new DimensionalPdfDiagramExporter();
    DimensionalModel model = DimensionalExportTestModels.starSchema();

    assertTrue(exporter.supportsSubject(model));
    assertEquals("PDF", exporter.getFormat().getId());
    assertEquals("pdf", exporter.getFileExtension());

    IExportContext context =
        new ExportContext(new Variables(), null, null, IExportContext.ExportEnvironment.TEST);
    DiagramExportResult result =
        exporter.export(model, new DiagramExportOptions(null, "PDF"), context);
    assertTrue(result.isSuccess());
    byte[] pdf = result.getBytes();
    assertNotNull(pdf);
    assertTrue(pdf.length > 8);
    String header = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
    assertEquals("%PDF-", header);
  }
}
