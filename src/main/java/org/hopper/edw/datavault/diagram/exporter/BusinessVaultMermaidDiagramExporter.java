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
import org.hopper.edw.datavault.diagram.schema.BusinessVaultSchemaBuilder;
import org.hopper.edw.datavault.diagram.schema.MermaidSchemaRenderer;
import org.hopper.edw.datavault.diagram.schema.SchemaRenderOptions;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;

@DiagramExporter(
    id = "business-vault-mermaid",
    name = "Business Vault Mermaid Exporter",
    description = "Exports a Business Vault model as a Mermaid erDiagram",
    format = "MERMAID",
    fileExtension = "mmd",
    fileFilterNames = {"Mermaid Diagrams (*.mmd)"},
    supportedSubjectTypes = {BusinessVaultModel.class})
public class BusinessVaultMermaidDiagramExporter
    extends EdwTextDiagramExporter<BusinessVaultModel> {

  @Override
  public DiagramExportResult export(
      BusinessVaultModel model, DiagramExportOptions options, IExportContext context)
      throws HopException {
    if (model == null) {
      throw new HopException("Business Vault model is null");
    }
    String content =
        MermaidSchemaRenderer.render(
            BusinessVaultSchemaBuilder.build(model), SchemaRenderOptions.from(options));
    return finishText(content, options, context, "text/vnd.mermaid");
  }
}
