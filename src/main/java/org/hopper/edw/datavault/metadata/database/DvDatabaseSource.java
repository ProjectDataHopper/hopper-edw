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
package org.hopper.edw.datavault.metadata.database;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DvSourceBase;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Database (RDBMS) implementation of {@link IDvSource}, embedded in {@link
 * org.hopper.edw.datavault.metadata.DataVaultSource}.
 */
@Getter
@Setter
public class DvDatabaseSource extends DvSourceBase implements IDvSource {

  public static final String GUI_PLUGIN_ELEMENT_DATABASE_TAB_ID = "DATAVAULT_SOURCE_DATABASE_TAB";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.METADATA,
      metadata = DatabaseMeta.class,
      label = "i18n::DvDatabaseSource.DatabaseName.Label",
      toolTip = "i18n::DvDatabaseSource.DatabaseName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DATABASE_TAB_ID)
  @HopMetadataProperty(key = "databaseName")
  private String databaseName;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvDatabaseSource.SchemaName.Label",
      toolTip = "i18n::DvDatabaseSource.SchemaName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DATABASE_TAB_ID)
  @HopMetadataProperty
  private String schemaName;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvDatabaseSource.TableName.Label",
      toolTip = "i18n::DvDatabaseSource.TableName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_DATABASE_TAB_ID)
  @HopMetadataProperty(key = "tableName")
  private String tableName;

  public DvDatabaseSource() {
    this.sourceType = DvSourceType.DATABASE;
  }

  @Override
  public boolean supportsLiveFieldResolution() {
    return true;
  }

  @Override
  public IRowMeta resolveLiveFields(IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return DvDatabaseSourceLiveSchemaSupport.resolveLiveFields(this, variables, metadataProvider);
  }

  @Override
  public List<RowMetaAndData> previewRecords(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    return DvDatabaseSourcePreviewSupport.previewRecords(
        this, variables, metadataProvider, rowLimit, queryTimeoutSeconds);
  }
}
