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
package org.hopper.edw.datavault.hopgui.file.projectdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HopProjectDocFileTypeTest {

  @TempDir Path tempDir;

  @Test
  void isHttpRecognizesAbsoluteUrls() {
    assertTrue(HopProjectDocFileType.isHttp("https://example/docs"));
    assertTrue(HopProjectDocFileType.isHttp("http://localhost/ui?file=index.html"));
    assertFalse(HopProjectDocFileType.isHttp("/tmp/work/documentation/index.html"));
    assertFalse(HopProjectDocFileType.isHttp(null));
  }

  @Test
  void siteRootOfDetectsGeneratedDocumentation() throws Exception {
    Path site = tempDir.resolve("documentation");
    Path css = site.resolve("assets").resolve("css").resolve("hop-doc.css");
    Path html = site.resolve("index.html");
    Files.createDirectories(css.getParent());
    Files.writeString(css, "body{}");
    Files.writeString(html, "<html class=\"hop-doc-page\"></html>");
    assertEquals(
        site.toAbsolutePath().normalize(),
        ProjectDocumentationExplorerSupport.siteRootOf(html.toString()));
    assertNull(
        ProjectDocumentationExplorerSupport.siteRootOf(tempDir.resolve("x.html").toString()));
  }
}
