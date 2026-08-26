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

import java.nio.file.Path;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.menu.GuiMenuElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

/** Main toolbar and Help menu entry that opens the plugin-shipped HTML documentation. */
@GuiPlugin
public class EdwDocsGuiPlugin {

  public static final Class<?> PKG = EdwDocsGuiPlugin.class;

  public static final String ID_MAIN_TOOLBAR_EDW_DOCS = "toolbar-10070-edw-docs";
  public static final String ID_MAIN_MENU_HELP_EDW_DOCS = "90005-menu-help-edw-docs";

  private static EdwDocsGuiPlugin instance;

  public EdwDocsGuiPlugin() {
    // Instantiated by the GUI plugin system
  }

  public static EdwDocsGuiPlugin getInstance() {
    if (instance == null) {
      instance = new EdwDocsGuiPlugin();
    }
    return instance;
  }

  @GuiMenuElement(
      root = HopGui.ID_MAIN_MENU,
      id = ID_MAIN_MENU_HELP_EDW_DOCS,
      label = "i18n::EdwDocsGuiPlugin.Menu.Text",
      toolTip = "i18n::EdwDocsGuiPlugin.Toolbar.Tooltip",
      parentId = HopGui.ID_MAIN_MENU_HELP_PARENT_ID,
      image = "edw-logo.svg")
  @GuiToolbarElement(
      root = HopGui.ID_MAIN_TOOLBAR,
      id = ID_MAIN_TOOLBAR_EDW_DOCS,
      image = "edw-logo.svg",
      toolTip = "i18n::EdwDocsGuiPlugin.Toolbar.Tooltip",
      separator = true)
  public void openDocumentation() {
    openHtml(HopGui.getInstance(), "index.html");
  }

  /** Open a plugin-shipped HTML page (for example {@code edw-journey.html}) in the browser. */
  public static void openHtml(HopGui hopGui, String pageName) {
    openHtml(hopGui == null ? null : hopGui.getShell(), pageName, null);
  }

  /**
   * Open a plugin-shipped HTML page, optionally at an in-page fragment ({@code #section-id}).
   *
   * @param shell parent for error dialogs; may be null
   * @param pageName file under {@code docs/}, optionally {@code help/name.html}
   * @param fragment optional AsciiDoc section id without {@code #}
   */
  public static void openHtml(Shell shell, String pageName, String fragment) {
    String page = Const.NVL(pageName, "index.html");
    try {
      Path html = EdwDocsSupport.findHtmlPage(page);
      if (html == null) {
        if (shell == null || shell.isDisposed()) {
          return;
        }
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "EdwDocsGuiPlugin.Error.Title"));
        box.setMessage(BaseMessages.getString(PKG, "EdwDocsGuiPlugin.Error.NotFound", page));
        box.open();
        return;
      }
      String url = html.toUri().toString();
      if (!Utils.isEmpty(fragment)) {
        String id = fragment.startsWith("#") ? fragment.substring(1) : fragment;
        url = url + "#" + id;
      }
      // Open the real file:// URL in the system browser. Hop's HTML explorer tab uses
      // Browser.setText(), which has no document base, so relative links such as
      // ../business-vault-sql-view.html resolve to /business-vault-sql-view.html.
      EnvironmentUtils.getInstance().openUrl(url);
    } catch (Exception e) {
      if (shell == null || shell.isDisposed()) {
        return;
      }
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "EdwDocsGuiPlugin.Error.Title"),
          BaseMessages.getString(PKG, "EdwDocsGuiPlugin.Error.Message"),
          e);
    }
  }
}
