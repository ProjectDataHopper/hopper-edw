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
package org.hopper.edw.datavault.hopgui.opsanalytics;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.menu.GuiMenuElement;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.edw.datavault.hopgui.security.EdwWebAuthorizationUi;

/** Tools menu entry for operational analytics. */
@GuiPlugin
public class OpsAnalyticsGuiPlugin {

  public static final Class<?> PKG = OpsAnalyticsGuiPlugin.class;

  public static final String ID_MAIN_MENU_TOOLS_OPS_ANALYTICS =
      "40190-menu-tools-ops-analytics";

  @GuiMenuElement(
      root = HopGui.ID_MAIN_MENU,
      id = ID_MAIN_MENU_TOOLS_OPS_ANALYTICS,
      label = "i18n::OpsAnalyticsGuiPlugin.Menu.Text",
      toolTip = "i18n::OpsAnalyticsGuiPlugin.Menu.Tooltip",
      parentId = HopGui.ID_MAIN_MENU_TOOLS_PARENT_ID,
      image = "datavault-configuration.svg",
      separator = true)
  public void menuToolsCreateOperationalAnalytics() {
    if (!EdwWebAuthorizationUi.allowCreateFiles()) {
      return;
    }
    HopGui hopGui = HopGui.getInstance();
    try {
      OpsAnalyticsDialog.open(hopGui);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "OpsAnalyticsDialog.Error.Title"),
          BaseMessages.getString(PKG, "OpsAnalyticsDialog.Error.Message"),
          e);
    }
  }
}
