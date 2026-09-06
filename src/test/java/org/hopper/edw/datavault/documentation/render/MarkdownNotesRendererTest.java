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

import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.model.TableDoc;
import org.junit.jupiter.api.Test;

class MarkdownNotesRendererTest {

  @Test
  void rendersMarkdownTableAndEmphasis() {
    String html = MarkdownNotesRenderer.toHtml("**bold**\n\n| a | b |\n| --- | --- |\n| 1 | 2 |\n");
    assertTrue(html.contains("<strong>bold</strong>") || html.contains("<strong>bold</strong>"));
    assertTrue(html.contains("<table"), html);
  }

  @Test
  void wrapsNoteBlock() {
    String html = MarkdownNotesRenderer.noteBlock("General", "Hello");
    assertTrue(html.contains("hop-doc-note"));
    assertTrue(html.contains("General"));
    assertTrue(html.contains("Hello"));
  }

  @Test
  void rewritesWorkflowLinkFromModelFileFolder() {
    DocumentationSite site = new DocumentationSite();
    site.rememberPage(
        "workflows/run-retail-initial.hwf",
        DocPaths.htmlForSource("workflows/run-retail-initial.hwf", "hwf"));

    String html =
        MarkdownNotesRenderer.toHtml(
            "See [initial](../workflows/run-retail-initial.hwf)",
            site,
            "models/retail-360.hdv",
            "models/data-vault/models/retail-360.html");

    assertTrue(html.contains("../../../workflows/workflows/run-retail-initial.html"), html);
    assertFalse(html.contains(".hwf"), html);
    assertTrue(html.contains(">initial</a>"), html);
  }

  @Test
  void leavesHttpLinksUnchanged() {
    DocumentationSite site = new DocumentationSite();
    String html =
        MarkdownNotesRenderer.toHtml(
            "See [wiki](https://en.wikipedia.org/wiki/Data_vault_modeling)",
            site,
            "models/retail-360.hdv",
            "models/data-vault/models/retail-360.html");
    assertTrue(html.contains("https://en.wikipedia.org/wiki/Data_vault_modeling"), html);
  }

  @Test
  void rewritesBareTableNameOnSameModel() {
    DocumentationSite site = new DocumentationSite();
    TableDoc table = new TableDoc();
    table.setLayer("bv");
    table.setLogicalName("customer_360_bv");
    table.setModelPageHref("models/business-vault/customer-360.html");
    site.addTable(table);

    String html =
        MarkdownNotesRenderer.toHtml(
            "Open [customer_360_bv](customer_360_bv)",
            site,
            "customer-360.hbv",
            "models/business-vault/customer-360.html");
    assertTrue(html.contains("../../tables/bv/customer-360-bv.html"), html);
  }

  @Test
  void rewritesProjectHomeWorkflowLink() {
    DocumentationSite site = new DocumentationSite();
    site.rememberPage(
        "workflows/run-retail-update.hwf",
        DocPaths.htmlForSource("workflows/run-retail-update.hwf", "hwf"));

    String html =
        MarkdownNotesRenderer.toHtml(
            "[update](${PROJECT_HOME}/workflows/run-retail-update.hwf)",
            site,
            "models/retail-360.hdv",
            "models/data-vault/models/retail-360.html");
    assertTrue(html.contains("../../../workflows/workflows/run-retail-update.html"), html);
  }
}
