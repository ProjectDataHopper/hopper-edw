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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.junit.jupiter.api.Test;

class HtmlPageWriterTest {

  @Test
  void sidebarKeepsSiblingsAndHeaderHasBreadcrumbSearchAndThemeCombo() {
    DocumentationSite site = new DocumentationSite();
    site.setProjectName("Demo Project");
    site.addNav(
        new NavItem(
            DocObjectKind.PIPELINE,
            "pipeline-file",
            "pipelines/test/pipeline-file.html",
            null,
            List.of("test")));
    site.addNav(
        new NavItem(
            DocObjectKind.PIPELINE,
            "other-file",
            "pipelines/test/other-file.html",
            null,
            List.of("test")));

    String html =
        HtmlPageWriter.render(
            site, "pipelines/test/pipeline-file.html", "pipeline-file", "Pipeline", "<p>body</p>");

    assertTrue(html.contains("pipeline-file"));
    assertTrue(html.contains("other-file"), "sibling in the same folder must remain in the tree");
    assertTrue(html.contains("hop-doc-breadcrumb"));
    assertTrue(html.contains("Demo Project"));
    assertTrue(html.contains(">Pipelines</li>"));
    assertTrue(html.contains(">test</li>"));
    assertTrue(html.contains("aria-current=\"page\">pipeline-file</li>"));
    assertTrue(html.contains("hop-doc-header-tools"));
    assertTrue(html.contains("id=\"hop-doc-theme\""));
    assertTrue(html.contains("<select"));
    assertFalse(html.contains("data-theme-value"));
    int breadcrumb = html.indexOf("hop-doc-breadcrumb");
    int search = html.indexOf("hop-doc-search");
    int theme = html.indexOf("hop-doc-theme");
    assertTrue(breadcrumb >= 0 && search > breadcrumb && theme > search);
  }
}
