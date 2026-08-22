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
package org.apache.hop.datavault.metadata.composite;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSourceBase;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Composed multi-table feed defined by a {@code SourceQuery} inside a {@code .hsm} source model.
 *
 * <p>At generation time the live {@code .hsm} is preferred; {@link #generatedSql} is an optional
 * cache used when the model file is unavailable.
 */
@Getter
@Setter
public class DvCompositeSource extends DvSourceBase implements IDvSource {

  public static final String GUI_PLUGIN_ELEMENT_COMPOSITE_TAB_ID = "DATAVAULT_SOURCE_COMPOSITE_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::DvCompositeSource.SourceModelFilename.Label",
      toolTip = "i18n::DvCompositeSource.SourceModelFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COMPOSITE_TAB_ID)
  @HopMetadataProperty
  private String sourceModelFilename;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvCompositeSource.SourceQueryName.Label",
      toolTip = "i18n::DvCompositeSource.SourceQueryName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COMPOSITE_TAB_ID)
  @HopMetadataProperty
  private String sourceQueryName;

  /**
   * Optional denormalized SQL snapshot from the last publish. Prefer regenerating from the live
   * {@code .hsm} when present.
   */
  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvCompositeSource.GeneratedSql.Label",
      toolTip = "i18n::DvCompositeSource.GeneratedSql.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_COMPOSITE_TAB_ID)
  @HopMetadataProperty
  private String generatedSql;

  public DvCompositeSource() {
    this.sourceType = DvSourceType.COMPOSITE;
  }

  @Override
  public List<RowMetaAndData> previewRecords(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    return DvCompositeSourcePreviewSupport.previewRecords(
        this, variables, metadataProvider, rowLimit, queryTimeoutSeconds);
  }
}
