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

import org.apache.hop.core.diagram.BaseDiagramExporter;
import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.diagram.DiagramExportResult;
import org.apache.hop.core.diagram.IExportContext;
import org.apache.hop.core.exception.HopException;

/** Shared text write-out for PlantUML, Mermaid, and Draw.io exporters. */
abstract class EdwTextDiagramExporter<T> extends BaseDiagramExporter<T> {

  protected DiagramExportResult finishText(
      String content, DiagramExportOptions options, IExportContext context, String mimeType)
      throws HopException {
    if (options != null && options.getTargetFilename() != null) {
      writeToTarget(options.getTargetFilename(), content, context);
    }
    return DiagramExportResult.success(
        options != null ? options.getTargetFilename() : null, content, mimeType);
  }
}
