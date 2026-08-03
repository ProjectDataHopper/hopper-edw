/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

/**
 * Definition of a single business key component within a Hub. Hubs can have composite business keys
 * (multiple parts).
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
}
