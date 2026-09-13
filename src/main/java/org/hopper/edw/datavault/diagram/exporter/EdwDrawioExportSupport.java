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

import org.hopper.edw.datavault.architecture.ArchitectureGraph;
import org.hopper.edw.datavault.architecture.DrawioArchitectureExporter;
import org.hopper.edw.datavault.diagram.schema.DrawioSchemaRenderer;
import org.hopper.edw.datavault.diagram.schema.SchemaDiagram;
import org.hopper.edw.datavault.diagram.schema.SchemaRenderOptions;

/** Chooses ELK table Draw.io vs architecture swimlanes. */
final class EdwDrawioExportSupport {

  private EdwDrawioExportSupport() {}

  static String export(SchemaDiagram schema, ArchitectureGraph graph, SchemaRenderOptions options) {
    SchemaRenderOptions opts = options != null ? options : new SchemaRenderOptions();
    if (opts.getLayoutMode() == SchemaRenderOptions.LayoutMode.SWIMLANE && graph != null) {
      graph.setFreeformLayout(false);
      return DrawioArchitectureExporter.export(graph);
    }
    return DrawioSchemaRenderer.render(schema, graph, opts);
  }
}
