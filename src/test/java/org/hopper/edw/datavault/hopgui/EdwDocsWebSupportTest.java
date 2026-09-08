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
package org.hopper.edw.datavault.hopgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EdwDocsWebSupportTest {

  @TempDir Path tempDir;

  @Test
  void resolveSafeRejectsTraversalAndUnknownTypes() {
    Path docs = tempDir.resolve("docs");
    assertEquals(
        docs.resolve("index.html").toAbsolutePath().normalize(),
        EdwDocsWebSupport.resolveSafe(docs, "index.html"));
    assertEquals(
        docs.resolve("help").resolve("dv-hub-dialog.html").toAbsolutePath().normalize(),
        EdwDocsWebSupport.resolveSafe(docs, "help/dv-hub-dialog.html"));
    assertEquals(
        docs.resolve("images").resolve("edw-logo.svg").toAbsolutePath().normalize(),
        EdwDocsWebSupport.resolveSafe(docs, "images/edw-logo.svg"));
    assertNull(EdwDocsWebSupport.resolveSafe(docs, "../secret.html"));
    assertNull(EdwDocsWebSupport.resolveSafe(docs, "help/../../secret.html"));
    assertNull(EdwDocsWebSupport.resolveSafe(docs, "/etc/passwd"));
    assertNull(EdwDocsWebSupport.resolveSafe(docs, "index.exe"));
  }

  @Test
  void rewriteRelativeUrlsKeepsAbsoluteAndFragments() {
    String html =
        """
        <a href="architecture.html">A</a>
        <a href="help/index.html">H</a>
        <img src="images/edw-logo.svg">
        <a href="#start-here">frag</a>
        <a href="https://hop.apache.org/">ext</a>
        """;
    String rewritten =
        EdwDocsWebSupport.rewriteRelativeUrls(
            html, "./ui?cid=1&servicehandler=hopperEdwDocs", "index.html");
    assertTrue(rewritten.contains("file=architecture.html"));
    assertTrue(
        rewritten.contains("file=help%2Findex.html") || rewritten.contains("file=help/index.html"));
    assertTrue(
        rewritten.contains("file=images%2Fedw-logo.svg")
            || rewritten.contains("file=images/edw-logo.svg"));
    assertTrue(rewritten.contains("href=\"#start-here\""));
    assertTrue(rewritten.contains("https://hop.apache.org/"));
    assertFalse(rewritten.contains("file=#start-here"));
  }

  @Test
  void rewriteRefResolvesParentDirectory() {
    String rewritten =
        EdwDocsWebSupport.rewriteRef(
            "./ui?servicehandler=hopperEdwDocs", "help/index.html", "../architecture.html");
    assertTrue(rewritten.contains("file=architecture.html"));
    assertNull(EdwDocsWebSupport.resolveAgainst("help/index.html", "../../secret.html"));
  }

  @Test
  void relativeDocFileAndDocsDirectory() {
    Path html =
        tempDir
            .resolve("misc")
            .resolve("hopper-edw")
            .resolve("docs")
            .resolve("help")
            .resolve("x.html");
    Path docs = html.getParent().getParent();
    assertEquals(docs, EdwDocsWebSupport.docsDirectory(html));
    assertEquals("help/x.html", EdwDocsWebSupport.relativeDocFile(docs, html));
  }

  @Test
  void contentTypeForHtmlAndSvg() {
    assertEquals("text/html; charset=UTF-8", EdwDocsWebSupport.contentType("index.html"));
    assertEquals("image/svg+xml", EdwDocsWebSupport.contentType("edw-logo.svg"));
  }

  @Test
  void stripFileParamRemovesFileQuery() {
    assertEquals(
        "cid=1&servicehandler=hopperEdwDocs",
        EdwDocsWebSupport.stripFileParam("cid=1&file=index.html&servicehandler=hopperEdwDocs"));
  }

  @Test
  void browserUrlOnDesktopUsesFileUri() throws Exception {
    Path html = tempDir.resolve("docs").resolve("index.html");
    Files.createDirectories(html.getParent());
    Files.writeString(html, "<html></html>");
    String url = EdwDocsWebSupport.browserUrl(html);
    assertTrue(url.startsWith("file:"));
    assertTrue(url.contains("index.html"));
  }
}
