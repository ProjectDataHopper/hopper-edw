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
package org.hopper.edw.datavault.hopgui.widget;

import java.util.List;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.hopgui.markdown.CommonMarkStyledTextVisitor;

/** Converts markdown into display text and style spans for SWT StyledText. */
public final class MarkdownStyleRenderer {

  private MarkdownStyleRenderer() {}

  public enum SpanKind {
    BOLD,
    ITALIC,
    CODE,
    LINK,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    CODE_BLOCK,
    TABLE_ROW
  }

  public record StyleSpan(int start, int length, SpanKind kind) {}

  public record RenderedMarkdown(String displayText, List<StyleSpan> spans) {}

  public static RenderedMarkdown render(String markdown) {
    if (Utils.isEmpty(markdown)) {
      return new RenderedMarkdown("", List.of());
    }
    try {
      return CommonMarkStyledTextVisitor.render(markdown);
    } catch (RuntimeException ex) {
      return new RenderedMarkdown(markdown, List.of());
    }
  }
}
