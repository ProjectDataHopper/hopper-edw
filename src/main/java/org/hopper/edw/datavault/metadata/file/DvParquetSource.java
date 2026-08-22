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
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Parquet file implementation of a Data Vault source. */
@Getter
@Setter
public class DvParquetSource extends DvSourceBase implements IDvFileBasedSource {

  public static final String GUI_PLUGIN_ELEMENT_PARQUET_TAB_ID = "DATAVAULT_SOURCE_PARQUET_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvParquetSource.Folder.Label",
      toolTip = "i18n::DvParquetSource.Folder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARQUET_TAB_ID)
  @HopMetadataProperty
  private String folder;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvParquetSource.IncludeFileMask.Label",
      toolTip = "i18n::DvParquetSource.IncludeFileMask.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARQUET_TAB_ID)
  @HopMetadataProperty
  private String includeFileMask;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvParquetSource.ExcludeFileMask.Label",
      toolTip = "i18n::DvParquetSource.ExcludeFileMask.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARQUET_TAB_ID)
  @HopMetadataProperty
  private String excludeFileMask;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DvParquetSource.IncludeSubfolders.Label",
      toolTip = "i18n::DvParquetSource.IncludeSubfolders.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARQUET_TAB_ID)
  @HopMetadataProperty
  private boolean includeSubfolders;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvParquetSource.SingleFilename.Label",
      toolTip = "i18n::DvParquetSource.SingleFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARQUET_TAB_ID)
  @HopMetadataProperty
  private String singleFilename;

  public DvParquetSource() {
    this.sourceType = DvSourceType.PARQUET;
  }

  public boolean usesSingleFile() {
    return singleFilename != null && !singleFilename.isBlank();
  }
}
