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
package org.hopper.edw.datavault.diagram.schema;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.diagram.EdwDiagramExportSupport;

/**
 * Extra PlantUML / Mermaid / Draw.io options from {@link DiagramExportOptions#getExtraOptions()}.
 */
@Getter
@Setter
public class SchemaRenderOptions {

  public enum SyntaxStyle {
    CLASS,
    ENTITY
  }

  public enum DiagramType {
    ER,
    CLASS
  }

  public enum LayoutMode {
    ELK,
    SWIMLANE
  }

  private SyntaxStyle syntaxStyle = SyntaxStyle.CLASS;
  private DiagramType diagramType = DiagramType.ER;
  private LayoutMode layoutMode = LayoutMode.ELK;
  private boolean includeDataTypes = true;
  private boolean includeGrain;
  private boolean markdownFence;
  private boolean includeColumnDetails = true;
  private boolean includeNotes = true;

  public static SchemaRenderOptions from(DiagramExportOptions options) {
    SchemaRenderOptions render = new SchemaRenderOptions();
    if (options == null) {
      return render;
    }
    render.setIncludeNotes(options.isIncludeNotes());
    Map<String, String> extra = options.getExtraOptions();
    if (extra == null || extra.isEmpty()) {
      return render;
    }
    String syntax = extra.get(EdwDiagramExportSupport.EXTRA_SYNTAX_STYLE);
    if (!Utils.isEmpty(syntax) && "ENTITY".equalsIgnoreCase(syntax.trim())) {
      render.setSyntaxStyle(SyntaxStyle.ENTITY);
    }
    String diagramType = extra.get(EdwDiagramExportSupport.EXTRA_DIAGRAM_TYPE);
    if (!Utils.isEmpty(diagramType) && "CLASS".equalsIgnoreCase(diagramType.trim())) {
      render.setDiagramType(DiagramType.CLASS);
    }
    String layout = extra.get(EdwDiagramExportSupport.EXTRA_LAYOUT);
    if (!Utils.isEmpty(layout) && "SWIMLANE".equalsIgnoreCase(layout.trim())) {
      render.setLayoutMode(LayoutMode.SWIMLANE);
    }
    render.setIncludeDataTypes(
        extraBool(extra, EdwDiagramExportSupport.EXTRA_INCLUDE_DATA_TYPES, true));
    render.setIncludeGrain(extraBool(extra, EdwDiagramExportSupport.EXTRA_INCLUDE_GRAIN, false));
    render.setMarkdownFence(extraBool(extra, EdwDiagramExportSupport.EXTRA_MARKDOWN_FENCE, false));
    render.setIncludeColumnDetails(
        extraBool(extra, EdwDiagramExportSupport.EXTRA_INCLUDE_COLUMN_DETAILS, true));
    return render;
  }

  private static boolean extraBool(Map<String, String> extra, String key, boolean defaultValue) {
    String value = extra.get(key);
    if (Utils.isEmpty(value)) {
      return defaultValue;
    }
    String trimmed = value.trim();
    if ("false".equalsIgnoreCase(trimmed)
        || "0".equals(trimmed)
        || "no".equalsIgnoreCase(trimmed)) {
      return false;
    }
    if ("true".equalsIgnoreCase(trimmed)
        || "1".equals(trimmed)
        || "yes".equalsIgnoreCase(trimmed)) {
      return true;
    }
    return defaultValue;
  }
}
