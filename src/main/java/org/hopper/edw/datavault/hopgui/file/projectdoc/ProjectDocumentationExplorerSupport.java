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
package org.hopper.edw.datavault.hopgui.file.projectdoc;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerFile;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.IExplorerFilePaintListener;
import org.apache.hop.ui.hopgui.perspective.explorer.IExplorerSelectionListener;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.hopper.edw.datavault.hopgui.EdwDocsWebSupport;

/**
 * On Hop Web, routes {@code .html} files to {@link HopProjectDocFileType} so the explorer tab
 * serves CSS and links through a RAP handler. Hop's generic HTML handler uses {@code
 * Browser.setText()} and has no document base (Markdown preview looks fine because it inlines CSS).
 */
public final class ProjectDocumentationExplorerSupport
    implements IExplorerFilePaintListener, IExplorerSelectionListener {

  public static final String ID_CONTEXT_MENU_OPEN_IN_BROWSER =
      "ExplorerPerspective-ContextMenu-10103-OpenInBrowser";

  private static final ProjectDocumentationExplorerSupport INSTANCE =
      new ProjectDocumentationExplorerSupport();

  private ProjectDocumentationExplorerSupport() {}

  public static ProjectDocumentationExplorerSupport getInstance() {
    return INSTANCE;
  }

  public static void register(ExplorerPerspective explorer) {
    if (explorer == null) {
      return;
    }
    ProjectDocumentationExplorerSupport listener = getInstance();
    if (EnvironmentUtils.getInstance().isWeb()) {
      installAsHtmlFileType(explorer);
      if (!explorer.getFilePaintListeners().contains(listener)) {
        explorer.getFilePaintListeners().add(listener);
      }
    }
    if (!explorer.getSelectionListeners().contains(listener)) {
      explorer.getSelectionListeners().add(listener);
    }
    enableOpenInBrowserMenu();
  }

  /**
   * Makes this plugin the explorer handler for {@code html}/{@code htm}. Hop indexes the first
   * matching file type; without this, {@code HtmlExplorerFileType} always wins.
   */
  static boolean installAsHtmlFileType(ExplorerPerspective explorer) {
    if (explorer == null) {
      return false;
    }
    try {
      Field field = ExplorerPerspective.class.getDeclaredField("fileTypeByExtension");
      field.setAccessible(true);
      Object raw = field.get(explorer);
      if (!(raw instanceof Map<?, ?>)) {
        return false;
      }
      @SuppressWarnings("unchecked")
      Map<String, IHopFileType> map = (Map<String, IHopFileType>) raw;
      HopProjectDocFileType type = HopProjectDocFileType.getInstance();
      map.put("html", type);
      map.put("htm", type);
      return true;
    } catch (Exception e) {
      LogChannel.UI.logError(
          "Unable to install Hop Web HTML documentation handler on the explorer perspective", e);
      return false;
    }
  }

  @Override
  public void filePainted(Tree tree, TreeItem treeItem, String path, String name) {
    if (!EnvironmentUtils.getInstance().isWeb()
        || tree == null
        || tree.isDisposed()
        || treeItem == null
        || !EdwDocsWebSupport.isHtmlPath(path)) {
      return;
    }
    tree.getDisplay()
        .asyncExec(
            () -> {
              if (treeItem.isDisposed()) {
                return;
              }
              Object data = treeItem.getData();
              if (data == null) {
                return;
              }
              try {
                Field folder = data.getClass().getField("folder");
                if (Boolean.TRUE.equals(folder.get(data))) {
                  return;
                }
                Field fileType = data.getClass().getField("fileType");
                IHopFileType current = (IHopFileType) fileType.get(data);
                if (current instanceof HopProjectDocFileType) {
                  return;
                }
                fileType.set(data, HopProjectDocFileType.getInstance());
              } catch (Exception ignored) {
                // Explorer tree-item layout is internal to Hop.
              }
            });
  }

  @Override
  public void fileSelected() {
    enableOpenInBrowserMenu();
  }

  static void enableOpenInBrowserMenu() {
    ExplorerPerspective explorer = ExplorerPerspective.getInstance();
    if (explorer == null || explorer.getMenuWidgets() == null) {
      return;
    }
    ExplorerFile selected = explorer.getSelectedFile();
    boolean enabled =
        selected != null && EdwDocsWebSupport.canOpenInBrowser(selected.getFilename());
    explorer.getMenuWidgets().enableMenuItem(ID_CONTEXT_MENU_OPEN_IN_BROWSER, enabled);
  }

  static Path siteRootOf(String path) {
    return EdwDocsWebSupport.findSiteRoot(path);
  }
}
