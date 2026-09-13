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
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.SourceModelSvgPainter;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;

@DiagramExporter(
    id = "source-model-svg",
    name = "Source model SVG Exporter",
    description = "Exports a source model diagram to Scalable Vector Graphics (SVG)",
    format = "SVG",
    fileExtension = "svg",
    fileFilterNames = {"SVG Files (*.svg)"},
    supportedSubjectTypes = {SourceModel.class})
public class SourceModelSvgDiagramExporter extends EdwSvgDiagramExporter<SourceModel> {

  @Override
  public DiagramExportResult export(
      SourceModel model, DiagramExportOptions options, IExportContext context) throws HopException {
    if (model == null) {
      throw new HopException("Source model is null");
    }
    IVariables variables = context != null ? context.getVariables() : null;
    IHopMetadataProvider metadataProvider = context != null ? context.getMetadataProvider() : null;
    String svgXml =
        SourceModelSvgPainter.generateSourceModelSvg(
            model, renderOptions(options), variables, metadataProvider);
    return finish(svgXml, options, context);
  }
}
