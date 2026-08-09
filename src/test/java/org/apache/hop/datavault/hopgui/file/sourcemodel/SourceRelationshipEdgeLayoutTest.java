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
package org.apache.hop.datavault.hopgui.file.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelGraphConnectionGeometry.Bounds;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;

class SourceRelationshipEdgeLayoutTest {

  @Test
  void spreadsAnchorsOnSharedSide() {
    SourceModel model = new SourceModel();
    SourceTable parent = new SourceTable("parent");
    parent.setLocation(new Point(400, 100));
    parent.setDrawnBoxWidth(160);
    parent.setDrawnBoxHeight(90);
    SourceTable c1 = new SourceTable("c1");
    c1.setLocation(new Point(50, 50));
    c1.setDrawnBoxWidth(160);
    c1.setDrawnBoxHeight(90);
    SourceTable c2 = new SourceTable("c2");
    c2.setLocation(new Point(50, 200));
    c2.setDrawnBoxWidth(160);
    c2.setDrawnBoxHeight(90);
    model.getTables().add(parent);
    model.getTables().add(c1);
    model.getTables().add(c2);

    SourceRelationship r1 = rel("r1", "c1", "parent");
    SourceRelationship r2 = rel("r2", "c2", "parent");
    model.getRelationships().add(r1);
    model.getRelationships().add(r2);

    Map<String, SourceTable> byName = new HashMap<>();
    byName.put("parent", parent);
    byName.put("c1", c1);
    byName.put("c2", c2);

    Map<SourceRelationship, SourceRelationshipEdgeLayout.EdgeGeometry> layout =
        SourceRelationshipEdgeLayout.layout(model, byName);

    assertEquals(2, layout.size());
    Point p1 = layout.get(r1).parentAnchor();
    Point p2 = layout.get(r2).parentAnchor();
    // Both attach to left side of parent (children are to the left) but different Y.
    assertEquals(p1.x, p2.x);
    assertNotEquals(p1.y, p2.y);
  }

  @Test
  void anchorOnSideUsesFractions() {
    Bounds bounds = new Bounds(0, 0, 100, 100);
    Point a =
        SourceRelationshipEdgeLayout.anchorOnSide(
            bounds, SourceRelationshipEdgeLayout.Side.RIGHT, 0, 2);
    Point b =
        SourceRelationshipEdgeLayout.anchorOnSide(
            bounds, SourceRelationshipEdgeLayout.Side.RIGHT, 1, 2);
    assertEquals(100, a.x);
    assertEquals(100, b.x);
    assertTrue(a.y < b.y);
  }

  @Test
  void layoutIncludesPipelineEndpoints() {
    SourceModel model = new SourceModel();
    SourcePipeline pipeline = new SourcePipeline("asn_feed");
    pipeline.setLocation(new Point(40, 80));
    SourceTable order = new SourceTable("order_header");
    order.setLocation(new Point(320, 80));
    order.setDrawnBoxWidth(160);
    order.setDrawnBoxHeight(90);
    model.getPipelineSources().add(pipeline);
    model.getTables().add(order);

    SourceRelationship rel = new SourceRelationship("rel_asn_order");
    rel.setChildEndpointKind(SourceEndpointKind.PIPELINE);
    rel.setParentEndpointKind(SourceEndpointKind.TABLE);
    rel.setChildTableName("asn_feed");
    rel.setParentTableName("order_header");
    rel.getChildColumns().add("order_id");
    rel.getParentColumns().add("order_id");
    model.getRelationships().add(rel);

    Map<String, SourceTable> byName = new HashMap<>();
    byName.put("order_header", order);

    Map<SourceRelationship, SourceRelationshipEdgeLayout.EdgeGeometry> layout =
        SourceRelationshipEdgeLayout.layout(model, byName);

    assertEquals(1, layout.size());
    SourceRelationshipEdgeLayout.EdgeGeometry geometry = layout.get(rel);
    assertNotNull(geometry);
    assertNotNull(geometry.childAnchor());
    assertNotNull(geometry.parentAnchor());
    // Pipeline is left of table → child anchors on its right side.
    assertEquals(SourceRelationshipEdgeLayout.Side.RIGHT, geometry.childSide());
    assertTrue(geometry.childAnchor().x > pipeline.getLocation().x);
  }

  private static SourceRelationship rel(String name, String child, String parent) {
    SourceRelationship relationship = new SourceRelationship(name);
    relationship.setChildTableName(child);
    relationship.setParentTableName(parent);
    relationship.getChildColumns().add("id");
    relationship.getParentColumns().add("id");
    return relationship;
  }
}
