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

import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.diagram.DiagramExportResult;
import org.hopper.edw.datavault.diagram.EdwDiagramExportSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.junit.jupiter.api.Test;

class DimensionalMermaidDiagramExporterTest {

  @Test
  void exportsErDiagramWithPkFkAndRelationships() throws Exception {
    DimensionalMermaidDiagramExporter exporter = new DimensionalMermaidDiagramExporter();
    DimensionalModel model = DimensionalExportTestModels.starSchema();

    assertTrue(exporter.supportsSubject(model));
    assertEquals("MERMAID", exporter.getFormat().getId());
    assertEquals("mmd", exporter.getFileExtension());

    DiagramExportResult result =
        exporter.export(model, new DiagramExportOptions(null, "MERMAID"), null);
    assertTrue(result.isSuccess());
    String mmd = result.getContent();
    assertTrue(mmd.startsWith("erDiagram"));
    assertTrue(mmd.contains("dim_customer"));
    assertTrue(mmd.contains("fact_orders"));
    assertTrue(mmd.contains("PK"));
    assertTrue(mmd.contains("FK"));
    assertTrue(mmd.contains("dim_customer ||--|{ fact_orders"));
    assertTrue(mmd.contains("customer_id"));
  }

  @Test
  void markdownFenceAndClassDiagramUseExtraOptions() throws Exception {
    DimensionalMermaidDiagramExporter exporter = new DimensionalMermaidDiagramExporter();
    DiagramExportOptions options = new DiagramExportOptions(null, "MERMAID");
    options.getExtraOptions().put(EdwDiagramExportSupport.EXTRA_MARKDOWN_FENCE, "true");
    options.getExtraOptions().put(EdwDiagramExportSupport.EXTRA_DIAGRAM_TYPE, "CLASS");

    DiagramExportResult result =
        exporter.export(DimensionalExportTestModels.starSchema(), options, null);
    String mmd = result.getContent();
    assertTrue(mmd.startsWith("```mermaid"));
    assertTrue(mmd.contains("classDiagram"));
    assertTrue(mmd.contains("```"));
  }
}
