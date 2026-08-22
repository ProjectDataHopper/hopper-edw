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
package org.apache.hop.catalog.hopgui.perspective;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.ui.core.widget.TreeMemory;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/** Tree navigation helpers for the data catalog perspective. */
public final class DataCatalogTreeNavigation {

  private DataCatalogTreeNavigation() {}

  public static List<TreeItem> findRecordItems(TreeItem parent, List<String> recordKeys) {
    List<TreeItem> matches = new ArrayList<>();
    if (parent == null || parent.isDisposed() || recordKeys == null || recordKeys.isEmpty()) {
      return matches;
    }
    collectRecordItems(parent, recordKeys, matches);
    return matches;
  }

  /**
   * Expands every ancestor of {@code item} so a programmatic selection is visible. When {@code
   * treeMemoryKey} is provided, expanded state is persisted like a user expand.
   */
  public static void expandAncestors(TreeItem item, String treeMemoryKey, Set<String> seededPaths) {
    if (item == null || item.isDisposed()) {
      return;
    }
    TreeItem parent = item.getParentItem();
    while (parent != null && !parent.isDisposed()) {
      if (!parent.getExpanded()) {
        parent.setExpanded(true);
        if (treeMemoryKey != null) {
          if (seededPaths != null) {
            DataCatalogTreeMemorySupport.recordExpandedState(
                parent, treeMemoryKey, true, seededPaths);
          } else {
            TreeMemory.getInstance().storeExpanded(treeMemoryKey, parent, true);
          }
        }
      }
      parent = parent.getParentItem();
    }
  }

  private static void collectRecordItems(
      TreeItem parent, List<String> recordKeys, List<TreeItem> matches) {
    for (TreeItem child : parent.getItems()) {
      if (child == null || child.isDisposed()) {
        continue;
      }
      Object data = child.getData();
      if (data instanceof DataCatalogTreeNode node
          && node.getType() == DataCatalogTreeNode.Type.RECORD
          && node.getRecordKey() != null
          && recordKeys.contains(node.getRecordKey().toString())) {
        matches.add(child);
      }
      if (child.getItemCount() > 0) {
        collectRecordItems(child, recordKeys, matches);
      }
    }
  }

  /**
   * Expands or collapses folder nodes in the catalog tree. When grouped by namespace, namespace
   * folders are updated (catalog connections stay expanded). Otherwise top-level catalog connection
   * nodes are updated.
   */
  public static void setFolderExpandedState(
      Tree tree, String treeMemoryKey, boolean groupByNamespace, boolean expanded) {
    if (tree == null || tree.isDisposed()) {
      return;
    }
    for (TreeItem catalogItem : tree.getItems()) {
      if (catalogItem == null || catalogItem.isDisposed()) {
        continue;
      }
      if (groupByNamespace) {
        if (!catalogItem.getExpanded()) {
          catalogItem.setExpanded(true);
          TreeMemory.getInstance().storeExpanded(treeMemoryKey, catalogItem, true);
        }
        for (TreeItem child : catalogItem.getItems()) {
          if (child == null || child.isDisposed() || !isNamespaceItem(child)) {
            continue;
          }
          child.setExpanded(expanded);
          TreeMemory.getInstance().storeExpanded(treeMemoryKey, child, expanded);
        }
      } else if (isCatalogItem(catalogItem)) {
        catalogItem.setExpanded(expanded);
        TreeMemory.getInstance().storeExpanded(treeMemoryKey, catalogItem, expanded);
      }
    }
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
