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
package org.apache.hop.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.datatypemapping.IDataTypeMappingTarget;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Default connection and naming settings for source models.
 *
 * <p>Project metadata that many {@link SourceModel} files can share. Older {@code .hsm} files may
 * still embed a copy inline; prefer a named reference via {@code configurationName}.
 */
@HopMetadata(
    key = "source-model-configuration",
    name = "i18n::SourceModelConfiguration.name",
    description = "i18n::SourceModelConfiguration.description",
    image = "source-model.svg",
    documentationUrl = "/metadata-types/source-model-configuration.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@GuiPlugin
@Getter
@Setter
public class SourceModelConfiguration extends HopMetadataBase implements IHopMetadata {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "SOURCE_MODEL_DIALOG";

  public static SourceModelConfiguration createDefault() {
    return new SourceModelConfiguration();
  }

  @HopMetadataProperty private String description;

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::SourceModelConfiguration.DefaultDatabase.Label",
      toolTip = "i18n::SourceModelConfiguration.DefaultDatabase.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String defaultDatabase;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::SourceModelConfiguration.DefaultSchema.Label",
      toolTip = "i18n::SourceModelConfiguration.DefaultSchema.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String defaultSchema;

  /** Optional catalog connection used when publishing single-table or composite feeds. */
  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::SourceModelConfiguration.CatalogConnection.Label",
      toolTip = "i18n::SourceModelConfiguration.CatalogConnection.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String catalogConnection;

  /** Namespace prefix for published catalog feeds (e.g. hop/project/sources). */
  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::SourceModelConfiguration.CatalogNamespace.Label",
      toolTip = "i18n::SourceModelConfiguration.CatalogNamespace.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String catalogNamespace;

  /**
   * Comma-separated project Data type mapping profile names applied by default to newly imported
   * (or newly added) source tables/queries/JSON/pipeline cards when they have no profiles yet.
   */
  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::SourceModelConfiguration.DefaultDataTypeMappings.Label",
      toolTip = "i18n::SourceModelConfiguration.DefaultDataTypeMappings.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String defaultDataTypeMappingNames;

  /** Parse {@link #defaultDataTypeMappingNames} into an ordered list of profile names. */
  public List<String> resolveDefaultDataTypeMappingNames() {
    if (Utils.isEmpty(defaultDataTypeMappingNames)) {
      return List.of();
    }
    return Arrays.stream(defaultDataTypeMappingNames.split("[,;]"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * Attach model default data type mapping profiles to a target that has none configured yet.
   *
   * @return true when profiles were attached
   */
  public boolean applyDefaultDataTypeMappings(IDataTypeMappingTarget target) {
    if (target == null) {
      return false;
    }
    if (target.getDataTypeMappingNames() != null && !target.getDataTypeMappingNames().isEmpty()) {
      return false;
    }
    List<String> defaults = resolveDefaultDataTypeMappingNames();
    if (defaults.isEmpty()) {
      return false;
    }
    target.setDataTypeMappingNames(new ArrayList<>(defaults));
    return true;
  }
}
