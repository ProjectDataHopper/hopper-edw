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
package org.apache.hop.datavault.hopgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.Const;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EdwDocsSupportTest {

  @TempDir Path tempDir;

  @Test
  void candidatesIncludeDefaultPluginFolder() {
    List<Path> candidates = EdwDocsSupport.candidates(EdwDocsSupport.class, null);
    assertFalse(candidates.isEmpty());
    assertTrue(
        candidates.stream()
            .anyMatch(path -> path.endsWith(Path.of("misc", "datavault", "docs", "index.html"))));
  }

  @Test
  void findIndexHtmlUsesPluginBaseFolder() throws Exception {
    Path index = tempDir.resolve("misc").resolve("datavault").resolve("docs").resolve("index.html");
    Files.createDirectories(index.getParent());
    Files.writeString(index, "<html><title>EDW</title></html>");

    String previous = System.getProperty(Const.HOP_PLUGIN_BASE_FOLDERS);
    System.setProperty(Const.HOP_PLUGIN_BASE_FOLDERS, tempDir.toString());
    try {
      Path found = EdwDocsSupport.findIndexHtml(EdwDocsSupport.class, null);
      assertEquals(index.toAbsolutePath().normalize(), found);
    } finally {
      if (previous == null) {
        System.clearProperty(Const.HOP_PLUGIN_BASE_FOLDERS);
      } else {
        System.setProperty(Const.HOP_PLUGIN_BASE_FOLDERS, previous);
      }
    }
  }

  @Test
  void findHtmlPageUsesPluginBaseFolder() throws Exception {
    Path page =
        tempDir.resolve("misc").resolve("datavault").resolve("docs").resolve("edw-journey.html");
    Files.createDirectories(page.getParent());
    Files.writeString(page, "<html><title>Journey</title></html>");

    String previous = System.getProperty(Const.HOP_PLUGIN_BASE_FOLDERS);
    System.setProperty(Const.HOP_PLUGIN_BASE_FOLDERS, tempDir.toString());
    try {
      Path found = EdwDocsSupport.findHtmlPage(EdwDocsSupport.class, null, "edw-journey.html");
      assertEquals(page.toAbsolutePath().normalize(), found);
      assertEquals(
          page.toAbsolutePath().normalize(),
          EdwDocsSupport.findHtmlPage(EdwDocsSupport.class, null, "docs/edw-journey"));
    } finally {
      if (previous == null) {
        System.clearProperty(Const.HOP_PLUGIN_BASE_FOLDERS);
      } else {
        System.setProperty(Const.HOP_PLUGIN_BASE_FOLDERS, previous);
      }
    }
  }

  @Test
  void docsRelativeRejectsPathTraversal() {
    assertEquals("docs/index.html", EdwDocsSupport.docsRelative(null));
    assertEquals("docs/index.html", EdwDocsSupport.docsRelative(""));
    assertEquals("docs/edw-journey.html", EdwDocsSupport.docsRelative("edw-journey.html"));
    assertEquals("docs/edw-journey.html", EdwDocsSupport.docsRelative("docs/edw-journey.html"));
    assertEquals("docs/edw-journey.html", EdwDocsSupport.docsRelative("edw-journey"));
    assertEquals(null, EdwDocsSupport.docsRelative("../secret.html"));
    assertEquals(null, EdwDocsSupport.docsRelative("docs/../secret.html"));
    assertEquals(null, EdwDocsSupport.docsRelative("presentations/overview.html"));
  }
}
