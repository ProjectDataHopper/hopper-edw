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
package org.hopper.edw.datavault.hopgui.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownHtmlRendererTest {

  @Test
  void wrapsDocumentWithTitleAndBody() {
    String html = MarkdownHtmlRenderer.toHtmlDocument("Hub help", "# Summary");

    assertTrue(html.contains("<!DOCTYPE html>"));
    assertTrue(html.contains("<title>Hub help</title>"));
    assertTrue(html.contains("<h1>Summary</h1>"));
  }

  @Test
  void rendersInlineFormattingAndLinks() {
    String body =
        MarkdownHtmlRenderer.toHtmlBody("**bold** and *italic* with [docs](https://example.com)");

    assertTrue(body.contains("<strong>bold</strong>"));
    assertTrue(body.contains("<em>italic</em>"));
    assertTrue(body.contains("<a href=\"https://example.com\">docs</a>"));
  }

  @Test
  void rendersListsAndCodeBlocks() {
    String body =
        MarkdownHtmlRenderer.toHtmlBody(
            """
            - first
            - second

            ```
            SELECT 1
            ```
            """);

    assertTrue(body.contains("<ul>"));
    assertTrue(body.contains("first"));
    assertTrue(body.contains("second"));
    assertTrue(body.contains("<pre>"));
    assertTrue(body.contains("SELECT 1"));
  }

  @Test
  void escapesHtmlInPlainText() {
    String body = MarkdownHtmlRenderer.toHtmlBody("value 5 < 10");

    assertTrue(body.contains("&lt;"));
    assertTrue(body.contains("value 5"));
    assertTrue(body.contains("10"));
  }

  @Test
  void rendersGfmTables() {
    String body =
        MarkdownHtmlRenderer.toHtmlBody(
            """
            | Hub | Source column |
            |-----|-----------------|
            | hub_order | order_id |
            """);

    assertTrue(body.contains("<table>"));
    assertTrue(body.contains("hub_order"));
  }

  @Test
  void emptyMarkdownProducesEmptyBody() {
    assertEquals("", MarkdownHtmlRenderer.toHtmlBody(""));
    assertEquals("", MarkdownHtmlRenderer.toHtmlBody(null));
  }
}
