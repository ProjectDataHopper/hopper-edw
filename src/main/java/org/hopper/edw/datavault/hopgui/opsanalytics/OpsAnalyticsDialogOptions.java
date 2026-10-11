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
package org.hopper.edw.datavault.hopgui.opsanalytics;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.gui.plugin.GuiWidgetGroupType;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.workflow.config.WorkflowRunConfiguration;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsDialogSupport;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsProjectBuilder;
import org.hopper.edw.datavault.opsanalytics.OpsAnalyticsRunConfigurationSupport;

/** Fields for the Create operational analytics dialog. */
@Getter
@Setter
@GuiPlugin(description = "Create operational analytics")
public class OpsAnalyticsDialogOptions {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "OpsAnalyticsDialog";
  private static final String GROUP = "i18n::OpsAnalyticsDialog.Group";

  @GuiWidgetElement(
      id = "connectionName",
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::OpsAnalyticsDialog.OpsConnection.Label",
      toolTip = "i18n::OpsAnalyticsDialog.OpsConnection.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String connectionName;

  @GuiWidgetElement(
      id = "schemaName",
      order = "0200",
      type = GuiElementType.TEXT,
      label = "i18n::OpsAnalyticsDialog.OpsSchema.Label",
      toolTip = "i18n::OpsAnalyticsDialog.OpsSchema.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String schemaName = "dv_ops";

  @GuiWidgetElement(
      id = "dvTargetConnection",
      order = "0300",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::OpsAnalyticsDialog.DvConnection.Label",
      toolTip = "i18n::OpsAnalyticsDialog.DvConnection.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String dvTargetConnection;

  @GuiWidgetElement(
      id = "bvTargetConnection",
      order = "0400",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::OpsAnalyticsDialog.BvConnection.Label",
      toolTip = "i18n::OpsAnalyticsDialog.BvConnection.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String bvTargetConnection;

  @GuiWidgetElement(
      id = "dmTargetConnection",
      order = "0500",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::OpsAnalyticsDialog.DmConnection.Label",
      toolTip = "i18n::OpsAnalyticsDialog.DmConnection.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String dmTargetConnection;

  @GuiWidgetElement(
      id = "outputFolder",
      order = "0600",
      type = GuiElementType.FOLDER,
      label = "i18n::OpsAnalyticsDialog.Folder.Label",
      toolTip = "i18n::OpsAnalyticsDialog.Folder.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String outputFolder = OpsAnalyticsDialogSupport.DEFAULT_OUTPUT_FOLDER;

  @GuiWidgetElement(
      id = "groupName",
      order = "0700",
      type = GuiElementType.TEXT,
      label = "i18n::OpsAnalyticsDialog.GroupName.Label",
      toolTip = "i18n::OpsAnalyticsDialog.GroupName.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String groupName = OpsAnalyticsProjectBuilder.GROUP_NAME;

  @GuiWidgetElement(
      id = "pipelineRunConfiguration",
      order = "0720",
      type = GuiElementType.METADATA,
      metadata = PipelineRunConfiguration.class,
      label = "i18n::OpsAnalyticsDialog.PipelineRunConfiguration.Label",
      toolTip = "i18n::OpsAnalyticsDialog.PipelineRunConfiguration.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String pipelineRunConfiguration =
      OpsAnalyticsRunConfigurationSupport.PIPELINE_RUN_CONFIGURATION;

  @GuiWidgetElement(
      id = "workflowRunConfiguration",
      order = "0740",
      type = GuiElementType.METADATA,
      metadata = WorkflowRunConfiguration.class,
      label = "i18n::OpsAnalyticsDialog.WorkflowRunConfiguration.Label",
      toolTip = "i18n::OpsAnalyticsDialog.WorkflowRunConfiguration.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String workflowRunConfiguration =
      OpsAnalyticsRunConfigurationSupport.WORKFLOW_RUN_CONFIGURATION;

  @GuiWidgetElement(
      id = "includeExecutions",
      order = "0800",
      type = GuiElementType.CHECKBOX,
      label = "i18n::OpsAnalyticsDialog.IncludeExecutions.Label",
      toolTip = "i18n::OpsAnalyticsDialog.IncludeExecutions.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private boolean includeExecutions;

  @GuiWidgetElement(
      id = "executionSchema",
      order = "0900",
      type = GuiElementType.TEXT,
      label = "i18n::OpsAnalyticsDialog.ExecutionSchema.Label",
      toolTip = "i18n::OpsAnalyticsDialog.ExecutionSchema.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String executionSchema = "";

  @GuiWidgetElement(
      id = "replaceExisting",
      order = "1000",
      type = GuiElementType.CHECKBOX,
      label = "i18n::OpsAnalyticsDialog.Replace.Label",
      toolTip = "i18n::OpsAnalyticsDialog.Replace.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private boolean replaceExisting;
}
