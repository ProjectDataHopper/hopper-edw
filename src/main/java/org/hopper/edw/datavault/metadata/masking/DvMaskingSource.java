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
package org.hopper.edw.datavault.metadata.masking;

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DvSourceBase;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.IDvSource;

/**
 * Masking feed defined by a {@code SourceMasking} card inside a {@code .hsm} source model.
 *
 * <p>At generation time the live {@code .hsm} is preferred.
 */
@Getter
@Setter
public class DvMaskingSource extends DvSourceBase implements IDvSource {

  public static final String GUI_PLUGIN_ELEMENT_MASKING_TAB_ID = "DATAVAULT_SOURCE_MASKING_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::DvMaskingSource.SourceModelFilename.Label",
      toolTip = "i18n::DvMaskingSource.SourceModelFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MASKING_TAB_ID)
  @HopMetadataProperty
  private String sourceModelFilename;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvMaskingSource.SourceMaskingName.Label",
      toolTip = "i18n::DvMaskingSource.SourceMaskingName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_MASKING_TAB_ID)
  @HopMetadataProperty
  private String sourceMaskingName;

  public DvMaskingSource() {
    this.sourceType = DvSourceType.MASKING;
  }

  @Override
  public List<RowMetaAndData> previewRecords(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    return Collections.emptyList();
  }
}
