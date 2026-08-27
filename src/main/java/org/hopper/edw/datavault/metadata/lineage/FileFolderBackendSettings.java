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
package org.hopper.edw.datavault.metadata.lineage;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.lineageview.backend.LineageBackendKind;

/** Folder of exported OpenLineage RunEvent JSON files. */
@GuiPlugin(id = "GUI-FileFolderLineageBackend")
@Getter
@Setter
public class FileFolderBackendSettings implements ILineageBackendSettings {

  private static final Class<?> PKG = FileFolderBackendSettings.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID =
      "FileFolderBackendSettings-PluginSpecific-Options";

  @HopMetadataProperty private String pluginId = PLUGIN_FILE_FOLDER;

  @GuiWidgetElement(
      order = "10",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::FileFolderBackendSettings.Folder.Label",
      toolTip = "i18n::FileFolderBackendSettings.Folder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String folder;

  @Override
  public LineageBackendKind kind() {
    return LineageBackendKind.FILE_FOLDER;
  }

  @Override
  public LineageConnectionTestResult testConnection(
      IVariables variables, IHopMetadataProvider metadataProvider, ILogChannel log)
      throws HopException {
    String resolved = variables != null ? variables.resolve(Const.NVL(folder, "")) : folder;
    if (Utils.isEmpty(resolved)) {
      throw new HopException(
          BaseMessages.getString(PKG, "FileFolderBackendSettings.Error.NoFolder"));
    }
    try {
      FileObject dir = HopVfs.getFileObject(resolved);
      if (dir == null || !dir.exists() || !dir.isFolder()) {
        return LineageConnectionTestResult.builder()
            .ok(false)
            .message(
                BaseMessages.getString(PKG, "FileFolderBackendSettings.Test.Missing", resolved))
            .build();
      }
      int json = 0;
      FileObject[] children = dir.getChildren();
      if (children != null) {
        for (FileObject child : children) {
          if (child == null || child.isFolder()) {
            continue;
          }
          String name = child.getName().getBaseName();
          if (name != null
              && name.toLowerCase().endsWith(".json")
              && !"export-summary.json".equalsIgnoreCase(name)) {
            json++;
          }
        }
      }
      return LineageConnectionTestResult.builder()
          .ok(true)
          .detailCount(json)
          .message(
              BaseMessages.getString(
                  PKG, "FileFolderBackendSettings.Test.Ok", resolved, Integer.toString(json)))
          .build();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "FileFolderBackendSettings.Error.Read", resolved), e);
    }
  }
}
