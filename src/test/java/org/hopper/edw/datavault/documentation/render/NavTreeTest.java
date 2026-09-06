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
package org.hopper.edw.datavault.documentation.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.render.NavTree.Node;
import org.junit.jupiter.api.Test;

class NavTreeTest {

  @Test
  void nestsFilesByProjectFolderAndSortsCaseInsensitive() {
    Map<DocObjectKind, List<NavItem>> nav = new EnumMap<>(DocObjectKind.class);
    nav.put(
        DocObjectKind.PIPELINE,
        List.of(
            new NavItem(
                DocObjectKind.PIPELINE,
                "zebra",
                "pipelines/load/zebra.html",
                null,
                List.of("load")),
            new NavItem(
                DocObjectKind.PIPELINE,
                "Alpha",
                "pipelines/Load/alpha.html",
                null,
                List.of("Load")),
            new NavItem(DocObjectKind.PIPELINE, "root", "pipelines/root.html")));

    List<Node> roots = NavTree.build(nav);
    assertEquals(1, roots.size());
    Node pipelines = roots.get(0);
    assertEquals("Pipelines", pipelines.getLabel());
    assertEquals(2, pipelines.getChildren().size());
    assertEquals("load", pipelines.getChildren().get(0).getLabel());
    assertTrue(pipelines.getChildren().get(0).isFolder());
    assertEquals("root", pipelines.getChildren().get(1).getLabel());
    assertFalse(pipelines.getChildren().get(1).isFolder());

    List<Node> loadKids = pipelines.getChildren().get(0).getChildren();
    assertEquals(List.of("Alpha", "zebra"), labels(loadKids));
  }

  @Test
  void nestsTablesUnderLayerAndModel() {
    Map<DocObjectKind, List<NavItem>> nav = new EnumMap<>(DocObjectKind.class);
    nav.put(
        DocObjectKind.TABLE,
        List.of(
            new NavItem(
                DocObjectKind.TABLE,
                "hub_customer",
                "tables/dv/hub-customer.html",
                null,
                List.of("Data Vault models", "customer-360")),
            new NavItem(
                DocObjectKind.TABLE,
                "sat_customer",
                "tables/dv/sat-customer.html",
                null,
                List.of("Data Vault models", "customer-360"))));

    Node tables = NavTree.build(nav).get(0);
    assertEquals("Tables", tables.getLabel());
    assertEquals("Data Vault models", tables.getChildren().get(0).getLabel());
    Node model = tables.getChildren().get(0).getChildren().get(0);
    assertEquals("customer-360", model.getLabel());
    assertEquals(List.of("hub_customer", "sat_customer"), labels(model.getChildren()));
  }

  @Test
  void nestsMetadataUnderTypeAndVirtualPath() {
    Map<DocObjectKind, List<NavItem>> nav = new EnumMap<>(DocObjectKind.class);
    nav.put(
        DocObjectKind.METADATA,
        List.of(
            new NavItem(
                DocObjectKind.METADATA,
                "postgres",
                "metadata/rdbms/postgres.html",
                null,
                List.of("Relational Database Connection", "production"))));

    Node metadata = NavTree.build(nav).get(0);
    assertEquals("Metadata", metadata.getLabel());
    assertEquals("Relational Database Connection", metadata.getChildren().get(0).getLabel());
    assertEquals("production", metadata.getChildren().get(0).getChildren().get(0).getLabel());
    assertEquals(
        "postgres",
        metadata.getChildren().get(0).getChildren().get(0).getChildren().get(0).getLabel());
  }

  @Test
  void nestsCatalogUnderCatalogAndNamespace() {
    Map<DocObjectKind, List<NavItem>> nav = new EnumMap<>(DocObjectKind.class);
    nav.put(
        DocObjectKind.CATALOG,
        List.of(
            new NavItem(
                DocObjectKind.CATALOG,
                "customer",
                "catalog/source/customer.html",
                null,
                List.of("retail-catalog", "source"))));

    Node catalog = NavTree.build(nav).get(0);
    assertEquals("Catalog", catalog.getLabel());
    assertEquals("retail-catalog", catalog.getChildren().get(0).getLabel());
    assertEquals("source", catalog.getChildren().get(0).getChildren().get(0).getLabel());
    assertEquals(
        "customer",
        catalog.getChildren().get(0).getChildren().get(0).getChildren().get(0).getLabel());
  }

