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
package org.apache.hop.datavault.hopgui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.RenderedMarkdown;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.SpanKind;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.StyleSpan;
import org.junit.jupiter.api.Test;

class MarkdownStyleRendererTest {

  @Test
  void headingStripsMarkersAndMarksBoldRange() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("# Summary");

    assertEquals("Summary", rendered.displayText());
    assertTrue(containsKind(rendered.spans(), SpanKind.HEADING_1, 0, "Summary".length()));
  }

  @Test
  void distinguishesHeadingLevels() {
    RenderedMarkdown h1 = MarkdownStyleRenderer.render("# One");
    RenderedMarkdown h2 = MarkdownStyleRenderer.render("## Two");
    RenderedMarkdown h3 = MarkdownStyleRenderer.render("### Three");

    assertTrue(containsKind(h1.spans(), SpanKind.HEADING_1, 0, 3));
    assertTrue(containsKind(h2.spans(), SpanKind.HEADING_2, 0, 3));
    assertTrue(containsKind(h3.spans(), SpanKind.HEADING_3, 0, 5));
  }

  @Test
  void parsesBoldAndItalicInline() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("**bold** and *italic*");

    assertEquals("bold and italic", rendered.displayText());
    assertTrue(containsKind(rendered.spans(), SpanKind.BOLD, 0, 4));
    assertTrue(containsKind(rendered.spans(), SpanKind.ITALIC, 9, 6));
  }

  @Test
  void fencedCodeBlockUsesMonospaceStylePerLine() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("```\nSELECT 1\n```");

    assertEquals("SELECT 1", rendered.displayText());
    assertTrue(containsKind(rendered.spans(), SpanKind.CODE_BLOCK, 0, "SELECT 1".length()));
  }

  @Test
  void bulletLineUsesBulletPrefix() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("- first item");

    assertEquals("• first item", rendered.displayText());
  }

  @Test
  void linkShowsLabelWithLinkStyle() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("[docs](https://example.com)");

    assertEquals("docs", rendered.displayText());
    assertTrue(containsKind(rendered.spans(), SpanKind.LINK, 0, 4));
  }

  @Test
  void inlineCodeStripsBackticks() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("Use `sortRowsSize` here");

    assertEquals("Use sortRowsSize here", rendered.displayText());
    assertTrue(containsKind(rendered.spans(), SpanKind.CODE, 4, "sortRowsSize".length()));
  }

  @Test
  void emptyInputReturnsEmptyDisplay() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("");

    assertEquals("", rendered.displayText());
    assertTrue(rendered.spans().isEmpty());
  }

  @Test
  void fencedAsciiTableUsesCodeBlockStyle() {
    String markdown =
        "## Pipelines\n\n```\n| Transform | Plugin |\n|-----------|----------|\n| Table Input | TableInput |\n```";
    RenderedMarkdown rendered = MarkdownStyleRenderer.render(markdown);

    assertTrue(rendered.displayText().contains("| Transform | Plugin |"));
    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.CODE_BLOCK));
    assertTrue(containsKind(rendered.spans(), SpanKind.HEADING_2, 0, "Pipelines".length()));
  }

  @Test
  void pipelineMarkdownKeepsStatusSectionStylingWithTables() {
    String markdown =
        "## Current status\n\n- **Model:** retail-f-orders\n\n## Pipelines\n\n### dm-fact-f_orders `RUNNING`\n\n"
            + "```\n| Transform | Running |\n|-----------|----------|\n| Table Input | yes |\n```";
    RenderedMarkdown rendered = MarkdownStyleRenderer.render(markdown);

    assertTrue(rendered.displayText().contains("• Model: retail-f-orders"));
    assertTrue(containsKind(rendered.spans(), SpanKind.HEADING_2, 0, "Current status".length()));
    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.CODE_BLOCK));
    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.BOLD));
    assertTrue(!hasOverlappingSpans(rendered.spans()));
  }

  @Test
  void headingWithInlineCodeKeepsBothNonOverlappingSpans() {
    RenderedMarkdown rendered =
        MarkdownStyleRenderer.render("### dm-fact-f_orders — f_orders `RUNNING`");

    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.HEADING_3));
    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.CODE));
    assertTrue(isSortedByStart(rendered.spans()));
    assertTrue(!hasOverlappingSpans(rendered.spans()));
    assertTrue(
        containsKind(
            rendered.spans(), SpanKind.HEADING_3, 0, "dm-fact-f_orders — f_orders ".length()));
    assertTrue(containsKind(rendered.spans(), SpanKind.CODE, 28, "RUNNING".length()));
  }

  @Test
  void headingWithInlineCodeProducesSortedNonOverlappingSpans() {
    RenderedMarkdown rendered =
        MarkdownStyleRenderer.render("### dm-fact-f_orders — f_orders `RUNNING`");

    assertTrue(isSortedByStart(rendered.spans()));
    assertTrue(!hasOverlappingSpans(rendered.spans()));
    assertTrue(rendered.spans().stream().anyMatch(span -> span.kind() == SpanKind.CODE));
  }

  @Test
  void identifiersWithUnderscoresDoNotTriggerItalic() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("Current table f_orders");

    assertEquals("Current table f_orders", rendered.displayText());
    assertTrue(rendered.spans().stream().noneMatch(span -> span.kind() == SpanKind.ITALIC));
  }

  @Test
  void malformedMarkdownFallsBackSafely() {
    RenderedMarkdown rendered = MarkdownStyleRenderer.render("**unclosed bold");

    assertEquals("**unclosed bold", rendered.displayText());
  }

  private static boolean hasOverlappingSpans(List<StyleSpan> spans) {
    for (int i = 0; i < spans.size(); i++) {
      StyleSpan left = spans.get(i);
      int leftEnd = left.start() + left.length();
      for (int j = i + 1; j < spans.size(); j++) {
        StyleSpan right = spans.get(j);
        int rightEnd = right.start() + right.length();
        if (left.start() < rightEnd && right.start() < leftEnd) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isSortedByStart(List<StyleSpan> spans) {
    int previousStart = -1;
    for (StyleSpan span : spans) {
      if (span.start() < previousStart) {
        return false;
      }
      previousStart = span.start();
    }
    return true;
  }

  private static boolean containsKind(List<StyleSpan> spans, SpanKind kind, int start, int length) {
    for (StyleSpan span : spans) {
      if (span.kind() == kind && span.start() == start && span.length() == length) {
        return true;
      }
    }
    return false;
  }
}
