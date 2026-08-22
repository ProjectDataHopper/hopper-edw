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
package org.hopper.edw.datavault.metadata.file;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.hopper.edw.datavault.metadata.DvSourceBase;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * CSV / delimited text file implementation of {@link IDvSource}, embedded in {@link
 * org.hopper.edw.datavault.metadata.DataVaultSource}.
 */
@Getter
@Setter
public class DvCsvSource extends DvSourceBase implements IDvFileBasedSource {

  public static final String GUI_PLUGIN_ELEMENT_CSV_TAB_ID = "DATAVAULT_SOURCE_CSV_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvCsvSource.Folder.Label",
      toolTip = "i18n::DvCsvSource.Folder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private String folder;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvCsvSource.IncludeFileMask.Label",
      toolTip = "i18n::DvCsvSource.IncludeFileMask.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private String includeFileMask;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvCsvSource.ExcludeFileMask.Label",
      toolTip = "i18n::DvCsvSource.ExcludeFileMask.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private String excludeFileMask;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DvCsvSource.IncludeSubfolders.Label",
      toolTip = "i18n::DvCsvSource.IncludeSubfolders.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private boolean includeSubfolders;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvCsvSource.SingleFilename.Label",
      toolTip = "i18n::DvCsvSource.SingleFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private String singleFilename;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.TEXT,
      label = "i18n::DvCsvSource.Delimiter.Label",
      toolTip = "i18n::DvCsvSource.Delimiter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private String delimiter = ",";

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.TEXT,
      label = "i18n::DvCsvSource.Enclosure.Label",
      toolTip = "i18n::DvCsvSource.Enclosure.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private String enclosure = "\"";

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.TEXT,
      label = "i18n::DvCsvSource.EscapeCharacter.Label",
      toolTip = "i18n::DvCsvSource.EscapeCharacter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private String escapeCharacter;

  @GuiWidgetElement(
      order = "0900",
      type = GuiElementType.TEXT,
      label = "i18n::DvCsvSource.Encoding.Label",
      toolTip = "i18n::DvCsvSource.Encoding.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private String encoding;

  @GuiWidgetElement(
      order = "1000",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DvCsvSource.HeaderPresent.Label",
      toolTip = "i18n::DvCsvSource.HeaderPresent.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private boolean headerPresent = true;

  @GuiWidgetElement(
      order = "1100",
      type = GuiElementType.TEXT,
      label = "i18n::DvCsvSource.HeaderLines.Label",
      toolTip = "i18n::DvCsvSource.HeaderLines.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_CSV_TAB_ID)
  @HopMetadataProperty
  private int headerLines = 1;

  @HopMetadataProperty(storeWithCode = true)
  private DvCsvInputMode inputMode = DvCsvInputMode.TEXT_FILE_INPUT;

  public DvCsvSource() {
    this.sourceType = DvSourceType.CSV;
  }

  public boolean usesSingleFile() {
    return inputMode == DvCsvInputMode.CSV_INPUT
        || (singleFilename != null && !singleFilename.isBlank());
  }
}
