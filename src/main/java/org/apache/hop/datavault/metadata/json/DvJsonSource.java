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
package org.apache.hop.datavault.metadata.json;

import java.util.Collections;
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
 * JSON extraction feed defined by a {@code SourceJson} inside a {@code .hsm} source model.
 *
 * <p>At generation time the live {@code .hsm} is preferred.
 */
@Getter
@Setter
public class DvJsonSource extends DvSourceBase implements IDvSource {

  public static final String GUI_PLUGIN_ELEMENT_JSON_TAB_ID = "DATAVAULT_SOURCE_JSON_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::DvJsonSource.SourceModelFilename.Label",
      toolTip = "i18n::DvJsonSource.SourceModelFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_JSON_TAB_ID)
  @HopMetadataProperty
  private String sourceModelFilename;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvJsonSource.SourceJsonName.Label",
      toolTip = "i18n::DvJsonSource.SourceJsonName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_JSON_TAB_ID)
  @HopMetadataProperty
  private String sourceJsonName;

  public DvJsonSource() {
    this.sourceType = DvSourceType.JSON;
  }

  @Override
  public List<RowMetaAndData> previewRecords(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    // Preview via full pipeline generation is phase-later; fields are catalog-driven for mapping.
    return Collections.emptyList();
  }
}