  @Test
  void htmlKeepsGroupsCollapsedExceptAncestorsOfCurrentPage() {
    Map<DocObjectKind, List<NavItem>> nav = new EnumMap<>(DocObjectKind.class);
    nav.put(
        DocObjectKind.PIPELINE,
        List.of(
            new NavItem(
                DocObjectKind.PIPELINE,
                "alpha",
                "pipelines/load/alpha.html",
                null,
                List.of("load")),
            new NavItem(DocObjectKind.PIPELINE, "root", "pipelines/root.html")));
    nav.put(
        DocObjectKind.WORKFLOW,
        List.of(new NavItem(DocObjectKind.WORKFLOW, "nightly", "workflows/nightly.html")));

    String index = NavTree.render(nav, "index.html");
    assertTrue(
        index.contains(
            "<li class=\"hop-doc-nav-group\"><details data-nav-id=\"Pipelines\"><summary>Pipelines</summary>"));
    assertTrue(
        index.contains(
            "<li class=\"hop-doc-nav-group\"><details data-nav-id=\"Workflows\"><summary>Workflows</summary>"));
    assertFalse(index.contains(" open"));
    assertTrue(index.contains("pipelines/load/alpha.html"));
    assertTrue(index.contains("workflows/nightly.html"));

    String current = NavTree.render(nav, "pipelines/load/alpha.html");
    assertTrue(
        current.contains(
            "<li class=\"hop-doc-nav-group\"><details data-nav-id=\"Pipelines\" class=\"is-current\" open>"));
    assertTrue(
        current.contains(
            "<li class=\"hop-doc-nav-folder\"><details data-nav-id=\"Pipelines/load\" class=\"is-current\" open>"));
    assertTrue(
        current.contains(
            "<li class=\"hop-doc-nav-group\"><details data-nav-id=\"Workflows\"><summary>Workflows</summary>"));
    assertTrue(current.contains("aria-current=\"page\""));
    assertTrue(current.contains(">root</a>"), "sibling under Pipelines must stay visible");
  }

  @Test
  void breadcrumbFollowsTypeFolderAndTitle() {
    Map<DocObjectKind, List<NavItem>> nav = new EnumMap<>(DocObjectKind.class);
    nav.put(
        DocObjectKind.PIPELINE,
        List.of(
            new NavItem(
                DocObjectKind.PIPELINE,
                "pipeline-file",
                "pipelines/test/pipeline-file.html",
                null,
                List.of("test"))));
    assertEquals(
        List.of("Pipelines", "test", "pipeline-file"),
        NavTree.breadcrumb(nav, "pipelines/test/pipeline-file.html"));
    assertEquals(List.of(), NavTree.breadcrumb(nav, "index.html"));
  }

  @Test
  void doesNotTruncateLongLists() {
    List<NavItem> items = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      items.add(new NavItem(DocObjectKind.PIPELINE, "p" + i, "pipelines/p" + i + ".html"));
    }
    Map<DocObjectKind, List<NavItem>> nav = new EnumMap<>(DocObjectKind.class);
    nav.put(DocObjectKind.PIPELINE, items);

    String html = NavTree.render(nav, "index.html");
    assertFalse(html.contains("more…"));
    for (int i = 0; i < 50; i++) {
      assertTrue(html.contains("p" + i + ".html"), "missing p" + i);
    }
  }

  private static List<String> labels(List<Node> nodes) {
    List<String> labels = new ArrayList<>();
    for (Node node : nodes) {
      labels.add(node.getLabel());
    }
    return labels;
  }
}
