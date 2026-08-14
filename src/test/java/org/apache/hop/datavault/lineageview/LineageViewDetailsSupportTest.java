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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer;
import org.apache.hop.datavault.lineageview.backend.HopExportFacet;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;
import org.apache.hop.datavault.lineageview.backend.LineageGraphLayer;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.junit.jupiter.api.Test;

class LineageViewDetailsSupportTest {

  @Test
  void formatsSeedNodeWithoutCallingLoadTimes() {
    LineageNode node =
        LineageNode.builder()
            .id("dataset:Vault:f_orders")
            .kind(LineageNodeKind.DATASET)
            .layer(LineageGraphLayer.DM)
            .namespace("Vault")
            .name("f_orders")
            .lastExportedAt("2026-08-14T10:00:00Z")
            .hopExport(
                HopExportFacet.builder()
                    .modelLayer("DM")
                    .modelName("retail-pos")
                    .logicalName("f_orders")
                    .modelFilename("${PROJECT_HOME}/models/retail-pos.hdm")
                    .build())
            .schemaFieldNames(List.of("order_amount", "customer_hk"))
            .build();
    LineageGraph graph =
        LineageGraph.builder().seedNodeId("dataset:Vault:f_orders").nodes(List.of(node)).build();

    String text = LineageViewDetailsSupport.format(node, graph);
    assertTrue(text.startsWith("# f_orders"));
    assertTrue(text.contains("seed"));
    assertTrue(text.contains("## Identity"));
    assertTrue(text.contains("## Hop identity"));
    assertTrue(text.contains("## Fields"));
    assertTrue(text.contains("`2026-08-14T10:00:00Z`"));
    assertTrue(text.contains("`order_amount`"));
    assertFalse(text.toLowerCase().contains("durationms"));
    MarkdownStyleRenderer.RenderedMarkdown rendered = MarkdownStyleRenderer.render(text);
    assertTrue(rendered.displayText().contains("f_orders"));
  }

  @Test
  void emptySelectionMessage() {
    assertTrue(LineageViewDetailsSupport.format(null, null).contains("Select a node"));
  }
}
