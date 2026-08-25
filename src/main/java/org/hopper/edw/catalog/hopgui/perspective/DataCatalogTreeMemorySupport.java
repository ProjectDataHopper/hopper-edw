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
package org.hopper.edw.catalog.hopgui.perspective;

import java.util.Set;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.widget.TreeMemory;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/** Applies and records expand/collapse state for the data catalog perspective tree. */
public final class DataCatalogTreeMemorySupport {

  private DataCatalogTreeMemorySupport() {}

  public static void applyExpandedState(
      Tree tree, String treeKey, boolean groupByNamespace, Set<String> seededPaths) {
    if (tree == null || tree.isDisposed()) {
      return;
    }
    for (TreeItem catalogItem : tree.getItems()) {
      if (catalogItem == null || catalogItem.isDisposed() || !isCatalogItem(catalogItem)) {
        continue;
      }
      applyItemExpanded(catalogItem, treeKey, seededPaths, true);
      TreeItem workingRoot = DataCatalogTreeNavigation.findWorkingRoot(catalogItem);
      if (workingRoot != null) {
        applyItemExpanded(workingRoot, treeKey, seededPaths, true);
      }
      if (groupByNamespace) {
        TreeItem namespaceParent = workingRoot != null ? workingRoot : catalogItem;
        for (TreeItem child : namespaceParent.getItems()) {
          if (child != null && !child.isDisposed() && isNamespaceItem(child)) {
            applyItemExpanded(child, treeKey, seededPaths, true);
          }
        }
      }
    }
  }

  public static void recordExpandedState(
      TreeItem item, String treeKey, boolean expanded, Set<String> seededPaths) {
    if (item == null || item.isDisposed()) {
      return;
    }
    String[] path = ConstUi.getTreeStrings(item);
    TreeMemory.getInstance().storeExpanded(treeKey, path, expanded);
    seededPaths.add(pathKey(path));
  }

  private static void applyItemExpanded(
      TreeItem item, String treeKey, Set<String> seededPaths, boolean defaultExpanded) {
    String[] path = ConstUi.getTreeStrings(item);
    item.setExpanded(resolveExpanded(treeKey, path, seededPaths, defaultExpanded));
  }

  static boolean resolveExpanded(
      String treeKey, String[] path, Set<String> seededPaths, boolean defaultExpanded) {
    if (!TreeMemory.getInstance().isExpanded(treeKey, path)
        && defaultExpanded
        && seededPaths.add(pathKey(path))) {
      TreeMemory.getInstance().storeExpanded(treeKey, path, true);
    }
    return TreeMemory.getInstance().isExpanded(treeKey, path);
  }

  private static String pathKey(String[] path) {
    return String.join("\0", path);
  }

  private static boolean isCatalogItem(TreeItem item) {
    Object data = item.getData();
    return data instanceof DataCatalogTreeNode node
        && node.getType() == DataCatalogTreeNode.Type.CATALOG;
  }

  private static boolean isNamespaceItem(TreeItem item) {
    Object data = item.getData();
    return data instanceof DataCatalogTreeNode node
        && node.getType() == DataCatalogTreeNode.Type.NAMESPACE;
  }
}
