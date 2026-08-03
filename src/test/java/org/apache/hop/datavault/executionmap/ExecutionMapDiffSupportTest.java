/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.junit.jupiter.api.Test;

class ExecutionMapDiffSupportTest {

  @Test
  void compareDetectsAddedAndRemovedNodesByPath() {
    ExecutionMapDocument previous = new ExecutionMapDocument();
    ExecutionMapNode kept = node("workflow-a", ExecutionMapNodeType.WORKFLOW, "/tmp/a.hwf");
    ExecutionMapNode removed =
        node("dataset-old", ExecutionMapNodeType.SOURCE_DATASET, "dataset://crm/old");
    previous.getNodesOrEmpty().add(kept);
    previous.getNodesOrEmpty().add(removed);

    ExecutionMapDocument current = new ExecutionMapDocument();
    current.getNodesOrEmpty().add(kept);
    current
        .getNodesOrEmpty()
        .add(node("dataset-new", ExecutionMapNodeType.TARGET_DATASET, "dataset://crm/new"));

    ExecutionMapDiffSupport.DiffResult diff = ExecutionMapDiffSupport.compare(previous, current);

    assertTrue(diff.hasChanges());
    assertEquals(1, diff.getAddedNodes().size());
    assertEquals(1, diff.getRemovedNodes().size());
    assertTrue(diff.getAddedNodes().contains("dataset://crm/new"));
    assertTrue(diff.getRemovedNodes().contains("dataset://crm/old"));
    assertTrue(diff.summarize().contains("dataset://crm/new"));
  }

  @Test
  void compareReportsNoChangesForIdenticalMaps() {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document
        .getNodesOrEmpty()
        .add(node("root", ExecutionMapNodeType.ROOT_WORKFLOW, "/tmp/root.hwf"));

    ExecutionMapDiffSupport.DiffResult diff = ExecutionMapDiffSupport.compare(document, document);

    assertFalse(diff.hasChanges());
    assertTrue(diff.getAddedNodes().isEmpty());
    assertTrue(diff.getRemovedNodes().isEmpty());
  }

  private static ExecutionMapNode node(String name, ExecutionMapNodeType nodeType, String path) {
    ExecutionMapNode node = new ExecutionMapNode();
    node.setName(name);
    node.setNodeType(nodeType);
    node.setPath(path);
    return node;
  }
}
