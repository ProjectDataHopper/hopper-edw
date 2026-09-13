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
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewGraph;
import org.hopper.edw.datavault.lineageview.HopLineageViewDocument;

@DiagramExporter(
    id = "lineage-view-svg",
    name = "Lineage view SVG Exporter",
    description =
        "Exports an open Hop Lineage View session graph to Scalable Vector Graphics (SVG)",
    format = "SVG",
    fileExtension = "svg",
    fileFilterNames = {"SVG Files (*.svg)"},
    supportedSubjectTypes = {HopLineageViewDocument.class})
public class LineageViewSvgDiagramExporter extends EdwSvgDiagramExporter<HopLineageViewDocument> {

  @Override
  public DiagramExportResult export(
      HopLineageViewDocument document, DiagramExportOptions options, IExportContext context)
      throws HopException {
    if (document == null) {
      throw new HopException("Lineage view document is null");
    }
    if (context == null || context.getEnvironment() != IExportContext.ExportEnvironment.GUI) {
      throw new HopException(
          "Lineage view SVG export needs an open Lineage View tab (the graph is a live session, not stored in the .hlv file).");
    }
    HopGuiLineageViewGraph graph = EdwOpenDiagramGraphSupport.findLineageView(document);
    if (graph == null) {
      throw new HopException(
          "Lineage view SVG export needs an open Lineage View tab (the graph is a live session, not stored in the .hlv file).");
    }
    return finish(graph.generateSessionSvg(), options, context);
  }
}
