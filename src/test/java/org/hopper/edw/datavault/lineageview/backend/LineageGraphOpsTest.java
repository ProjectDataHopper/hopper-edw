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
package org.hopper.edw.datavault.lineageview.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.Test;

class LineageGraphOpsTest {

  private static final String SRC = "dataset:ns:src";
  private static final String JOB_SAT = "job:ns:dv/m/sat";
  private static final String SAT = "dataset:ns:sat";
  private static final String JOB_FACT = "job:ns:dm/m/fact";
  private static final String FACT = "dataset:ns:fact";
  private static final String DOWN = "dataset:ns:downstream";

  @Test
  void missingSeedThrows() {
    LineageGraph graph =
        LineageGraph.builder()
            .nodes(List.of(node(FACT, LineageNodeKind.DATASET, LineageGraphLayer.DM)))
            .build();
    HopException error =
        assertThrows(
            HopException.class,
            () -> LineageGraphOps.apply(graph, LineageQuery.builder().depth(6).build()));
    assertTrue(error.getMessage().contains(ILineageQueryService.SEED_NOT_FOUND));
  }

  @Test
  void clipUpstreamKeepsAncestorsOnly() throws Exception {
    LineageGraph applied =
        LineageGraphOps.apply(
            chain(), LineageQuery.builder().depth(10).direction(LineageDirection.UPSTREAM).build());
    Set<String> ids = ids(applied);
    assertTrue(ids.containsAll(List.of(SRC, JOB_SAT, SAT, JOB_FACT, FACT)));
    assertFalse(ids.contains(DOWN));
  }

  @Test
  void clipDownstreamKeepsDescendantsOnly() throws Exception {
    LineageGraph applied =
        LineageGraphOps.apply(
            chain(),
            LineageQuery.builder().depth(10).direction(LineageDirection.DOWNSTREAM).build());
    Set<String> ids = ids(applied);
    assertEquals(Set.of(FACT, DOWN), ids);
  }

  @Test
  void depthDropsDistantNodes() throws Exception {
    LineageGraph applied =
        LineageGraphOps.apply(
            chain(), LineageQuery.builder().depth(2).direction(LineageDirection.UPSTREAM).build());
    Set<String> ids = ids(applied);
    assertTrue(ids.containsAll(Set.of(FACT, JOB_FACT, SAT)));
    assertFalse(ids.contains(SRC));
    assertTrue(
        applied.getWarnings().stream()
            .anyMatch(w -> LineageWarning.DEPTH_CLIPPED.equals(w.getCode())));
  }

  @Test
  void hideJobsConcatenatesDatasetEdges() throws Exception {
    LineageGraph applied =
        LineageGraphOps.apply(
            chain(),
            LineageQuery.builder()
                .depth(10)
                .direction(LineageDirection.UPSTREAM)
                .includeJobs(false)
                .build());
    Set<String> ids = ids(applied);
    assertEquals(Set.of(SRC, SAT, FACT), ids);
    assertTrue(
        applied.getEdgesOrEmpty().stream()
            .anyMatch(
                e ->
                    SAT.equals(e.getFromNodeId())
                        && FACT.equals(e.getToNodeId())
                        && JOB_FACT.equals(e.getViaJobId())));
  }

  @Test
  void layerFilterDropsOtherLayers() throws Exception {
    LineageGraph applied =
        LineageGraphOps.apply(
            chain(),
            LineageQuery.builder()
                .depth(10)
                .direction(LineageDirection.UPSTREAM)
                .layerFilters(List.of(LineageGraphLayer.DM))
                .build());
    Set<String> ids = ids(applied);
    assertTrue(ids.contains(FACT));
    assertTrue(ids.contains(JOB_FACT));
    assertFalse(ids.contains(SRC));
    assertTrue(
        applied.getWarnings().stream()
            .anyMatch(w -> LineageWarning.LAYER_DROPPED.equals(w.getCode())));
  }

  private static LineageGraph chain() {
    return LineageGraph.builder()
        .seedNodeId(FACT)
        .nodes(
            List.of(
                node(SRC, LineageNodeKind.DATASET, LineageGraphLayer.SOURCE),
                node(JOB_SAT, LineageNodeKind.JOB, LineageGraphLayer.DV),
                node(SAT, LineageNodeKind.DATASET, LineageGraphLayer.DV),
                node(JOB_FACT, LineageNodeKind.JOB, LineageGraphLayer.DM),
                node(FACT, LineageNodeKind.DATASET, LineageGraphLayer.DM),
                node(DOWN, LineageNodeKind.DATASET, LineageGraphLayer.DM)))
        .edges(
            List.of(
                edge(SRC, JOB_SAT),
                edge(JOB_SAT, SAT),
                edge(SAT, JOB_FACT),
                edge(JOB_FACT, FACT),
                edge(FACT, DOWN)))
        .build();
  }

  private static LineageNode node(String id, LineageNodeKind kind, LineageGraphLayer layer) {
    OpenLineageRef ref = OpenLineageRef.fromNodeId(id);
    return LineageNode.builder()
        .id(id)
        .kind(kind)
        .namespace(ref.getNamespace())
        .name(ref.getName())
        .layer(layer)
        .build();
  }

  private static LineageEdge edge(String from, String to) {
    return LineageEdge.builder().fromNodeId(from).toNodeId(to).build();
  }

  private static Set<String> ids(LineageGraph graph) {
    return graph.getNodesOrEmpty().stream().map(LineageNode::getId).collect(Collectors.toSet());
  }
}
