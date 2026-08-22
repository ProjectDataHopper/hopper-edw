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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;

/** Compares two execution map documents after refresh. */
public final class ExecutionMapDiffSupport {

  private ExecutionMapDiffSupport() {}

  @Getter
  public static final class DiffResult {
    private final List<String> addedNodes = new ArrayList<>();
    private final List<String> removedNodes = new ArrayList<>();
    private final int previousNodeCount;
    private final int currentNodeCount;
    private final int previousEdgeCount;
    private final int currentEdgeCount;

    public DiffResult(
        List<String> addedNodes,
        List<String> removedNodes,
        int previousNodeCount,
        int currentNodeCount,
        int previousEdgeCount,
        int currentEdgeCount) {
      this.addedNodes.addAll(addedNodes);
      this.removedNodes.addAll(removedNodes);
      this.previousNodeCount = previousNodeCount;
      this.currentNodeCount = currentNodeCount;
      this.previousEdgeCount = previousEdgeCount;
      this.currentEdgeCount = currentEdgeCount;
    }

    public boolean hasChanges() {
      return !addedNodes.isEmpty()
          || !removedNodes.isEmpty()
          || previousNodeCount != currentNodeCount
          || previousEdgeCount != currentEdgeCount;
    }

    public String summarize() {
      StringBuilder builder = new StringBuilder();
      builder.append("Nodes: ").append(previousNodeCount).append(" -> ").append(currentNodeCount);
      builder.append(System.lineSeparator());
      builder.append("Edges: ").append(previousEdgeCount).append(" -> ").append(currentEdgeCount);
      if (!addedNodes.isEmpty()) {
        builder.append(System.lineSeparator()).append("Added:");
        for (String added : addedNodes) {
          builder.append(System.lineSeparator()).append("  + ").append(added);
        }
      }
      if (!removedNodes.isEmpty()) {
        builder.append(System.lineSeparator()).append("Removed:");
        for (String removed : removedNodes) {
          builder.append(System.lineSeparator()).append("  - ").append(removed);
        }
      }
      return builder.toString();
    }
  }

  public static DiffResult compare(ExecutionMapDocument previous, ExecutionMapDocument current) {
    Set<String> previousKeys = nodeKeys(previous);
    Set<String> currentKeys = nodeKeys(current);
    List<String> added = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    for (String key : currentKeys) {
      if (!previousKeys.contains(key)) {
        added.add(key);
      }
    }
    for (String key : previousKeys) {
      if (!currentKeys.contains(key)) {
        removed.add(key);
      }
    }
    return new DiffResult(
        added,
        removed,
        previous != null ? previous.getNodesOrEmpty().size() : 0,
        current != null ? current.getNodesOrEmpty().size() : 0,
        previous != null ? previous.getEdgesOrEmpty().size() : 0,
        current != null ? current.getEdgesOrEmpty().size() : 0);
  }

  private static Set<String> nodeKeys(ExecutionMapDocument document) {
    Set<String> keys = new HashSet<>();
    if (document == null) {
      return keys;
    }
    for (ExecutionMapNode node : document.getNodesOrEmpty()) {
      if (node == null) {
        continue;
      }
      String key = nodeKey(node);
      if (!Utils.isEmpty(key)) {
        keys.add(key);
      }
    }
    return keys;
  }

  private static String nodeKey(ExecutionMapNode node) {
    if (!Utils.isEmpty(node.getPath())) {
      return node.getPath();
    }
    if (node.getNodeType() != null && !Utils.isEmpty(node.getName())) {
      return node.getNodeType().name() + ":" + node.getName();
    }
    return node.getId();
  }
}
