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
package org.apache.hop.datavault.executionmap;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Tracks the focused parent node for 2-level drill-down navigation. */
@Getter
public class ExecutionMapFocusContext {

  private final List<String> focusPath = new ArrayList<>();

  public ExecutionMapFocusContext() {}

  public ExecutionMapFocusContext(String focusNodeId) {
    if (!Utils.isEmpty(focusNodeId)) {
      focusPath.add(focusNodeId);
    }
  }

  /** Null means root focus (implicit root workflow/pipeline node). */
  public String getFocusNodeId() {
    return focusPath.isEmpty() ? null : focusPath.get(focusPath.size() - 1);
  }

  public void setFocusNodeId(String focusNodeId) {
    focusPath.clear();
    if (!Utils.isEmpty(focusNodeId)) {
      focusPath.add(focusNodeId);
    }
  }

  public ExecutionMapNode resolveFocusNode(ExecutionMapDocument document) {
    if (document == null) {
      return null;
    }
    if (!focusPath.isEmpty()) {
      ExecutionMapNode focused = document.findNodeById(getFocusNodeId());
      if (focused != null) {
        return focused;
      }
    }
    return findRootNode(document);
  }

  public List<ExecutionMapNode> getBreadcrumb(ExecutionMapDocument document) {
    List<ExecutionMapNode> breadcrumb = new ArrayList<>();
    if (document == null) {
      return breadcrumb;
    }
    ExecutionMapNode root = findRootNode(document);
    if (root != null) {
      breadcrumb.add(root);
    }
    for (String nodeId : focusPath) {
      ExecutionMapNode node = document.findNodeById(nodeId);
      if (node != null) {
        breadcrumb.add(node);
      }
    }
    return breadcrumb;
  }

  public boolean canDrillInto(ExecutionMapNode node, ExecutionMapDocument document) {
    if (node == null || document == null || Utils.isEmpty(node.getId())) {
      return false;
    }
    return !ExecutionMapViewFilter.directChildNodeIds(document, node.getId()).isEmpty();
  }

  public void drillInto(String nodeId) {
    if (!Utils.isEmpty(nodeId)) {
      focusPath.add(nodeId);
    }
  }

  public void navigateTo(String nodeId) {
    navigateTo(nodeId, null);
  }

  public void navigateTo(String nodeId, ExecutionMapDocument document) {
    if (Utils.isEmpty(nodeId)) {
      focusPath.clear();
      return;
    }
    if (document != null) {
      ExecutionMapNode root = findRootNode(document);
      if (root != null && nodeId.equals(root.getId())) {
        focusPath.clear();
        return;
      }
    }
    int index = focusPath.indexOf(nodeId);
    if (index >= 0) {
      focusPath.subList(index + 1, focusPath.size()).clear();
      return;
    }
    focusPath.clear();
    focusPath.add(nodeId);
  }

  public void navigateToRoot() {
    focusPath.clear();
  }

  public static ExecutionMapNode findRootNode(ExecutionMapDocument document) {
    if (document == null) {
      return null;
    }
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null || node.getNodeType() == null) {
        continue;
      }
      if (node.getNodeType() == ExecutionMapNodeType.ROOT_WORKFLOW
          || node.getNodeType() == ExecutionMapNodeType.ROOT_PIPELINE) {
        return node;
      }
    }
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node != null && Utils.isEmpty(node.getParentNodeId())) {
        return node;
      }
    }
    return document.getNodesOrEmpty().isEmpty() ? null : document.getNodesOrEmpty().get(0);
  }
}
