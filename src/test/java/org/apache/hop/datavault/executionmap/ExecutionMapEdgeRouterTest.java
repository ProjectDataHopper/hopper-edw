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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.junit.jupiter.api.Test;

class ExecutionMapEdgeRouterTest {

  @Test
  void hubSpokeRouteAvoidsIntermediateNodes() {
    Bounds hub =
        new Bounds(64, 80, ExecutionMapMetrics.NODE_WIDTH, ExecutionMapMetrics.NODE_HEIGHT);
    Bounds dm1 =
        new Bounds(304, 64, ExecutionMapMetrics.NODE_WIDTH, ExecutionMapMetrics.NODE_HEIGHT);
    Bounds dm2 =
        new Bounds(304, 144, ExecutionMapMetrics.NODE_WIDTH, ExecutionMapMetrics.NODE_HEIGHT);
    Bounds dv =
        new Bounds(304, 448, ExecutionMapMetrics.NODE_WIDTH, ExecutionMapMetrics.NODE_HEIGHT);

    List<Bounds> obstacles = new ArrayList<>(List.of(hub, dm1, dm2, dv));
    int[] polyline =
        ExecutionMapEdgeRouter.routeOrthogonal(
            new ExecutionMapEdgeRouter.RouteRequest(
                hub, dv, ExecutionMapEdgeRouter.obstaclesForEdge(obstacles, hub, dv), 0, true));

    assertTrue(polyline.length >= 8, "route should contain bus segments");
    assertFalse(
        ExecutionMapEdgeRouter.intersectsObstacles(polyline, List.of(dm1, dm2)),
        "route should not pass through intermediate nodes");
  }

  @Test
  void sameColumnRouteUsesRightSideBus() {
    Bounds bv =
        new Bounds(304, 544, ExecutionMapMetrics.NODE_WIDTH, ExecutionMapMetrics.NODE_HEIGHT);
    Bounds dv =
        new Bounds(304, 448, ExecutionMapMetrics.NODE_WIDTH, ExecutionMapMetrics.NODE_HEIGHT);

    int[] polyline =
        ExecutionMapEdgeRouter.routeOrthogonal(
            new ExecutionMapEdgeRouter.RouteRequest(
                bv, dv, ExecutionMapEdgeRouter.obstaclesForEdge(List.of(bv, dv), bv, dv), 0, true));

    assertTrue(polyline.length >= 8);
    int busX = polyline[2];
    assertTrue(
        busX > bv.x() + bv.width(), "same-column links should route outside the node column");
  }
}
