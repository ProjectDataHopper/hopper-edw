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
package org.apache.hop.datavault.hopgui.file.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.junit.jupiter.api.Test;

class LineageViewCanvasBannerTest {

  @Test
  void errorWinsOverEmptyAndLoading() {
    assertEquals(
        "backend down",
        LineageViewCanvasBanner.text("backend down", null, true, "Loading…", "empty"));
    assertTrue(LineageViewCanvasBanner.error("backend down"));
    assertFalse(LineageViewCanvasBanner.error(""));
  }

  @Test
  void loadingWhenGraphMissing() {
    assertEquals("Loading…", LineageViewCanvasBanner.text(null, null, true, "Loading…", "empty"));
  }

  @Test
  void emptyWhenGraphMissingAndIdle() {
    assertEquals("empty", LineageViewCanvasBanner.text(null, null, false, "Loading…", "empty"));
  }

  @Test
  void noBannerWhenGraphHasNodes() {
    LineageGraph graph =
        LineageGraph.builder()
            .nodes(
                List.of(
                    LineageNode.builder()
                        .id("dataset:ns:a")
                        .kind(LineageNodeKind.DATASET)
                        .name("a")
                        .build()))
            .build();
    assertNull(LineageViewCanvasBanner.text(null, graph, false, "Loading…", "empty"));
    assertNull(LineageViewCanvasBanner.text(null, graph, true, "Loading…", "empty"));
  }
}
