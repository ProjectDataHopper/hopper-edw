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

class DimensionalPlantUmlDiagramExporterTest {

  @Test
  void exportsStarSchemaWithStereotypesAndConnectors() throws Exception {
    DimensionalPlantUmlDiagramExporter exporter = new DimensionalPlantUmlDiagramExporter();
    DimensionalModel model = DimensionalExportTestModels.starSchema();

    assertTrue(exporter.supportsSubject(model));
    assertEquals("PLANTUML", exporter.getFormat().getId());
    assertEquals("puml", exporter.getFileExtension());

    DiagramExportResult result =
        exporter.export(model, new DiagramExportOptions(null, "PLANTUML"), null);
    assertTrue(result.isSuccess());
    String puml = result.getContent();
    assertTrue(puml.contains("@startuml"));
    assertTrue(puml.contains("@enduml"));
    assertTrue(puml.contains("<<Fact>>"));
    assertTrue(puml.contains("<<Dimension>>"));
    assertTrue(puml.contains("<<Bridge>>"));
    assertTrue(puml.contains("customer_id"));
    assertTrue(puml.contains("<<PK>>"));
    assertTrue(puml.contains("<<FK>>"));
    assertTrue(puml.contains("fact_orders }|--|| dim_customer"));
    assertTrue(puml.contains("dim_customer }|--|| dim_geo"));
    assertTrue(puml.contains("br_customer_account }|--|| dim_customer"));
  }

  @Test
  void entitySyntaxAndGrainUseExtraOptions() throws Exception {
    DimensionalPlantUmlDiagramExporter exporter = new DimensionalPlantUmlDiagramExporter();
    DiagramExportOptions options = new DiagramExportOptions(null, "PLANTUML");
    options.getExtraOptions().put(EdwDiagramExportSupport.EXTRA_SYNTAX_STYLE, "ENTITY");
    options.getExtraOptions().put(EdwDiagramExportSupport.EXTRA_INCLUDE_GRAIN, "true");

    DiagramExportResult result =
        exporter.export(DimensionalExportTestModels.starSchema(), options, null);
    String puml = result.getContent();
    assertTrue(puml.contains("entity \"fact_orders\""));
    assertTrue(puml.contains("Grain: one row per order line"));
  }
}
