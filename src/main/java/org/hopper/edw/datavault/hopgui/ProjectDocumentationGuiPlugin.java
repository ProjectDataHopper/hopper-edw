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
import org.apache.hop.core.gui.plugin.callback.GuiCallback;
import org.apache.hop.core.gui.plugin.menu.GuiMenuElement;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerFile;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.apache.hop.workflow.WorkflowMeta;
import org.eclipse.swt.SWT;
import org.hopper.edw.datavault.documentation.ProjectDocumentationOptions;
import org.hopper.edw.datavault.documentation.ProjectDocumentationResult;
import org.hopper.edw.datavault.documentation.ProjectDocumentationService;
import org.hopper.edw.datavault.hopgui.file.projectdoc.ProjectDocumentationExplorerSupport;
import org.hopper.edw.datavault.workflow.actions.generatedocumentation.ActionGenerateProjectDocumentation;
import org.hopper.edw.datavault.workflow.actions.generatedocumentation.ActionGenerateProjectDocumentationDialog;

/** Tools menu: generate a static HTML documentation set for the current project. */
@GuiPlugin
public class ProjectDocumentationGuiPlugin {

  public static final Class<?> PKG = ProjectDocumentationGuiPlugin.class;

  public static final String ID_MAIN_MENU_TOOLS_PROJECT_DOC =
      "40170-menu-tools-generate-project-documentation";

  private static ProjectDocumentationGuiPlugin instance;

  public ProjectDocumentationGuiPlugin() {}

  public static ProjectDocumentationGuiPlugin getInstance() {
    if (instance == null) {
      instance = new ProjectDocumentationGuiPlugin();
    }
    return instance;
  }

  @GuiCallback(callbackId = ExplorerPerspective.GUI_TOOLBAR_CREATED_CALLBACK_ID)
  public void registerExplorerListener() {
    ProjectDocumentationExplorerSupport.register(ExplorerPerspective.getInstance());
  }

  @GuiMenuElement(
      root = ExplorerPerspective.GUI_PLUGIN_CONTEXT_MENU_PARENT_ID,
      parentId = ExplorerPerspective.GUI_PLUGIN_CONTEXT_MENU_PARENT_ID,
      id = ProjectDocumentationExplorerSupport.ID_CONTEXT_MENU_OPEN_IN_BROWSER,
      label = "i18n::ProjectDocumentationGuiPlugin.Menu.OpenInBrowser",
      toolTip = "i18n::ProjectDocumentationGuiPlugin.Menu.OpenInBrowser.Tooltip",
      image = "edw-logo.svg")
  public void menuOpenInBrowser() {
    HopGui hopGui = HopGui.getInstance();
    try {
      ExplorerPerspective explorer = ExplorerPerspective.getInstance();
      if (explorer == null) {
        return;
      }
      ExplorerFile selected = explorer.getSelectedFile();
      if (selected == null || Utils.isEmpty(selected.getFilename())) {
        return;
      }
      if (!EdwDocsWebSupport.canOpenInBrowser(selected.getFilename())) {
        MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
        box.setText(
            BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.OpenInBrowser.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG, "ProjectDocumentationGuiPlugin.OpenInBrowser.NotSupported"));
        box.open();
        return;
      }
      EdwDocsWebSupport.openInBrowser(selected.getFilename());
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.OpenInBrowser.Title"),
          BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.OpenInBrowser.Error"),
          e);
    }
  }

  @GuiMenuElement(
      root = HopGui.ID_MAIN_MENU,
      id = ID_MAIN_MENU_TOOLS_PROJECT_DOC,
      label = "i18n::ProjectDocumentationGuiPlugin.Menu.Text",
      toolTip = "i18n::ProjectDocumentationGuiPlugin.Menu.Tooltip",
      parentId = HopGui.ID_MAIN_MENU_TOOLS_PARENT_ID,
      image = "edw-logo.svg")
  public void menuToolsGenerateProjectDocumentation() {
    HopGui hopGui = HopGui.getInstance();
    try {
      ActionGenerateProjectDocumentation action = new ActionGenerateProjectDocumentation();
      action.setName(
          BaseMessages.getString(
              ActionGenerateProjectDocumentation.class, "ActionGenerateProjectDocumentation.Name"));
      ActionGenerateProjectDocumentationDialog dialog =
          new ActionGenerateProjectDocumentationDialog(
              hopGui.getShell(), action, new WorkflowMeta(), hopGui.getVariables());
      if (dialog.open() == null) {
        return;
      }
      ProjectDocumentationOptions options = new ProjectDocumentationOptions();
      options.setTargetFolder(hopGui.getVariables().resolve(action.getTargetFolder()));
      options.setSourceFolder(
          Utils.isEmpty(action.getSourceFolder())
              ? null
              : hopGui.getVariables().resolve(action.getSourceFolder()));
      options.setScope(
          org.hopper.edw.datavault.documentation.model.DocumentationScope.parse(action.getScope()));
      options.setTheme(action.getTheme());
      options.setIncludingParameters(action.isIncludingParameters());
      options.setIncludingNotes(action.isIncludingNotes());
      options.setIncludingMetadata(action.isIncludingMetadata());
      options.setIncludingCatalog(action.isIncludingCatalog());
      options.setIncludingLineage(action.isIncludingLineage());
      options.setDarkSvg(action.isDarkSvg());
      options.setProjectName(
          hopGui.getVariables().getVariable(ProjectDocumentationService.VAR_PROJECT_NAME));

      GuiProgressSupport.runAsync(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.Progress.Title"),
          true,
          monitor ->
              ProjectDocumentationService.generate(
                  options,
                  hopGui.getVariables(),
                  hopGui.getMetadataProvider(),
                  LogChannel.UI,
                  monitor),
          progress -> handleGenerateResult(hopGui, progress));
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.Error.Title"),
          BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.Error.Message"),
          e);
    }
  }

  static void handleGenerateResult(
      HopGui hopGui, GuiProgressSupport.ProgressResult<ProjectDocumentationResult> progress) {
    if (hopGui == null || hopGui.getShell() == null || hopGui.getShell().isDisposed()) {
      return;
    }
    if (progress == null || progress.value() == null) {
      return;
    }
    ProjectDocumentationResult result = progress.value();
    try {
      if (progress.cancelled() || result.isCancelled()) {
        MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.Cancelled.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG,
                "ProjectDocumentationGuiPlugin.Cancelled.Message",
                result.getPagesWritten(),
                result.getOutputFolder()));
        box.open();
        return;
      }
      openGeneratedSite(hopGui, result.getOutputFolder());
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.Error.Title"),
          BaseMessages.getString(PKG, "ProjectDocumentationGuiPlugin.Error.Message"),
          e);
    }
  }

  static void openGeneratedSite(HopGui hopGui, String outputFolder) throws Exception {
    String index = HopVfs.getFilename(HopVfs.getFileObject(outputFolder + "/index.html"));
    if (EnvironmentUtils.getInstance().isWeb()) {
      try {
        HopGui.getExplorerPerspective().refresh();
      } catch (Exception ignored) {
        // Best-effort: the project tree can still be stale if refresh fails.
      }
      EdwDocsWebSupport.openInBrowser(index);
      return;
    }
    EnvironmentUtils.getInstance().openUrl(java.nio.file.Path.of(index).toUri().toString());
  }
}
