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
package org.hopper.edw.datavault.layout;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.apache.hop.ui.hopgui.HopGui;

/** GUI plugin that applies ELK layout to the active Data Vault model graph. */
@GuiPlugin
public class VaultElkLayout {

  protected static final Class<?> PKG = VaultElkLayout.class;
  private static final String KEY_PREFIX = "VaultElkLayout";

  public static final String ID_TOOLBAR_ITEM_ELK_LAYOUT =
      "HopGuiVaultGraph-ToolBar-10045-elk-layout";

  @GuiToolbarElement(
      root = HopGuiVaultGraph.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ELK_LAYOUT,
      toolTip = "i18n::VaultElkLayout.Toolbar.Layout.Tooltip",
      image = "elk-layout.svg")
  public void layoutActiveVaultModel() {
    HopGui hopGui = HopGui.getInstance();
    HopGuiVaultGraph vaultGraph = HopGuiVaultGraph.getInstance();
    if (vaultGraph == null) {
      return;
    }

    DataVaultModel model = vaultGraph.getModel();
    if (model == null) {
      return;
    }

    List<IDvTable> tables = model.getTables();
    if (tables == null || tables.isEmpty()) {
      return;
    }

    try {
      ElkLayout layout = ElkLayoutGuiSupport.promptForLayout(hopGui.getShell());
      if (layout == null) {
        return;
      }
      vaultGraph.runUndoableModelChange(
          () -> ElkGraphLayout.fromDataVaultModel(model).layout(layout));
    } catch (HopException e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    } catch (Exception e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    }
  }
}
