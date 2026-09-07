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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Props;
import org.apache.hop.core.util.Utils;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.hopper.edw.datavault.hopgui.widget.MarkdownStyleRenderer.RenderedMarkdown;
import org.hopper.edw.datavault.hopgui.widget.MarkdownStyleRenderer.SpanKind;
import org.hopper.edw.datavault.hopgui.widget.MarkdownStyleRenderer.StyleSpan;

/**
 * Desktop {@code StyledText} helpers. RAP / Hop Web does not ship {@code StyledText}; load this
 * class only when {@code !EnvironmentUtils.getInstance().isWeb()}.
 */
final class MarkdownDesktopStyledText {

  private MarkdownDesktopStyledText() {}

  static Control createReadOnly(Composite parent) {
    StyledText styledText =
        new StyledText(parent, SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER);
    PropsUi.setLook(styledText);
    styledText.setMargins(4, 4, 4, 4);
    return styledText;
  }

  static Control createEditor(Composite parent) {
    StyledText editor =
        new StyledText(parent, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
    PropsUi.setLook(editor, Props.WIDGET_STYLE_FIXED);
    editor.setMargins(4, 4, 4, 4);
    return editor;
  }

  static void setMarkdown(Control control, String markdown, Font fixedFont, Font boldFixedFont) {
    StyledText styledText = styledText(control);
    GuiResource resources = GuiResource.getInstance();
    ThemeColors colors = ThemeColors.from(resources);
    RenderedMarkdown rendered = MarkdownStyleRenderer.render(markdown);
    String displayText = rendered.displayText();
    List<StyleRange> ranges =
        toStyleRanges(rendered.spans(), displayText.length(), fixedFont, boldFixedFont, colors);
    styledText.setRedraw(false);
    try {
      applyBaseFont(styledText, fixedFont);
      styledText.setText(displayText);
      applyStyleRangesSafely(styledText, ranges, displayText.length(), fixedFont);
    } catch (RuntimeException ex) {
      clearStyleRanges(styledText);
    } finally {
      styledText.setRedraw(true);
    }
  }

  static void setPlainText(Control control, String text, Font fixedFont, Font boldFixedFont) {
    StyledText styledText = styledText(control);
    String value = text != null ? text : "";
    if (Utils.isEmpty(value)) {
      styledText.setText("");
      clearStyleRanges(styledText);
      return;
    }
    setMarkdown(control, value, fixedFont, boldFixedFont);
  }

  static void setText(Control control, String text) {
    styledText(control).setText(text != null ? text : "");
  }

  static String getText(Control control) {
    return styledText(control).getText();
  }

  static void scrollToTop(Control control) {
    StyledText styledText = styledText(control);
    if (!styledText.isDisposed()) {
      styledText.setTopIndex(0);
    }
  }

  static int preferredHeight(Control control, int width) {
    StyledText styledText = styledText(control);
    return Math.max(styledText.computeSize(width, SWT.DEFAULT).y + 8, styledText.getLineHeight());
  }

  static void applyBaseFont(Control control, Font fixedFont) {
    applyBaseFont(styledText(control), fixedFont);
  }

  private static void applyBaseFont(StyledText styledText, Font fixedFont) {
    if (styledText != null
        && !styledText.isDisposed()
        && fixedFont != null
        && !fixedFont.isDisposed()) {
      styledText.setFont(fixedFont);
    }
  }

  private static void applyStyleRangesSafely(
      StyledText styledText, List<StyleRange> ranges, int textLength, Font fixedFont) {
    if (textLength <= 0) {
      clearStyleRanges(styledText);
      return;
    }
    clearStyleRanges(styledText);
    if (ranges == null || ranges.isEmpty()) {
      return;
    }
    for (StyleRange range : ranges) {
      StyleRange normalized = normalizeStyleRange(range, textLength, fixedFont);
      if (normalized == null) {
        continue;
      }
      try {
        styledText.setStyleRange(normalized);
      } catch (RuntimeException ignored) {
        // Skip ranges SWT rejects; keep the rest of the document styled.
      }
    }
  }

  private static void clearStyleRanges(StyledText styledText) {
    try {
      styledText.setStyleRanges(new StyleRange[0]);
    } catch (RuntimeException ignored) {
      // Widget may reject style reset; plain text is still usable.
    }
  }

  private static StyleRange normalizeStyleRange(StyleRange range, int textLength, Font fixedFont) {
    if (range == null || range.length <= 0 || range.start < 0 || range.start >= textLength) {
      return null;
    }
    StyleRange normalized = new StyleRange();
    normalized.start = range.start;
    normalized.length = Math.min(range.length, textLength - range.start);
    normalized.fontStyle = range.fontStyle;
    normalized.foreground = range.foreground;
    normalized.background = range.background;
    normalized.underline = range.underline;
    normalized.underlineStyle = range.underlineStyle;
    normalized.strikeout = range.strikeout;
    normalized.borderStyle = range.borderStyle;
    normalized.font = validFont(range.font);
    if (normalized.font == null) {
      normalized.font = validFont(fixedFont);
    }
    return normalized.length > 0 && normalized.font != null ? normalized : null;
  }

  private static Font validFont(Font font) {
    return font != null && !font.isDisposed() ? font : null;
  }

  private static List<StyleRange> toStyleRanges(
      List<StyleSpan> spans,
      int textLength,
      Font fixedFont,
      Font boldFixedFont,
      ThemeColors colors) {
    if (spans == null || spans.isEmpty() || textLength <= 0) {
      return List.of();
    }
    List<StyleSpan> ordered =
        spans.stream()
            .sorted(
                java.util.Comparator.comparingInt(StyleSpan::start)
                    .thenComparingInt(StyleSpan::length)
                    .thenComparing(span -> span.kind().name()))
            .toList();
    List<StyleRange> proseRanges = new ArrayList<>();
    List<StyleRange> blockRanges = new ArrayList<>();
    for (StyleSpan span : ordered) {
      if (span.length() <= 0 || span.start() >= textLength) {
        continue;
      }
      int length = Math.min(span.length(), textLength - span.start());
      StyleRange range = new StyleRange();
      range.start = span.start();
      range.length = length;
      applyKind(range, span.kind(), fixedFont, boldFixedFont, colors);
      if (span.kind() == SpanKind.CODE_BLOCK || span.kind() == SpanKind.TABLE_ROW) {
        blockRanges.add(range);
      } else {
        proseRanges.add(range);
      }
    }
    List<StyleRange> ranges = new ArrayList<>(proseRanges.size() + blockRanges.size());
    ranges.addAll(blockRanges);
    ranges.addAll(proseRanges);
    return ranges;
  }

  private static void applyKind(
      StyleRange range, SpanKind kind, Font fixedFont, Font boldFixedFont, ThemeColors colors) {
    range.font = validFont(fixedFont);
    range.fontStyle = SWT.NORMAL;
    Font headingFont = validFont(boldFixedFont);
    if (headingFont == null) {
      headingFont = validFont(fixedFont);
    }
    switch (kind) {
      case HEADING_1 -> {
        range.font = headingFont;
        applyForeground(range, colors.heading1());
      }
      case HEADING_2 -> {
        range.font = headingFont;
        applyForeground(range, colors.heading2());
      }
      case HEADING_3 -> {
        range.font = headingFont;
        applyForeground(range, colors.heading3());
      }
      case BOLD -> range.font = headingFont;
      case ITALIC -> range.fontStyle = SWT.ITALIC;
      case CODE -> {
        applyForeground(range, colors.codeForeground());
        applyBackground(range, colors.codeBackground());
      }
      case CODE_BLOCK, TABLE_ROW -> {
        applyForeground(range, colors.codeForeground());
        applyBackground(range, colors.codeBlockBackground());
      }
      case LINK -> {
        applyForeground(range, colors.link());
        range.underline = true;
        range.underlineStyle = SWT.UNDERLINE_SINGLE;
      }
      default -> {
        // no-op
      }
    }
  }

  private static void applyForeground(StyleRange range, Color color) {
    if (color != null && !color.isDisposed()) {
      range.foreground = color;
    }
  }

  private static void applyBackground(StyleRange range, Color color) {
    if (color != null && !color.isDisposed()) {
      range.background = color;
    }
  }

  private static StyledText styledText(Control control) {
    return (StyledText) control;
  }

  private record ThemeColors(
      Color heading1,
      Color heading2,
      Color heading3,
      Color codeForeground,
      Color codeBackground,
      Color codeBlockBackground,
      Color link) {

    static ThemeColors from(GuiResource resources) {
      return new ThemeColors(
          MarkdownStylePalette.heading1(resources),
          MarkdownStylePalette.heading2(resources),
          MarkdownStylePalette.heading3(resources),
          MarkdownStylePalette.codeForeground(resources),
          MarkdownStylePalette.codeBackground(resources),
          MarkdownStylePalette.codeBlockBackground(resources),
          MarkdownStylePalette.link(resources));
    }
  }
}
