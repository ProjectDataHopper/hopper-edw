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
package org.apache.hop.datavault.layout;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.ui.hopgui.HopGui;

/** GUI plugin that applies ELK layout to the active Business Vault model graph. */
@GuiPlugin
public class BusinessVaultElkLayout {

  protected static final Class<?> PKG = BusinessVaultElkLayout.class;
  private static final String KEY_PREFIX = "BusinessVaultElkLayout";

  public static final String ID_TOOLBAR_ITEM_ELK_LAYOUT =
      "HopGuiBusinessVaultGraph-ToolBar-10045-elk-layout";

  @GuiToolbarElement(
      root = HopGuiBusinessVaultGraph.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ELK_LAYOUT,
      toolTip = "i18n::BusinessVaultElkLayout.Toolbar.Layout.Tooltip",
      image = "elk-layout.svg")
  public void layoutActiveBusinessVaultModel() {
    HopGui hopGui = HopGui.getInstance();
    HopGuiBusinessVaultGraph graph = HopGuiBusinessVaultGraph.getInstance();
    if (graph == null) {
      return;
    }

    BusinessVaultModel model = graph.getModel();
    if (model == null || model.getTables().isEmpty()) {
      return;
    }

    try {
      ElkLayout layout = ElkLayoutGuiSupport.promptForLayout(hopGui.getShell());
      if (layout == null) {
        return;
      }
      DataVaultModel dataVaultModel = graph.getDataVaultModel();
      graph.runUndoableModelChange(
          () -> ElkGraphLayout.fromBusinessVaultModel(model, dataVaultModel).layout(layout));
    } catch (HopException e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    } catch (Exception e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    }
  }
}
