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

import java.util.List;
import org.junit.jupiter.api.Test;

class DocPathsTest {

  @Test
  void htmlPathsKeepFolderStructure() {
    assertEquals("pipelines/load1.html", DocPaths.htmlForSource("load1.hpl", "hpl"));
    assertEquals("pipelines/sub/load1.html", DocPaths.htmlForSource("sub/load1.hpl", "hpl"));
    assertEquals(
        "models/data-vault/models/retail.html", DocPaths.htmlForSource("models/retail.hdv", "hdv"));
  }

  @Test
  void relativizeClimbsToSiteRoot() {
    assertEquals("../index.html", DocPaths.relativize("pipelines/load1.html", "index.html"));
    assertEquals(
        "../../assets/css/hop-doc.css",
        DocPaths.relativize("models/data-vault/retail.html", "assets/css/hop-doc.css"));
    assertEquals("pipelines/load1.html", DocPaths.relativize("index.html", "pipelines/load1.html"));
  }

  @Test
  void folderAndPathSegments() {
    assertEquals(List.of(), DocPaths.folderSegments("load1.hpl"));
    assertEquals(List.of("sub"), DocPaths.folderSegments("sub/load1.hpl"));
    assertEquals(List.of("models"), DocPaths.folderSegments("models/retail.hdv"));
    assertEquals(List.of("a", "b"), DocPaths.pathSegments("/a/b/"));
    assertEquals(List.of("production", "eu"), DocPaths.pathSegments("production/eu"));
    assertEquals("Data Vault models", DocPaths.tableLayerNavLabel("dv"));
    assertEquals("Source models", DocPaths.tableLayerNavLabel("source"));
    assertEquals("", DocPaths.tableLayerNavLabel(""));
  }

  @Test
  void resolveRelativeHonorsParentSegments() {
    assertEquals(
        "workflows/run-retail-initial.hwf",
        DocPaths.resolveRelative("models", "../workflows/run-retail-initial.hwf"));
    assertEquals("workflows/foo.hwf", DocPaths.resolveRelative("", "workflows/foo.hwf"));
    assertEquals("a/c", DocPaths.resolveRelative("a/b", "../c"));
    assertEquals("hwf", DocPaths.extensionOf("workflows/run-retail-initial.hwf"));
  }

  @Test
  void slugAndStableId() {
    assertEquals("hub-customer", DocPaths.slug("Hub Customer"));
    assertEquals("sub_load1.hpl", DocPaths.stableId("sub/load1.hpl"));
    assertEquals("transform-table-input", DocPaths.fragmentId("transform", "Table input"));
  }
}
