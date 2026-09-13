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
import org.hopper.edw.datavault.diagram.EdwDiagramExportSupport;
import org.hopper.edw.datavault.hopgui.file.vault.DataVaultModelSvgPainter;
import org.hopper.edw.datavault.metadata.DataVaultModel;

@DiagramExporter(
    id = "data-vault-pdf",
    name = "Data Vault PDF Exporter",
    description = "Exports a Data Vault model diagram to a vector PDF",
    format = "PDF",
    fileExtension = "pdf",
    fileFilterNames = {"PDF Files (*.pdf)"},
    supportedSubjectTypes = {DataVaultModel.class})
public class DataVaultPdfDiagramExporter extends EdwPdfDiagramExporter<DataVaultModel> {

  @Override
  protected String generateSvg(
      DataVaultModel model, DiagramExportOptions options, IExportContext context)
      throws HopException {
    IVariables variables = context != null ? context.getVariables() : null;
    return DataVaultModelSvgPainter.generateDataVaultModelSvg(
        model, EdwDiagramExportSupport.toSvgRenderOptions(options), variables);
  }
}
