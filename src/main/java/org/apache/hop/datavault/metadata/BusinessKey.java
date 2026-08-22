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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Definition of a hub business key (vault column) and how a record source supplies it.
 *
 * <p>Two multi-part styles are supported:
 *
 * <ul>
 *   <li><b>Multipartite vault columns</b> — several {@link BusinessKey} rows with different {@link
 *       #name} values; each is a physical hub column and a hash input (legacy / default).
 *   <li><b>Composite (single vault column)</b> — {@link #composite}{@code =true}: one physical
 *       column {@link #name} composed from ordered source parts ({@link #sourceFieldNames} or
 *       legacy {@link #sourceFieldName}). Hash inputs are the parts by default (see {@link
 *       DataVaultConfiguration#isHashUsesComposedBusinessKey()}).
 * </ul>
 *
 * <p>Multi-source hubs repeat rows with the same {@link #name} and different {@link
 * #recordSourceName}, each with its own source field mapping.
 */
@GuiPlugin
@Getter
@Setter
public class BusinessKey {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_BUSINESS_KEY_DIALOG";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessKey.Name.Label",
      toolTip = "i18n::BusinessKey.Name.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String name;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessKey.Description.Label",
      toolTip = "i18n::BusinessKey.Description.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String description;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessKey.DataType.Label",
      toolTip = "i18n::BusinessKey.DataType.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dataType;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessKey.Length.Label",
      toolTip = "i18n::BusinessKey.Length.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String length;

  @GuiWidgetElement(
      order = "0450",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessKey.Precision.Label",
      toolTip = "i18n::BusinessKey.Precision.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String precision;

  /**
   * When {@code true}, this business key is stored as a single hub column ({@link #name}) composed
   * from ordered source parts. When {@code false} (default), {@link #name} is one multipartite
   * vault column supplied by a single source field.
   */
  @GuiWidgetElement(
      order = "0475",
      type = GuiElementType.CHECKBOX,
      label = "i18n::BusinessKey.Composite.Label",
      toolTip = "i18n::BusinessKey.Composite.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean composite;

  /**
   * Ordered source column names that supply this business key for {@link #recordSourceName}.
   *
   * <p>Used when {@link #composite} is true (N parts → one vault column). Empty list falls back to
   * {@link #sourceFieldName} (single field / dual-read).
   */
  @HopMetadataProperty(key = "sourceFieldName", groupKey = "sourceFieldNames")
  private List<String> sourceFieldNames = new ArrayList<>();

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessKey.SourceFieldName.Label",
      toolTip = "i18n::BusinessKey.SourceFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceFieldName;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.TEXT,
      label = "i18n::BusinessKey.SourceSystem.Label",
      toolTip = "i18n::BusinessKey.SourceSystem.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordSourceName;

  public BusinessKey() {}

  public BusinessKey(String name) {
    this.name = name;
  }

  /**
   * Ordered non-empty source field names for this mapping row.
   *
   * <p>Prefers {@link #sourceFieldNames}; if empty, uses legacy {@link #sourceFieldName} as a
   * single-element list.
   */
  public List<String> resolveSourceParts() {
    List<String> parts = new ArrayList<>();
    if (sourceFieldNames != null) {
      for (String part : sourceFieldNames) {
        if (!Utils.isEmpty(part)) {
          parts.add(part);
        }
      }
    }
    if (!parts.isEmpty()) {
      return parts;
    }
    if (!Utils.isEmpty(sourceFieldName)) {
      return List.of(sourceFieldName);
    }
    return List.of();
  }

  /** Number of source parts that contribute to this mapping (0 if none mapped). */
  public int sourcePartCount() {
    return resolveSourceParts().size();
  }
}
