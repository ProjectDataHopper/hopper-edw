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
import org.apache.hop.core.diagram.DiagramExporter;
import org.apache.hop.core.diagram.IExportContext;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.command.svg.SvgRenderOptions;
import org.hopper.edw.datavault.diagram.EdwDiagramExportSupport;
import org.hopper.edw.datavault.hopgui.file.executionmap.ExecutionMapSvgPainter;
import org.hopper.edw.datavault.hopgui.file.executionmap.HopGuiExecutionMapGraph;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;

@DiagramExporter(
    id = "execution-map-pdf",
    name = "Execution map PDF Exporter",
    description = "Exports an execution map diagram to a vector PDF",
    format = "PDF",
    fileExtension = "pdf",
    fileFilterNames = {"PDF Files (*.pdf)"},
    supportedSubjectTypes = {ExecutionMapDocument.class})
public class ExecutionMapPdfDiagramExporter extends EdwPdfDiagramExporter<ExecutionMapDocument> {

  @Override
  protected String generateSvg(
      ExecutionMapDocument document, DiagramExportOptions options, IExportContext context)
      throws HopException {
    IVariables variables = context != null ? context.getVariables() : null;
    SvgRenderOptions render = EdwDiagramExportSupport.toSvgRenderOptions(options);
    if (context != null && context.getEnvironment() == IExportContext.ExportEnvironment.GUI) {
      HopGuiExecutionMapGraph graph = EdwOpenDiagramGraphSupport.findExecutionMap(document);
      if (graph != null && graph.getFocusContext() != null) {
        render.setExecutionMapFocus(graph.getFocusContext());
      }
    }
    return ExecutionMapSvgPainter.generateExecutionMapSvg(document, render, variables);
  }
}
