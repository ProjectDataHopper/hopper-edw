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
package org.apache.hop.datavault.metadata.pipeline;

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
 * Pipeline-backed feed, typically published from a {@code SourcePipeline} in a {@code .hsm} source
 * model. Load pipelines inject this source via MetaInject.
 */
@Getter
@Setter
public class DvPipelineSource extends DvSourceBase implements IDvSource {

  public static final String GUI_PLUGIN_ELEMENT_PIPELINE_TAB_ID = "DATAVAULT_SOURCE_PIPELINE_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::DvPipelineSource.PipelineFilename.Label",
      toolTip = "i18n::DvPipelineSource.PipelineFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PIPELINE_TAB_ID)
  @HopMetadataProperty
  private String pipelineFilename;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvPipelineSource.OutputTransformName.Label",
      toolTip = "i18n::DvPipelineSource.OutputTransformName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PIPELINE_TAB_ID)
  @HopMetadataProperty
  private String outputTransformName;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvPipelineSource.PipelineRunConfiguration.Label",
      toolTip = "i18n::DvPipelineSource.PipelineRunConfiguration.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PIPELINE_TAB_ID)
  @HopMetadataProperty
  private String pipelineRunConfiguration;

  /** Optional path to the {@code .hsm} that owns this feed definition. */
  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::DvPipelineSource.SourceModelFilename.Label",
      toolTip = "i18n::DvPipelineSource.SourceModelFilename.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PIPELINE_TAB_ID)
  @HopMetadataProperty
  private String sourceModelFilename;

  /** Optional name of the {@code SourcePipeline} object inside the model. */
  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvPipelineSource.SourcePipelineName.Label",
      toolTip = "i18n::DvPipelineSource.SourcePipelineName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PIPELINE_TAB_ID)
  @HopMetadataProperty
  private String sourcePipelineName;

  public DvPipelineSource() {
    this.sourceType = DvSourceType.PIPELINE;
  }

  @Override
  public List<RowMetaAndData> previewRecords(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    return DvPipelineSourceSupport.previewRecords(
        this, variables, metadataProvider, rowLimit, queryTimeoutSeconds);
  }
}
