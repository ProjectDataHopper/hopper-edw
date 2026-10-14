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
package org.hopper.edw.datavault.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;

class ElkGraphLayoutSourceModelTest {

  @Test
  void sourceGraphIncludesQueriesAndUsesDrawnTableHeight() {
    SourceModel model = new SourceModel();
    SourceTable table = new SourceTable("load_run");
    table.setLocation(new Point(0, 0));
    table.setDrawnBoxWidth(180);
    table.setDrawnBoxHeight(200);
    SourceQuery query = new SourceQuery("pipeline");
    query.setLocation(new Point(40, 40));
    model.getTables().add(table);
    model.getQueries().add(query);

    SourceRelationship kept =
        relationship("load_run", SourceEndpointKind.TABLE, "pipeline", SourceEndpointKind.QUERY);
    SourceRelationship dangling =
        relationship("load_run", SourceEndpointKind.TABLE, "missing", SourceEndpointKind.QUERY);
    model.getRelationships().add(kept);
    model.getRelationships().add(dangling);

    ElkGraphLayout graph = ElkGraphLayout.fromSourceModel(model);

    assertEquals(2, graph.getNodes().size());
    ElkLayoutNode loadRun = node(graph, "TABLE:load_run");
    assertEquals(200.0, loadRun.getHeight());
    assertEquals(180.0, loadRun.getWidth());
    assertTrue(node(graph, "QUERY:pipeline") != null);
    assertEquals(1, graph.getEdges().size());
    assertEquals("TABLE:load_run", graph.getEdges().get(0).getFromId());
    assertEquals("QUERY:pipeline", graph.getEdges().get(0).getToId());
    assertFalse(
        graph.getEdges().stream()
            .anyMatch(
                edge ->
                    edge.getFromId().contains("missing") || edge.getToId().contains("missing")));
  }

  @Test
  void stressLayoutAssignsFinitePositions() throws HopException {
    SourceModel model = new SourceModel();
    SourceTable a = new SourceTable("a");
    a.setLocation(new Point(0, 0));
    SourceTable b = new SourceTable("b");
    b.setLocation(new Point(0, 0));
    SourceTable c = new SourceTable("c");
    c.setLocation(new Point(0, 0));
    model.getTables().add(a);
    model.getTables().add(b);
    model.getTables().add(c);
    model
        .getRelationships()
        .add(relationship("a", SourceEndpointKind.TABLE, "b", SourceEndpointKind.TABLE));
    model
        .getRelationships()
        .add(relationship("b", SourceEndpointKind.TABLE, "c", SourceEndpointKind.TABLE));
    model
        .getRelationships()
        .add(relationship("c", SourceEndpointKind.TABLE, "a", SourceEndpointKind.TABLE));

    ElkLayout layout = ElkLayout.createDefault();
    layout.setAlgorithm(ElkLayoutAlgorithm.STRESS);
    var boxes = ElkGraphLayout.fromSourceModel(model).layoutToBoxes(layout);

    assertEquals(3, boxes.size());
    for (ElkLayoutBox box : boxes.values()) {
      assertTrue(Double.isFinite(box.x()));
      assertTrue(Double.isFinite(box.y()));
    }
    assertSeparated(boxes.get("TABLE:a"), boxes.get("TABLE:b"), layout.getSpacingWithinLayer());
  }

  private static void assertSeparated(ElkLayoutBox a, ElkLayoutBox b, int gap) {
    boolean clear =
        a.x() + a.width() + gap <= b.x()
            || b.x() + b.width() + gap <= a.x()
            || a.y() + a.height() + gap <= b.y()
            || b.y() + b.height() + gap <= a.y();
    assertTrue(clear);
  }

  private static ElkLayoutNode node(ElkGraphLayout graph, String id) {
    return graph.getNodes().stream()
        .filter(node -> id.equals(node.getId()))
        .findFirst()
        .orElse(null);
  }

  private static SourceRelationship relationship(
      String parent, SourceEndpointKind parentKind, String child, SourceEndpointKind childKind) {
    SourceRelationship relationship = new SourceRelationship();
    relationship.setName(parent + "_" + child);
    relationship.setParentEndpointKind(parentKind);
    relationship.setParentTableName(parent);
    relationship.setChildEndpointKind(childKind);
    relationship.setChildTableName(child);
    return relationship;
  }
}
