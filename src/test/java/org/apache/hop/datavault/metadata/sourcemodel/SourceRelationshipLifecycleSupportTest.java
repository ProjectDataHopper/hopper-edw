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
package org.apache.hop.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceRelationshipLifecycleSupportTest {

  @Test
  void dropRelationshipsOnRenameRemovesEdgesForOldName() {
    SourceModel model = new SourceModel();
    SourcePipeline pipeline = new SourcePipeline("pipeline");
    SourceTable warehouse = new SourceTable("warehouse");
    model.getPipelineSources().add(pipeline);
    model.getTables().add(warehouse);

    SourceRelationship rel = rel("rel_warehouse_pipeline", pipeline, warehouse);
    model.getRelationships().add(rel);
    // Unrelated edge must survive.
    SourceTable order = new SourceTable("order_header");
    model.getTables().add(order);
    SourceRelationship keep = relTable("rel_order_wh", "order_header", "warehouse");
    model.getRelationships().add(keep);

    int removed =
        SourceRelationshipLifecycleSupport.dropRelationshipsOnRename(
            model, SourceEndpointKind.PIPELINE, "pipeline", "asn_feed");
    assertEquals(1, removed);
    assertEquals(1, model.getRelationships().size());
    assertEquals("rel_order_wh", model.getRelationships().get(0).getName());
  }

  @Test
  void dropRelationshipsOnRenameNoopsWhenNameUnchanged() {
    SourceModel model = new SourceModel();
    model.getRelationships().add(relTable("r1", "a", "b"));
    assertEquals(
        0,
        SourceRelationshipLifecycleSupport.dropRelationshipsOnRename(
            model, SourceEndpointKind.TABLE, "a", "a"));
    assertEquals(1, model.getRelationships().size());
  }

  @Test
  void removeDanglingRelationshipsDropsMissingEndpoints() {
    SourceModel model = new SourceModel();
    SourceTable warehouse = new SourceTable("warehouse");
    model.getTables().add(warehouse);
    // Parent pipeline name no longer exists on the model.
    SourceRelationship dangling = new SourceRelationship("rel_warehouse_pipeline");
    dangling.setChildEndpointKind(SourceEndpointKind.TABLE);
    dangling.setParentEndpointKind(SourceEndpointKind.PIPELINE);
    dangling.setChildTableName("warehouse");
    dangling.setParentTableName("pipeline");
    dangling.getChildColumns().add("warehouse_id");
    dangling.getParentColumns().add("warehouse_id");
    model.getRelationships().add(dangling);

    SourceTable a = new SourceTable("a");
    SourceTable b = new SourceTable("b");
    model.getTables().add(a);
    model.getTables().add(b);
    model.getRelationships().add(relTable("ok", "a", "b"));

    int removed = SourceRelationshipLifecycleSupport.removeDanglingRelationships(model);
    assertEquals(1, removed);
    assertEquals(1, model.getRelationships().size());
    assertEquals("ok", model.getRelationships().get(0).getName());
  }

  @Test
  void referencesEndpointIsKindAware() {
    SourceRelationship rel = new SourceRelationship("r");
    rel.setChildEndpointKind(SourceEndpointKind.PIPELINE);
    rel.setParentEndpointKind(SourceEndpointKind.TABLE);
    rel.setChildTableName("feed");
    rel.setParentTableName("feed"); // same string, different kind
    assertTrue(
        SourceRelationshipLifecycleSupport.referencesEndpoint(
            rel, SourceEndpointKind.PIPELINE, "feed"));
    assertTrue(
        SourceRelationshipLifecycleSupport.referencesEndpoint(
            rel, SourceEndpointKind.TABLE, "feed"));
    assertFalse(
        SourceRelationshipLifecycleSupport.referencesEndpoint(
            rel, SourceEndpointKind.QUERY, "feed"));
  }

  private static SourceRelationship rel(
      String name, SourcePipeline pipeline, SourceTable parent) {
    SourceRelationship relationship = new SourceRelationship(name);
    relationship.setChildEndpointKind(SourceEndpointKind.PIPELINE);
    relationship.setParentEndpointKind(SourceEndpointKind.TABLE);
    relationship.setChildTableName(pipeline.getName());
    relationship.setParentTableName(parent.getName());
    relationship.getChildColumns().add("id");
    relationship.getParentColumns().add("id");
    return relationship;
  }

  private static SourceRelationship relTable(String name, String child, String parent) {
    SourceRelationship relationship = new SourceRelationship(name);
    relationship.setChildEndpointKind(SourceEndpointKind.TABLE);
    relationship.setParentEndpointKind(SourceEndpointKind.TABLE);
    relationship.setChildTableName(child);
    relationship.setParentTableName(parent);
    relationship.getChildColumns().add("id");
    relationship.getParentColumns().add("id");
    return relationship;
  }
}
