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
package org.hopper.edw.datavault.hopgui.perspective.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.CatalogFeed;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.OutputRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot.WorkflowRef;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyTreeNode.Kind;
import org.junit.jupiter.api.Test;

class EdwJourneyTreeBuilderTest {

  @Test
  void emptySnapshotStillEmitsCanonicalStagesInOrder() {
    EdwJourneyTreeNode root = EdwJourneyTreeBuilder.build(EdwJourneySnapshot.empty());

    assertEquals(Kind.GROUP, root.kind());
    assertEquals(EdwJourneyIds.group(""), root.id());
    assertEquals(8, root.children().size());
    assertEquals(EdwJourneyStage.SOURCES, root.children().get(0).stage());
    assertEquals(EdwJourneyStage.CONTROLS, root.children().get(1).stage());
    assertEquals(EdwJourneyStage.DATA_VAULT, root.children().get(2).stage());
    assertEquals(EdwJourneyStage.BUSINESS_VAULT, root.children().get(3).stage());
    assertEquals(EdwJourneyStage.DIMENSIONAL, root.children().get(4).stage());
    assertEquals(EdwJourneyStage.TARGET_QUALITY, root.children().get(5).stage());
    assertEquals(EdwJourneyStage.ORCHESTRATION, root.children().get(6).stage());
    assertEquals(EdwJourneyStage.OUTPUTS, root.children().get(7).stage());
    assertTrue(root.children().get(2).children().isEmpty());
    assertTrue(root.children().get(3).children().isEmpty());
  }

  @Test
  void preservesModelListOrderAndStableIds() {
    RecordDefinitionKey customer =
        new RecordDefinitionKey("hop/retail-example/sources", "customer");
    EdwJourneySnapshot snapshot =
        new EdwJourneySnapshot(
            "retail-sources",
            "local-catalog",
            List.of(
                new ModelRef(
                    "${PROJECT_HOME}/models/crm.hsm",
                    "crm",
                    EdwJourneySnapshot.MODEL_TYPE_SOURCE,
                    List.of("customer"))),
            List.of(
                new CatalogFeed(
                    "local-catalog", customer, "${PROJECT_HOME}/models/crm.hsm", "SOURCE_MODEL")),
            List.of(
                new ModelRef(
                    "${PROJECT_HOME}/models/core.hdv",
                    "core",
                    EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT,
                    List.of("hub_customer")),
                new ModelRef(
                    "${PROJECT_HOME}/models/sales.hdv",
                    "sales",
                    EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT,
                    List.of())),
            List.of(),
            List.of(
                new ModelRef(
                    "${PROJECT_HOME}/models/orders.hdm",
                    "orders",
                    EdwJourneySnapshot.MODEL_TYPE_DIMENSIONAL,
                    List.of("f_orders"))),
            List.of("v1.0.0"),
            List.of(
                new WorkflowRef(
                    "${PROJECT_HOME}/workflows/run-retail-update.hwf",
                    "run-retail-update",
                    List.of(
                        new EdwJourneySnapshot.ActionRef(
                            "Update resource definition group",
                            "UPDATE_RESOURCE_DEFINITION_GROUP")))),
            List.of(
                new OutputRef(
                    "${PROJECT_HOME}/work/reports/retail-dv-update-report.html",
                    "retail-dv-update-report.html")),
            List.of(),
            List.of(),
            List.of());

    EdwJourneyTreeNode root = EdwJourneyTreeBuilder.build(snapshot);
    assertEquals("retail-sources", root.label());
    assertEquals("local-catalog", root.catalogConnection());

    EdwJourneyTreeNode sources = root.children().get(0);
    assertEquals(Kind.SOURCE_MODEL, sources.children().get(0).kind());
    assertEquals(
        EdwJourneyIds.sourceModel("${PROJECT_HOME}/models/crm.hsm"),
        sources.children().get(0).id());
    assertEquals(Kind.CATALOG_FEEDS, sources.children().get(1).kind());
    EdwJourneyTreeNode feed = sources.children().get(1).children().get(0);
    assertEquals("customer", feed.label());
    assertEquals(customer, feed.catalogKey());
    assertEquals(EdwJourneyIds.catalogFeed(customer), feed.id());

    EdwJourneyTreeNode dv = root.children().get(2);
    assertEquals("core", dv.children().get(0).label());
    assertEquals("sales", dv.children().get(1).label());
    assertEquals("hub_customer", dv.children().get(0).children().get(0).tableName());
    assertEquals(
        EdwJourneyIds.model(
            EdwJourneySnapshot.MODEL_TYPE_DATA_VAULT, "${PROJECT_HOME}/models/core.hdv"),
        dv.children().get(0).id());

    assertTrue(root.children().get(3).children().isEmpty());
    assertEquals("orders", root.children().get(4).children().get(0).label());

    EdwJourneyTreeNode versions =
        find(root.children().get(1), EdwJourneyIds.control(EdwJourneyControl.CATALOG_VERSION));
    assertEquals("v1.0.0", versions.children().get(0).label());

    EdwJourneyTreeNode workflow = root.children().get(6).children().get(0);
    assertEquals("run-retail-update", workflow.label());
    assertEquals("UPDATE_RESOURCE_DEFINITION_GROUP", workflow.children().get(0).actionType());

    EdwJourneyTreeNode reports = root.children().get(7).children().get(0);
    assertEquals("retail-dv-update-report.html", reports.children().get(0).label());
    assertEquals(EdwJourneySnapshot.OUTPUT_REPORTS, reports.modelType());
    assertEquals(EdwJourneySnapshot.OUTPUT_REPORTS, reports.children().get(0).modelType());
    assertEquals(EdwJourneyStage.OUTPUTS, reports.stage());
    assertNull(find(root, "missing"));
  }

  @Test
  void catalogOnlyFeedHasNoOriginPath() {
    RecordDefinitionKey key = new RecordDefinitionKey("hop/demo/sources", "orphan");
    EdwJourneySnapshot snapshot =
        new EdwJourneySnapshot(
            "demo",
            "local-catalog",
            List.of(),
            List.of(new CatalogFeed("local-catalog", key, null, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    EdwJourneyTreeNode feed =
        EdwJourneyTreeBuilder.build(snapshot).children().get(0).children().get(0).children().get(0);
    assertEquals("orphan", feed.label());
    assertNull(feed.storedPath());
  }

  private static EdwJourneyTreeNode find(EdwJourneyTreeNode node, String id) {
    if (id.equals(node.id())) {
      return node;
    }
    for (EdwJourneyTreeNode child : node.children()) {
      EdwJourneyTreeNode hit = find(child, id);
      if (hit != null) {
        return hit;
      }
    }
    return null;
  }

  private static EdwJourneyTreeNode find(List<EdwJourneyTreeNode> nodes, String id) {
    for (EdwJourneyTreeNode node : nodes) {
      EdwJourneyTreeNode hit = find(node, id);
      if (hit != null) {
        return hit;
      }
    }
    return null;
  }
}
