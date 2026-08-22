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
package org.hopper.edw.datavault.hopgui;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.menu.GuiMenuElement;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;

/** Tools menu entry to (re)open the EDW project-setup dialog. */
@GuiPlugin
public class EdwSetupGuiPlugin {

  public static final Class<?> PKG = EdwSetupGuiPlugin.class;

  public static final String ID_MAIN_MENU_TOOLS_EDW_SETUP = "40160-menu-tools-edw-setup";

  private static EdwSetupGuiPlugin instance;

  public EdwSetupGuiPlugin() {
    // Instantiated by the GUI plugin system
  }

  public static EdwSetupGuiPlugin getInstance() {
    if (instance == null) {
      instance = new EdwSetupGuiPlugin();
    }
    return instance;
  }

  @GuiMenuElement(
      root = HopGui.ID_MAIN_MENU,
      id = ID_MAIN_MENU_TOOLS_EDW_SETUP,
      label = "i18n::EdwSetupGuiPlugin.Menu.Text",
      toolTip = "i18n::EdwSetupGuiPlugin.Menu.Tooltip",
      parentId = HopGui.ID_MAIN_MENU_TOOLS_PARENT_ID,
      image = "datavault-configuration.svg",
      separator = true)
  public void menuToolsEdwSetup() {
    HopGui hopGui = HopGui.getInstance();
    try {
      StandardProjectElementsOfferSupport.openFromMenu(hopGui);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "EdwSetupGuiPlugin.Error.Title"),
          BaseMessages.getString(PKG, "EdwSetupGuiPlugin.Error.Message"),
          e);
    }
  }
}
