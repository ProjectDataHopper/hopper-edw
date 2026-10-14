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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;

class SourceModelBrowseLayoutTest {

  @Test
  void focusAndFourNeighborsStayDisjointInNameOrder() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("hub"));
    model.getTables().add(table("a"));
    model.getTables().add(table("b"));
    model.getTables().add(table("c"));
    model.getTables().add(table("d"));
    model.getRelationships().add(rel("hub", "a"));
    model.getRelationships().add(rel("hub", "b"));
    model.getRelationships().add(rel("hub", "c"));
    model.getRelationships().add(rel("hub", "d"));

    SourceModelBrowseLayout.Result first =
        SourceModelBrowseLayout.layout(model, SourceEndpointKind.TABLE, "hub", 1);
    SourceModelBrowseLayout.Result second =
        SourceModelBrowseLayout.layout(model, SourceEndpointKind.TABLE, "hub", 1);

    assertEquals(5, first.nodes().size());
    SourceModelBrowseLayout.Node focus = first.focus();
    assertEquals(0, focus.ring());
    double focusDistance = Math.hypot(focus.centerX(), focus.centerY());
    for (SourceModelBrowseLayout.Node node : first.nodes().values()) {
      if (node == focus) {
        continue;
      }
      assertTrue(Math.hypot(node.centerX(), node.centerY()) > focusDistance);
      assertTrue(separated(focus, node));
    }
    List<String> names = first.ring(1).stream().map(SourceModelBrowseLayout.Node::name).toList();
    assertEquals(List.of("a", "b", "c", "d"), names);
    assertTrue(first.ring(1).get(0).centerY() < focus.centerY());
    for (int i = 0; i < first.ring(1).size(); i++) {
      for (int j = i + 1; j < first.ring(1).size(); j++) {
        assertTrue(separated(first.ring(1).get(i), first.ring(1).get(j)));
      }
      SourceModelBrowseLayout.Node again = second.ring(1).get(i);
      assertEquals(first.ring(1).get(i).x(), again.x());
      assertEquals(first.ring(1).get(i).y(), again.y());
    }
  }

  @Test
  void nodeReachableInOneHopStaysOnTheInnerRing() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("a"));
    model.getTables().add(table("b"));
    model.getTables().add(table("c"));
    model.getRelationships().add(rel("a", "b"));
    model.getRelationships().add(rel("a", "c"));
    model.getRelationships().add(rel("b", "c"));

    SourceModelBrowseLayout.Result result =
        SourceModelBrowseLayout.layout(model, SourceEndpointKind.TABLE, "a", 2);

    assertEquals(1, result.ringOf(SourceEndpointKind.TABLE, "c"));
    assertEquals(1, result.ringOf(SourceEndpointKind.TABLE, "b"));
  }

  @Test
  void depthTwoIncludesATwoHopCardAndSkipsAnUnrelatedOne() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("a"));
    model.getTables().add(table("b"));
    model.getTables().add(table("c"));
    model.getTables().add(table("d"));
    model.getTables().add(table("e"));
    model.getTables().add(table("f"));
    model.getRelationships().add(rel("a", "b"));
    model.getRelationships().add(rel("a", "c"));
    model.getRelationships().add(rel("b", "d"));
    model.getRelationships().add(rel("c", "e"));
    model.getRelationships().add(rel("d", "e"));

    SourceModelBrowseLayout.Result result =
        SourceModelBrowseLayout.layout(model, SourceEndpointKind.TABLE, "a", 2);

    assertEquals(2, result.ringOf(SourceEndpointKind.TABLE, "d"));
    assertFalse(result.isVisible(SourceEndpointKind.TABLE, "f"));
    assertTrue(result.drawsRelationship(rel("b", "d")));
    assertFalse(result.drawsRelationship(rel("d", "e")));
    assertTrue(result.drawsRelationship(rel("a", "b")));
  }

  @Test
  void translatedKeepsTheRingAndMovesTheFocusCenter() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("hub"));
    model.getTables().add(table("a"));
    model.getRelationships().add(rel("hub", "a"));

    SourceModelBrowseLayout.Result origin =
        SourceModelBrowseLayout.layout(model, SourceEndpointKind.TABLE, "hub", 1);
    SourceModelBrowseLayout.Result moved = origin.translated(400, 300);

    assertEquals(origin.focus().centerX() + 400, moved.focus().centerX());
    assertEquals(origin.focus().centerY() + 300, moved.focus().centerY());
    SourceModelBrowseLayout.Node neighbor = moved.ring(1).get(0);
    SourceModelBrowseLayout.Node originNeighbor = origin.ring(1).get(0);
    assertEquals(originNeighbor.x() + 400, neighbor.x());
    assertEquals(originNeighbor.y() + 300, neighbor.y());
  }

  private static boolean separated(SourceModelBrowseLayout.Node a, SourceModelBrowseLayout.Node b) {
    return a.x() + a.width() + SourceModelBrowseLayout.GAP <= b.x()
        || b.x() + b.width() + SourceModelBrowseLayout.GAP <= a.x()
        || a.y() + a.height() + SourceModelBrowseLayout.GAP <= b.y()
        || b.y() + b.height() + SourceModelBrowseLayout.GAP <= a.y();
  }

  private static SourceTable table(String name) {
    SourceTable table = new SourceTable(name);
    table.setLocation(new org.apache.hop.core.gui.Point(10, 10));
    return table;
  }

  private static SourceRelationship rel(String parent, String child) {
    SourceRelationship relationship = new SourceRelationship();
    relationship.setName(parent + "_" + child);
    relationship.setParentTableName(parent);
    relationship.setChildTableName(child);
    return relationship;
  }
}
