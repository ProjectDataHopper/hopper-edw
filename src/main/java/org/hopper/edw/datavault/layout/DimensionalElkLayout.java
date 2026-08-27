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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;

/** GUI plugin that applies ELK layout to the active dimensional model graph. */
@GuiPlugin
public class DimensionalElkLayout {

  protected static final Class<?> PKG = DimensionalElkLayout.class;
  private static final String KEY_PREFIX = "DimensionalElkLayout";

  public static final String ID_TOOLBAR_ITEM_ELK_LAYOUT =
      "HopGuiDimensionalModelGraph-ToolBar-10045-elk-layout";

  @GuiToolbarElement(
      root = HopGuiDimensionalModelGraph.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ELK_LAYOUT,
      toolTip = "i18n::DimensionalElkLayout.Toolbar.Layout.Tooltip",
      image = "elk-layout.svg")
  public void layoutActiveDimensionalModel() {
    HopGui hopGui = HopGui.getInstance();
    HopGuiDimensionalModelGraph graph = HopGuiDimensionalModelGraph.getInstance();
    if (graph == null) {
      return;
    }

    DimensionalModel model = graph.getModel();
    if (model == null || model.getTables().isEmpty()) {
      return;
    }

    try {
      ElkLayout layout = ElkLayoutGuiSupport.promptForLayout(hopGui.getShell());
      if (layout == null) {
        return;
      }
      graph.runUndoableModelChange(() -> ElkGraphLayout.fromDimensionalModel(model).layout(layout));
    } catch (HopException e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    } catch (Exception e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    }
  }
}
