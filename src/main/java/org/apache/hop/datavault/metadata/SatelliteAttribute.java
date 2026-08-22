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

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** A descriptive attribute stored in a Satellite (attached to a Hub or Link). */
@GuiPlugin
@Getter
@Setter
public class SatelliteAttribute {
  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_SATELLITE_ATTRIBUTE_DIALOG";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::SatelliteAttribute.Name.Label",
      toolTip = "i18n::SatelliteAttribute.Name.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String name;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      label = "i18n::SatelliteAttribute.Description.Label",
      toolTip = "i18n::SatelliteAttribute.Description.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String description;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::SatelliteAttribute.DataType.Label",
      toolTip = "i18n::SatelliteAttribute.DataType.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dataType = "String";

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      label = "i18n::SatelliteAttribute.Length.Label",
      toolTip = "i18n::SatelliteAttribute.Length.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String length;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      label = "i18n::SatelliteAttribute.Precision.Label",
      toolTip = "i18n::SatelliteAttribute.Precision.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String precision;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.CHECKBOX,
      label = "i18n::SatelliteAttribute.IncludeInChangeDataCapture.Label",
      toolTip = "i18n::SatelliteAttribute.IncludeInChangeDataCapture.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includeInChangeDataCapture = true;

  public SatelliteAttribute() {}

  public SatelliteAttribute(String name) {
    this.name = name;
  }
}
