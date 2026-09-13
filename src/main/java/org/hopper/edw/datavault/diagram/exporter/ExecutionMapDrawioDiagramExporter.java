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

import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.diagram.DiagramExportResult;
import org.apache.hop.core.diagram.DiagramExporter;
import org.apache.hop.core.diagram.IExportContext;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.architecture.ArchitectureGraph;
import org.hopper.edw.datavault.architecture.ArchitectureGraphFromExecutionMap;
import org.hopper.edw.datavault.architecture.DrawioArchitectureExporter;
import org.hopper.edw.datavault.diagram.schema.SchemaRenderOptions;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;

@DiagramExporter(
    id = "execution-map-drawio",
    name = "Execution map Draw.io Exporter",
    description = "Exports an execution map as a Draw.io / diagrams.net diagram",
    format = "DRAWIO",
    fileExtension = "drawio",
    fileFilterNames = {"Draw.io Files (*.drawio)"},
    supportedSubjectTypes = {ExecutionMapDocument.class})
public class ExecutionMapDrawioDiagramExporter
    extends EdwTextDiagramExporter<ExecutionMapDocument> {

  @Override
  public DiagramExportResult export(
      ExecutionMapDocument document, DiagramExportOptions options, IExportContext context)
      throws HopException {
    if (document == null) {
      throw new HopException("Execution map document is null");
    }
    ArchitectureGraph graph = ArchitectureGraphFromExecutionMap.build(document);
    SchemaRenderOptions render = SchemaRenderOptions.from(options);
    if (render.getLayoutMode() == SchemaRenderOptions.LayoutMode.ELK) {
      graph.setFreeformLayout(true);
    }
    String content = DrawioArchitectureExporter.export(graph);
    return finishText(content, options, context, "application/xml");
  }
}
