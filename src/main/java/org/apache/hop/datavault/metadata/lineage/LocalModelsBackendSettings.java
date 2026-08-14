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
package org.apache.hop.datavault.metadata.lineage;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.lineageview.backend.LineageBackendKind;
import org.apache.hop.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import org.apache.hop.datavault.resourcedefinition.ValidationModels;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Collects lineage from current DV/BV/DM models in a resource definition group. */
@GuiPlugin(id = "GUI-LocalModelsLineageBackend")
@Getter
@Setter
public class LocalModelsBackendSettings implements ILineageBackendSettings {

  private static final Class<?> PKG = LocalModelsBackendSettings.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID =
      "LocalModelsBackendSettings-PluginSpecific-Options";

  @HopMetadataProperty private String pluginId = PLUGIN_LOCAL_MODELS;

  @GuiWidgetElement(
      order = "10",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::LocalModelsBackendSettings.Group.Label",
      toolTip = "i18n::LocalModelsBackendSettings.Group.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  @GuiWidgetElement(
      order = "20",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::LocalModelsBackendSettings.JobNamespace.Label",
      toolTip = "i18n::LocalModelsBackendSettings.JobNamespace.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String jobNamespace = "${MARQUEZ_NAMESPACE_JOB}";

  @GuiWidgetElement(
      order = "30",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::LocalModelsBackendSettings.DatasetNamespace.Label",
      toolTip = "i18n::LocalModelsBackendSettings.DatasetNamespace.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String datasetNamespace = "${MARQUEZ_NAMESPACE_DATASET}";

  @Override
  public LineageBackendKind kind() {
    return LineageBackendKind.LOCAL_MODELS;
  }

  @Override
  public LineageConnectionTestResult testConnection(
      IVariables variables, IHopMetadataProvider metadataProvider, ILogChannel log)
      throws HopException {
    if (Utils.isEmpty(resourceDefinitionGroup)) {
      return LineageConnectionTestResult.builder()
          .ok(true)
          .detailCount(0)
          .message(BaseMessages.getString(PKG, "LocalModelsBackendSettings.Test.NoGroup"))
          .build();
    }
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(resourceDefinitionGroup, metadataProvider);
    ValidationModels models =
        ResourceDefinitionGroupResolver.resolve(group, variables, metadataProvider);
    int count =
        models.dataVaultModels().size()
            + models.businessVaultModels().size()
            + models.dimensionalModels().size();
    return LineageConnectionTestResult.builder()
        .ok(true)
        .detailCount(count)
        .message(
            BaseMessages.getString(
                PKG,
                "LocalModelsBackendSettings.Test.Ok",
                resourceDefinitionGroup,
                Integer.toString(count)))
        .build();
  }
}
