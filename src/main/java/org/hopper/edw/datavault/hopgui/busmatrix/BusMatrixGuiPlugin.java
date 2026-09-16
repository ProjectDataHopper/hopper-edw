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
package org.hopper.edw.datavault.hopgui.busmatrix;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.menu.GuiMenuElement;
import org.apache.hop.ui.hopgui.HopGui;

/** Tools menu: view the Kimball bus matrix for a resource definition group. */
@GuiPlugin
public class BusMatrixGuiPlugin {

  public static final String ID_MAIN_MENU_TOOLS_BUS_MATRIX = "40180-menu-tools-view-bus-matrix";

  private static BusMatrixGuiPlugin instance;

  public BusMatrixGuiPlugin() {}

  public static BusMatrixGuiPlugin getInstance() {
    if (instance == null) {
      instance = new BusMatrixGuiPlugin();
    }
    return instance;
  }

  @GuiMenuElement(
      root = HopGui.ID_MAIN_MENU,
      id = ID_MAIN_MENU_TOOLS_BUS_MATRIX,
      label = "i18n::BusMatrixGuiPlugin.Menu.Text",
      toolTip = "i18n::BusMatrixGuiPlugin.Menu.Tooltip",
      parentId = HopGui.ID_MAIN_MENU_TOOLS_PARENT_ID,
      image = "business-process-catalog.svg")
  public void menuToolsViewBusMatrix() {
    BusMatrixLaunchSupport.openPicker(HopGui.getInstance());
  }
}
