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
import org.apache.hop.core.util.Utils;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.IExplorerFilePaintListener;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.hopper.edw.datavault.hopgui.EdwDocsWebSupport;

/**
 * On Hop Web, routes generated project-documentation HTML to {@link HopProjectDocFileType} so the
 * explorer tab uses a RAP handler URL instead of {@code Browser.setText()}.
 */
public final class ProjectDocumentationExplorerSupport implements IExplorerFilePaintListener {

  private static final ProjectDocumentationExplorerSupport INSTANCE =
      new ProjectDocumentationExplorerSupport();

  private ProjectDocumentationExplorerSupport() {}

  public static ProjectDocumentationExplorerSupport getInstance() {
    return INSTANCE;
  }

  public static void register(ExplorerPerspective explorer) {
    if (explorer == null || !EnvironmentUtils.getInstance().isWeb()) {
      return;
    }
    IExplorerFilePaintListener listener = getInstance();
    if (!explorer.getFilePaintListeners().contains(listener)) {
      explorer.getFilePaintListeners().add(listener);
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
              Path siteRoot = siteRootOf(path);
              if (siteRoot == null) {
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

  static Path siteRootOf(String path) {
    if (Utils.isEmpty(path)) {
      return null;
    }
    try {
      return EdwDocsWebSupport.findSiteRoot(Path.of(path));
    } catch (Exception ignored) {
      return null;
    }
  }
}
