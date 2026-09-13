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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.diagram.DiagramExportResult;
import org.apache.hop.core.diagram.ExportContext;
import org.apache.hop.core.diagram.IExportContext;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DimensionalDrawioDiagramExporterTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void exportsMxfileWithVerticesAndEdges() throws Exception {
    DimensionalDrawioDiagramExporter exporter = new DimensionalDrawioDiagramExporter();
    DimensionalModel model = DimensionalExportTestModels.starSchema();

    assertTrue(exporter.supportsSubject(model));
    assertEquals("DRAWIO", exporter.getFormat().getId());
    assertEquals("drawio", exporter.getFileExtension());

    IExportContext context =
        new ExportContext(new Variables(), null, null, IExportContext.ExportEnvironment.TEST);
    DiagramExportResult result =
        exporter.export(model, new DiagramExportOptions(null, "DRAWIO"), context);
    assertTrue(result.isSuccess());
    String xml = result.getContent();
    assertTrue(xml.contains("<mxfile"));
    assertTrue(xml.contains("<mxCell"));
    assertTrue(xml.contains("vertex=\"1\""));
    assertTrue(xml.contains("edge=\"1\""));
    assertTrue(xml.contains("<mxGeometry"));
    assertTrue(xml.contains("fact_orders"));
    assertTrue(xml.contains("dim_customer"));
  }
}
