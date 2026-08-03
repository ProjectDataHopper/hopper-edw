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
package org.apache.hop.datavault.metadata.iceberg;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSourceBase;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Iceberg table implementation of a Data Vault source. */
@Getter
@Setter
public class DvIcebergSource extends DvSourceBase {

  private static final Class<?> PKG = DvIcebergSource.class;

  public static final String GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID = "DATAVAULT_SOURCE_ICEBERG_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.CatalogUri.Label",
      toolTip = "i18n::DvIcebergSource.CatalogUri.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String catalogUri;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.Warehouse.Label",
      toolTip = "i18n::DvIcebergSource.Warehouse.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String warehouse;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.Namespace.Label",
      toolTip = "i18n::DvIcebergSource.Namespace.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String namespace;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.TableName.Label",
      toolTip = "i18n::DvIcebergSource.TableName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String tableName;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.SnapshotId.Label",
      toolTip = "i18n::DvIcebergSource.SnapshotId.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String snapshotId;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.Branch.Label",
      toolTip = "i18n::DvIcebergSource.Branch.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String branch;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.S3Endpoint.Label",
      toolTip = "i18n::DvIcebergSource.S3Endpoint.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String s3Endpoint;

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.S3AccessKey.Label",
      toolTip = "i18n::DvIcebergSource.S3AccessKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String s3AccessKey;

  @GuiWidgetElement(
      order = "0900",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvIcebergSource.S3SecretKey.Label",
      toolTip = "i18n::DvIcebergSource.S3SecretKey.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_ICEBERG_TAB_ID)
  @HopMetadataProperty
  private String s3SecretKey;

  public DvIcebergSource() {
    this.sourceType = DvSourceType.ICEBERG;
  }

  @Override
  public boolean supportsLiveFieldResolution() {
    return true;
  }

  @Override
  public IRowMeta resolveLiveFields(IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return DvIcebergSourceLiveSchemaSupport.resolveLiveFields(this, variables, metadataProvider);
  }

  @Override
  public List<RowMetaAndData> previewRecords(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    IcebergConnectionSettings settings = IcebergConnectionSettings.from(this, variables);
    List<String> fieldNames = new ArrayList<>();
    if (getFields() != null) {
      for (SourceField field : getFields()) {
        if (field != null && !Utils.isEmpty(field.getName())) {
          fieldNames.add(variables.resolve(field.getName()));
        }
      }
    }
    try (IcebergTableReader reader = new IcebergTableReader(settings, fieldNames)) {
      return reader.readAll(rowLimit);
    } catch (Exception e) {
      throw new HopException("Unable to preview Iceberg source", e);
    }
  }
}
