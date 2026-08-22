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
import org.apache.hop.core.util.Utils;
import org.eclipse.swt.widgets.TreeItem;

/** Helpers for interpreting multi-selection in the data catalog perspective tree. */
public final class DataCatalogSelectionSupport {

  private DataCatalogSelectionSupport() {}

  public static List<DataCatalogTreeNode> collectRecordNodes(TreeItem[] items) {
    if (items == null) {
      return List.of();
    }
    List<DataCatalogTreeNode> nodes = new ArrayList<>(items.length);
    for (TreeItem item : items) {
      if (item == null) {
        continue;
      }
      Object data = item.getData();
      if (data instanceof DataCatalogTreeNode node) {
        nodes.add(node);
      }
    }
    return collectRecordNodesFromTreeNodes(nodes);
  }

  static List<DataCatalogTreeNode> collectRecordNodesFromTreeNodes(
      List<DataCatalogTreeNode> nodes) {
    List<DataCatalogTreeNode> recordNodes = new ArrayList<>();
    if (nodes == null) {
      return recordNodes;
    }
    for (DataCatalogTreeNode node : nodes) {
      if (node != null
          && (node.getType() == DataCatalogTreeNode.Type.RECORD
              || node.getType() == DataCatalogTreeNode.Type.VERSION_RECORD)) {
        recordNodes.add(node);
      }
    }
    return recordNodes;
  }

  public static String firstCatalogConnectionName(TreeItem[] items) {
    if (items == null) {
      return null;
    }
    List<DataCatalogTreeNode> nodes = new ArrayList<>(items.length);
    for (TreeItem item : items) {
      if (item == null) {
        continue;
      }
      Object data = item.getData();
      if (data instanceof DataCatalogTreeNode node) {
        nodes.add(node);
      }
    }
    return firstCatalogConnectionNameFromTreeNodes(nodes);
  }

  static String firstCatalogConnectionNameFromTreeNodes(List<DataCatalogTreeNode> nodes) {
    if (nodes == null) {
      return null;
    }
    for (DataCatalogTreeNode node : nodes) {
      if (node != null && !Utils.isEmpty(node.getCatalogConnectionName())) {
        return node.getCatalogConnectionName();
      }
    }
    return null;
  }

  public static List<String> collectRecordKeys(TreeItem[] items) {
    List<String> recordKeys = new ArrayList<>();
    for (DataCatalogTreeNode node : collectRecordNodes(items)) {
      if (node.getRecordKey() != null) {
        recordKeys.add(node.getRecordKey().toString());
      }
    }
    return recordKeys;
  }
}
