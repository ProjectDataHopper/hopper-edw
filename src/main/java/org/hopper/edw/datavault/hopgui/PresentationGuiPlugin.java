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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.extension.ExtensionPointPluginType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.menu.GuiMenuElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerFile;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Shell;
import org.hopper.core.HEnvironment;
import org.hopper.edw.datavault.presentation.EdwPresentationDashboards;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.swt.HPresentationChrome;
import org.hopper.presentation.swt.HPresentationViewer;

/**
 * Hop GUI host for generated Hopper presentations (issue #170). Dashboards are built on the fly
 * from execution information; they are not stored in the project metadata catalog.
 */
@GuiPlugin
public class PresentationGuiPlugin {

  public static final Class<?> PKG = PresentationGuiPlugin.class;

  public static final String ID_MAIN_TOOLBAR_PRESENTATION = "toolbar-10080-project-presentation";
  public static final String ID_MAIN_MENU_TOOLS_PRESENTATION =
      "40170-menu-tools-project-presentation";
  public static final String ID_EXPLORER_TOOLBAR_PRESENTATION =
      "ExplorerPerspective-Toolbar-10080-presentation";
  public static final String ID_EXPLORER_MENU_PRESENTATION =
      "ExplorerPerspective-ContextMenu-presentation";

  private static PresentationGuiPlugin instance;
  private static volatile boolean environmentInitialized;

  public PresentationGuiPlugin() {
    // Instantiated by the GUI plugin system
  }

  public static PresentationGuiPlugin getInstance() {
    if (instance == null) {
      instance = new PresentationGuiPlugin();
    }
    return instance;
  }

  @GuiMenuElement(
      root = HopGui.ID_MAIN_MENU,
      id = ID_MAIN_MENU_TOOLS_PRESENTATION,
      label = "i18n::PresentationGuiPlugin.Menu.Text",
      toolTip = "i18n::PresentationGuiPlugin.Menu.Tooltip",
      parentId = HopGui.ID_MAIN_MENU_TOOLS_PARENT_ID,
      image = "execution-metrics-profile.svg")
  @GuiToolbarElement(
      root = HopGui.ID_MAIN_TOOLBAR,
      id = ID_MAIN_TOOLBAR_PRESENTATION,
      image = "execution-metrics-profile.svg",
      toolTip = "i18n::PresentationGuiPlugin.Toolbar.Tooltip")
  public void openProjectPresentation() {
    HopGui hopGui = HopGui.getInstance();
    try {
      ensureEnvironment();
      HGeneratedCatalog catalog =
          EdwPresentationDashboards.projectOverview(
              hopGui.getMetadataProvider(), hopGui.getVariables());
      openViewer(hopGui, catalog);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "PresentationGuiPlugin.Error.Title"),
          BaseMessages.getString(PKG, "PresentationGuiPlugin.Error.Message"),
          e);
    }
  }

  @GuiMenuElement(
      root = ExplorerPerspective.GUI_PLUGIN_CONTEXT_MENU_PARENT_ID,
      parentId = ExplorerPerspective.GUI_PLUGIN_CONTEXT_MENU_PARENT_ID,
      id = ID_EXPLORER_MENU_PRESENTATION,
      label = "i18n::PresentationGuiPlugin.Explorer.Menu",
      image = "execution-metrics-profile.svg")
  @GuiToolbarElement(
      root = ExplorerPerspective.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_EXPLORER_TOOLBAR_PRESENTATION,
      image = "execution-metrics-profile.svg",
      toolTip = "i18n::PresentationGuiPlugin.Explorer.Tooltip")
  public void openSelectedFilePresentation() {
    HopGui hopGui = HopGui.getInstance();
    try {
      ensureEnvironment();
      ExplorerFile selected = ExplorerPerspective.getInstance().getSelectedFile();
      String filename = selected == null ? null : selected.getFilename();
      if (!isPipelineOrWorkflow(filename)) {
        MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "PresentationGuiPlugin.Explorer.None.Title"));
        box.setMessage(BaseMessages.getString(PKG, "PresentationGuiPlugin.Explorer.None.Message"));
        box.open();
        return;
      }
      HGeneratedCatalog catalog =
          EdwPresentationDashboards.pipelineOrWorkflow(
              hopGui.getMetadataProvider(), hopGui.getVariables(), filename);
      openViewer(hopGui, catalog);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "PresentationGuiPlugin.Error.Title"),
          BaseMessages.getString(PKG, "PresentationGuiPlugin.Error.Message"),
          e);
    }
  }

  public static void ensureEnvironment() throws Exception {
    if (environmentInitialized) {
      return;
    }
    synchronized (PresentationGuiPlugin.class) {
      if (!environmentInitialized) {
        IPlugin thisPlugin =
            PluginRegistry.getInstance()
                .findPluginWithId(
                    ExtensionPointPluginType.class, HEnvironment.HOP_PLUGIN_EXTENSION_POINT_ID);
        List<String> libraries =
            thisPlugin != null ? new ArrayList<>(thisPlugin.getLibraries()) : new ArrayList<>();
        URL pluginUrl = thisPlugin != null ? thisPlugin.getPluginDirectory() : null;
        HEnvironment.initEmbed(PresentationGuiPlugin.class.getClassLoader(), libraries, pluginUrl);
        environmentInitialized = true;
      }
    }
  }

  static boolean isPipelineOrWorkflow(String filename) {
    if (StringUtils.isBlank(filename)) {
      return false;
    }
    String lower = filename.toLowerCase();
    return lower.endsWith(".hpl") || lower.endsWith(".hwf");
  }

  static void openViewer(HopGui hopGui, HGeneratedCatalog catalog) throws Exception {
    Shell shell = new Shell(hopGui.getShell(), SWT.SHELL_TRIM);
    String title =
        catalog.getPresentation() != null
                && StringUtils.isNotBlank(catalog.getPresentation().getName())
            ? catalog.getPresentation().getName()
            : BaseMessages.getString(PKG, "PresentationGuiPlugin.Untitled");
    shell.setText(title);
    shell.setLayout(new FormLayout());
    HPresentationViewer viewer =
        new HPresentationViewer(
            shell,
            new LoggingObject("presentation-viewer"),
            catalog.getProvider(),
            catalog.getPresentation(),
            HPresentationChrome.FULL,
            null);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    fd.bottom = new FormAttachment(100, 0);
    viewer.setLayoutData(fd);
    shell.setSize(1000, 800);
    shell.open();
  }
}
