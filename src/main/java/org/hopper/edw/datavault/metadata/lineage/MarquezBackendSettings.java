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
package org.hopper.edw.datavault.metadata.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.lineageview.backend.LineageBackendKind;
import org.hopper.edw.datavault.lineageview.backend.marquez.MarquezLineageQueryService;
import org.hopper.edw.datavault.lineageview.backend.marquez.MarquezUrls;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Marquez 0.50 HTTP query settings. */
@GuiPlugin(id = "GUI-MarquezLineageBackend")
@Getter
@Setter
public class MarquezBackendSettings implements ILineageBackendSettings {

  private static final Class<?> PKG = MarquezBackendSettings.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID =
      "MarquezBackendSettings-PluginSpecific-Options";

  @HopMetadataProperty private String pluginId = PLUGIN_MARQUEZ;

  @GuiWidgetElement(
      order = "10",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::MarquezBackendSettings.BaseUrl.Label",
      toolTip = "i18n::MarquezBackendSettings.BaseUrl.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String baseUrl = "${MARQUEZ_BASE_URL}";

  @GuiWidgetElement(
      order = "20",
      type = GuiElementType.TEXT,
      label = "i18n::MarquezBackendSettings.ApiKeyHeader.Label",
      toolTip = "i18n::MarquezBackendSettings.ApiKeyHeader.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String apiKeyHeader;

  @GuiWidgetElement(
      order = "30",
      type = GuiElementType.TEXT,
      password = true,
      label = "i18n::MarquezBackendSettings.ApiKey.Label",
      toolTip = "i18n::MarquezBackendSettings.ApiKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty(password = true)
  private String apiKey;

  @GuiWidgetElement(
      order = "40",
      type = GuiElementType.TEXT,
      label = "i18n::MarquezBackendSettings.TimeoutMs.Label",
      toolTip = "i18n::MarquezBackendSettings.TimeoutMs.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String timeoutMs = "30000";

  @GuiWidgetElement(
      order = "50",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::MarquezBackendSettings.DefaultJobNamespace.Label",
      toolTip = "i18n::MarquezBackendSettings.DefaultJobNamespace.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String defaultJobNamespace = "${MARQUEZ_NAMESPACE_JOB}";

  @GuiWidgetElement(
      order = "60",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::MarquezBackendSettings.DefaultDatasetNamespace.Label",
      toolTip = "i18n::MarquezBackendSettings.DefaultDatasetNamespace.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String defaultDatasetNamespace = "${MARQUEZ_NAMESPACE_DATASET}";

  @GuiWidgetElement(
      order = "70",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::MarquezBackendSettings.UiBaseUrl.Label",
      toolTip = "i18n::MarquezBackendSettings.UiBaseUrl.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String uiBaseUrl;

  @Override
  public LineageBackendKind kind() {
    return LineageBackendKind.MARQUEZ;
  }

  @Override
  public LineageConnectionTestResult testConnection(
      IVariables variables, IHopMetadataProvider metadataProvider, ILogChannel log)
      throws HopException {
    String resolved = resolve(variables, baseUrl);
    if (Utils.isEmpty(resolved)) {
      throw new HopException(BaseMessages.getString(PKG, "MarquezBackendSettings.Error.NoUrl"));
    }
    int timeout = parseTimeout(resolve(variables, timeoutMs));
    try (MarquezLineageQueryService service =
        new MarquezLineageQueryService(
            resolved, resolve(variables, apiKeyHeader), apiKey, timeout)) {
      JsonNode namespaces = service.getNamespaces();
      if (namespaces == null) {
        return LineageConnectionTestResult.builder()
            .ok(false)
            .message(BaseMessages.getString(PKG, "MarquezBackendSettings.Test.Empty"))
            .build();
      }
      int count =
          namespaces.path("namespaces").isArray() ? namespaces.path("namespaces").size() : 0;
      return LineageConnectionTestResult.builder()
          .ok(true)
          .detailCount(count)
          .message(
              BaseMessages.getString(
                  PKG,
                  "MarquezBackendSettings.Test.Ok",
                  MarquezUrls.normalizeBaseUrl(resolved),
                  Integer.toString(count)))
          .build();
    }
  }

  public static int parseTimeout(String raw) {
    if (Utils.isEmpty(raw)) {
      return 30_000;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return 30_000;
    }
  }

  private static String resolve(IVariables variables, String value) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
