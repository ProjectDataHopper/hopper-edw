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
package org.apache.hop.datavault.hopgui.markdown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.RenderedMarkdown;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.SpanKind;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.StyleSpan;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;

/** Walks a CommonMark AST and produces SWT StyledText display text and style spans. */
public final class CommonMarkStyledTextVisitor extends AbstractVisitor {

  private final StringBuilder display = new StringBuilder();
  private final List<StyleSpan> spans = new ArrayList<>();

  public static RenderedMarkdown render(String markdown) {
    if (Utils.isEmpty(markdown)) {
      return new RenderedMarkdown("", List.of());
    }
    CommonMarkStyledTextVisitor visitor = new CommonMarkStyledTextVisitor();
    CommonMarkSupport.parse(markdown).accept(visitor);
    String displayText = visitor.display.toString();
    if (displayText.isEmpty()) {
      return new RenderedMarkdown(markdown, List.of());
    }
    return new RenderedMarkdown(displayText, visitor.sortSpans(visitor.spans));
  }

  @Override
  public void visit(Document document) {
    visitChildren(document);
    trimTrailingNewlines();
  }

  @Override
  public void visit(Heading heading) {
    ensureBlockSeparator();
    int start = display.length();
    visitChildren(heading);
    addBaseSpanOnGaps(start, display.length(), headingKind(heading.getLevel()));
  }

  @Override
  public void visit(Paragraph paragraph) {
    if (paragraph.getParent() instanceof ListItem) {
      visitChildren(paragraph);
      return;
    }
    ensureBlockSeparator();
    visitChildren(paragraph);
  }

  @Override
  public void visit(BulletList bulletList) {
    for (Node node = bulletList.getFirstChild(); node != null; node = node.getNext()) {
      if (node instanceof ListItem item) {
        visitListItem(item, "• ");
      }
    }
  }

  @Override
  public void visit(OrderedList orderedList) {
    int index = orderedList.getStartNumber();
    for (Node node = orderedList.getFirstChild(); node != null; node = node.getNext()) {
      if (node instanceof ListItem item) {
        visitListItem(item, index + ". ");
        index++;
      }
    }
  }

  @Override
  public void visit(FencedCodeBlock fencedCodeBlock) {
    renderCodeBlock(fencedCodeBlock.getLiteral());
  }

  @Override
  public void visit(IndentedCodeBlock indentedCodeBlock) {
    renderCodeBlock(indentedCodeBlock.getLiteral());
  }

  @Override
  public void visit(CustomBlock customBlock) {
    if (customBlock instanceof TableBlock) {
      ensureBlockSeparator();
    }
    visitChildren(customBlock);
  }

  @Override
  public void visit(CustomNode customNode) {
    if (customNode instanceof TableRow row) {
      renderTableRow(row);
      return;
    }
    visitChildren(customNode);
  }

  @Override
  public void visit(StrongEmphasis strongEmphasis) {
    int start = display.length();
    visitChildren(strongEmphasis);
    addSpan(start, display.length() - start, SpanKind.BOLD);
  }

  @Override
  public void visit(Emphasis emphasis) {
    int start = display.length();
    visitChildren(emphasis);
    addSpan(start, display.length() - start, SpanKind.ITALIC);
  }

  @Override
  public void visit(Code code) {
    String literal = code.getLiteral();
    if (Utils.isEmpty(literal)) {
      return;
    }
    int start = display.length();
    display.append(literal);
    addSpan(start, literal.length(), SpanKind.CODE);
  }

  @Override
  public void visit(Link link) {
    int start = display.length();
    visitChildren(link);
    addSpan(start, display.length() - start, SpanKind.LINK);
  }

  @Override
  public void visit(Text text) {
    display.append(text.getLiteral());
  }

  @Override
  public void visit(SoftLineBreak softLineBreak) {
    display.append('\n');
  }

  @Override
  public void visit(HardLineBreak hardLineBreak) {
    display.append('\n');
  }

  private void visitListItem(ListItem item, String prefix) {
    ensureBlockSeparator();
    Node first = item.getFirstChild();
    if (first instanceof TaskListItemMarker marker) {
      prefix = marker.isChecked() ? "[x] " : "[ ] ";
      first = first.getNext();
    }
    display.append(prefix);
    for (Node node = first; node != null; node = node.getNext()) {
      node.accept(this);
    }
  }

  private void renderTableRow(TableRow row) {
    ensureBlockSeparator();
    List<String> cells = new ArrayList<>();
    for (Node node = row.getFirstChild(); node != null; node = node.getNext()) {
      if (node instanceof TableCell cell) {
        cells.add(extractPlainText(cell).trim());
      }
    }
    if (cells.isEmpty()) {
      return;
    }
    int start = display.length();
    display.append("| ");
    display.append(String.join(" | ", cells));
    display.append(" |");
    addSpan(start, display.length() - start, SpanKind.TABLE_ROW);
  }

  private void renderCodeBlock(String literal) {
    ensureBlockSeparator();
    String content = literal != null ? literal : "";
    if (content.endsWith("\n")) {
      content = content.substring(0, content.length() - 1);
    }
    if (content.isEmpty()) {
      return;
    }
    int start = display.length();
    display.append(content);
    addSpan(start, display.length() - start, SpanKind.CODE_BLOCK);
  }

  private String extractPlainText(Node node) {
    PlainTextExtractor extractor = new PlainTextExtractor();
    node.accept(extractor);
    return extractor.text.toString();
  }

  private void ensureBlockSeparator() {
    if (display.isEmpty()) {
      return;
    }
    if (display.charAt(display.length() - 1) != '\n') {
      display.append('\n');
    }
  }

  private void trimTrailingNewlines() {
    while (!display.isEmpty() && display.charAt(display.length() - 1) == '\n') {
      display.setLength(display.length() - 1);
    }
  }

  private void addSpan(int start, int length, SpanKind kind) {
    if (length > 0) {
      spans.add(new StyleSpan(start, length, kind));
    }
  }

  private void addBaseSpanOnGaps(int start, int end, SpanKind baseKind) {
    if (start >= end) {
      return;
    }
    List<StyleSpan> inline =
        spans.stream()
            .filter(span -> span.start() >= start && span.start() + span.length() <= end)
            .sorted(Comparator.comparingInt(StyleSpan::start))
            .toList();
    if (inline.isEmpty()) {
      addSpan(start, end - start, baseKind);
      return;
    }
    int pos = start;
    for (StyleSpan span : inline) {
      if (span.start() > pos) {
        addSpan(pos, span.start() - pos, baseKind);
      }
      pos = Math.max(pos, span.start() + span.length());
    }
    if (pos < end) {
      addSpan(pos, end - pos, baseKind);
    }
  }

  private static SpanKind headingKind(int level) {
    return switch (level) {
      case 1 -> SpanKind.HEADING_1;
      case 2 -> SpanKind.HEADING_2;
      case 3 -> SpanKind.HEADING_3;
      default -> SpanKind.BOLD;
    };
  }

  private List<StyleSpan> sortSpans(List<StyleSpan> source) {
    if (source == null || source.isEmpty()) {
      return List.of();
    }
    return source.stream()
        .sorted(
            Comparator.comparingInt(StyleSpan::start)
                .thenComparingInt(StyleSpan::length)
                .thenComparing(span -> span.kind().name()))
        .toList();
  }

  private static final class PlainTextExtractor extends AbstractVisitor {
    private final StringBuilder text = new StringBuilder();

    @Override
    public void visit(Text node) {
      text.append(node.getLiteral());
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
      text.append(' ');
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
      text.append(' ');
    }
  }
}
