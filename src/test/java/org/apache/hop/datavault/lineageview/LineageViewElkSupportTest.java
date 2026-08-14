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
package org.apache.hop.datavault.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.datavault.layout.ElkLayoutBox;
import org.apache.hop.datavault.lineageview.backend.LineageEdge;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.junit.jupiter.api.Test;

class LineageViewElkSupportTest {

  @Test
  void layoutsTwoConnectedNodes() throws Exception {
    LineageGraph graph =
        LineageGraph.builder()
            .seedNodeId("dataset:ns:b")
            .nodes(
                List.of(
                    LineageNode.builder()
                        .id("dataset:ns:a")
                        .kind(LineageNodeKind.DATASET)
                        .name("a")
                        .build(),
                    LineageNode.builder()
                        .id("dataset:ns:b")
                        .kind(LineageNodeKind.DATASET)
                        .name("b")
                        .build()))
            .edges(
                List.of(
                    LineageEdge.builder()
                        .fromNodeId("dataset:ns:a")
                        .toNodeId("dataset:ns:b")
                        .build()))
            .build();
    Map<String, ElkLayoutBox> boxes = LineageViewElkSupport.layout(graph);
    assertEquals(2, boxes.size());
    assertTrue(boxes.get("dataset:ns:a").x() < boxes.get("dataset:ns:b").x());
  }
}
