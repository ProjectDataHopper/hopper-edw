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
package org.hopper.edw.datavault.diagram;

import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.command.svg.ExecutionMapExportScope;
import org.hopper.edw.datavault.command.svg.SvgRenderOptions;

/** Maps Hop {@link DiagramExportOptions} onto hopper-edw {@link SvgRenderOptions}. */
public final class EdwDiagramExportSupport {

  public static final String EXTRA_SHOW_HASH_KEYS = "showHashKeys";
  public static final String EXTRA_EXECUTION_MAP_SCOPE = "executionMapScope";
  public static final String EXTRA_SYNTAX_STYLE = "syntaxStyle";
  public static final String EXTRA_INCLUDE_DATA_TYPES = "includeDataTypes";
  public static final String EXTRA_INCLUDE_GRAIN = "includeGrain";
  public static final String EXTRA_MARKDOWN_FENCE = "markdownFence";
  public static final String EXTRA_DIAGRAM_TYPE = "diagramType";
  public static final String EXTRA_LAYOUT = "layout";
  public static final String EXTRA_INCLUDE_COLUMN_DETAILS = "includeColumnDetails";

  private EdwDiagramExportSupport() {}

  public static SvgRenderOptions toSvgRenderOptions(DiagramExportOptions options) {
    SvgRenderOptions render = SvgRenderOptions.defaults();
    if (options == null) {
      return render;
    }
    float magnification = options.getMagnification();
    render.setMagnification(
        magnification > 0 ? magnification : SvgRenderOptions.DEFAULT_MAGNIFICATION);
    render.setIncludeNotes(options.isIncludeNotes());
    if (options.getExtraOptions() == null) {
      return render;
    }
    String showHashKeys = options.getExtraOptions().get(EXTRA_SHOW_HASH_KEYS);
    render.setShowHashKeyFieldNames("true".equalsIgnoreCase(showHashKeys));
    String scope = options.getExtraOptions().get(EXTRA_EXECUTION_MAP_SCOPE);
    if (!Utils.isEmpty(scope)) {
      try {
        render.setExecutionMapExportScope(
            ExecutionMapExportScope.valueOf(scope.trim().toUpperCase()));
      } catch (IllegalArgumentException ignored) {
        // Keep the default focused scope.
      }
    }
    return render;
  }
}
