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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.apache.hop.datavault.lineageview.backend.HopExportFacet;
import org.apache.hop.datavault.lineageview.backend.HopOpsFacet;
import org.apache.hop.datavault.lineageview.backend.LineageGraph;
import org.apache.hop.datavault.lineageview.backend.LineageNode;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.apache.hop.datavault.metrics.LoadRunDurationRun;
import org.apache.hop.datavault.metrics.LoadRunDurationSnapshot;
import org.junit.jupiter.api.Test;

class LineageViewOpsOverlayTest {

  @Test
  void mapsLayerToLowercaseOpsType() {
    assertEquals("dv", LineageViewOpsOverlay.opsTypeForLayer("DV"));
    assertEquals("bv", LineageViewOpsOverlay.opsTypeForLayer("BV"));
    assertEquals("dm", LineageViewOpsOverlay.opsTypeForLayer("DM"));
    assertNull(LineageViewOpsOverlay.opsTypeForLayer("CROSS"));
  }

  @Test
  void parsesJobNameWhenHopExportMissing() {
    LineageViewOpsOverlay.OpsIdentity identity =
        LineageViewOpsOverlay.parseJobName("dm/retail-f-order-lines/f_order_lines");
    assertNotNull(identity);
    assertEquals("retail-f-order-lines", identity.modelName());
    assertEquals("dm", identity.opsType());
    assertEquals("f_order_lines", identity.logicalName());
  }

  @Test
  void prefersLogicalThenPhysicalAndMarksSlowLastRun() {
    LineageNode node =
        LineageNode.builder()
            .id("job:ns:dm/retail-f-order-lines/f_order_lines")
            .kind(LineageNodeKind.JOB)
            .name("dm/retail-f-order-lines/f_order_lines")
            .hopExport(
                HopExportFacet.builder()
                    .modelLayer("DM")
                    .modelName("retail-f-order-lines")
                    .logicalName("f_order_lines")
                    .physicalTableName("f_order_lines")
                    .build())
            .build();
    LineageGraph graph = LineageGraph.builder().nodes(List.of(node)).build();

    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.LOADED)
            .tableNames(List.of("f_order_lines"))
            .runs(
                List.of(
                    LoadRunDurationRun.builder()
                        .runId("r1")
                        .finishedAt(new Date(1))
                        .success(true)
                        .build(),
                    LoadRunDurationRun.builder()
                        .runId("r2")
                        .finishedAt(new Date(2))
                        .success(true)
                        .build()))
            .durationsByElement(java.util.Map.of("f_order_lines", new long[] {2_000L, 120_000L}))
            .build();

    LineageViewOpsOverlay overlay =
        LineageViewOpsOverlay.load(
            graph,
            (model, type, tables) -> {
              assertEquals("retail-f-order-lines", model);
              assertEquals("dm", type);
              assertTrue(tables.contains("f_order_lines"));
              assertFalse(
                  tables.stream()
                      .anyMatch(name -> name.contains(".") && !name.equals("f_order_lines")));
              return snapshot;
            });

    LineageViewOpsBadge badge = overlay.badgeFor(node);
    assertNotNull(badge);
    assertEquals(120_000L, badge.lastMs());
    assertEquals(2_000L, badge.averageMs());
    assertTrue(badge.slow());
    assertFalse(badge.stale());
    assertTrue(badge.label().contains("2m"));
    assertTrue(badge.tooltip().contains("OPS"));
    assertFalse(badge.tooltip().toLowerCase().contains("durationms"));
  }

  @Test
  void fallsBackToHopOpsWhenOpsHasNoRow() {
    LineageNode node =
        LineageNode.builder()
            .id("job:ns:dv/crm/hub_customer")
            .kind(LineageNodeKind.JOB)
            .name("dv/crm/hub_customer")
            .hopExport(
                HopExportFacet.builder()
                    .modelLayer("DV")
                    .modelName("crm")
                    .logicalName("hub_customer")
                    .build())
            .hopOps(HopOpsFacet.builder().durationMs(45_000L).build())
            .build();
    LineageGraph graph = LineageGraph.builder().nodes(List.of(node)).build();
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.LOADED)
            .tableNames(List.of("hub_customer"))
            .runs(
                List.of(
                    LoadRunDurationRun.builder()
                        .runId("r1")
                        .finishedAt(new Date())
                        .success(true)
                        .build()))
            .durationsByElement(java.util.Map.of("hub_customer", new long[] {0L}))
            .build();

    LineageViewOpsOverlay overlay = LineageViewOpsOverlay.load(graph, (m, t, tables) -> snapshot);
    LineageViewOpsBadge badge = overlay.badgeFor(node);
    assertNotNull(badge);
    assertTrue(badge.stale());
    assertEquals(45_000L, badge.lastMs());
    assertTrue(badge.label().contains("export"));
  }

  @Test
  void suppressesAllBadgesWhenOpsDatabaseMissing() {
    LineageNode node =
        LineageNode.builder()
            .id("job:ns:dm/x/y")
            .kind(LineageNodeKind.JOB)
            .name("dm/x/y")
            .hopOps(HopOpsFacet.builder().durationMs(9_000L).build())
            .build();
    LineageGraph graph = LineageGraph.builder().nodes(List.of(node)).build();
    LineageViewOpsOverlay overlay =
        LineageViewOpsOverlay.load(
            graph,
            (m, t, tables) ->
                LoadRunDurationSnapshot.builder()
                    .status(LoadRunDurationSnapshot.Status.NO_DATABASE)
                    .build());
    assertTrue(overlay.isSuppressed());
    assertEquals("OPS unavailable", overlay.getStatusNote());
    assertNull(overlay.badgeFor(node));
  }

  @Test
  void datasetWithoutHopExportIsNotAnOpsSubject() {
    LineageNode dataset =
        LineageNode.builder()
            .id("dataset:retail-dataset:public.f_order_lines")
            .kind(LineageNodeKind.DATASET)
            .name("public.f_order_lines")
            .build();
    assertNull(LineageViewOpsOverlay.identityOf(dataset));
    LineageViewOpsOverlay overlay =
        LineageViewOpsOverlay.load(
            LineageGraph.builder().nodes(List.of(dataset)).build(),
            (m, t, tables) -> {
              throw new AssertionError("should not query OPS for a nameless dataset");
            });
    assertTrue(overlay.getSnapshots().isEmpty());
  }
}
