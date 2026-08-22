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
package org.apache.hop.datavault.hopgui.widget;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.RenderedMarkdown;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.SpanKind;
import org.apache.hop.datavault.hopgui.widget.MarkdownStyleRenderer.StyleSpan;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

/** Read-only composite that renders markdown advice with SWT StyledText style ranges. */
public class MarkdownStyledTextComp extends Composite {

  private final StyledText styledText;
  private final Font fixedFont;
  private final Font boldFixedFont;
  private final boolean disposeFixedFont;
  private final boolean disposeBoldFixedFont;
  private final GuiResource resources;
  private Color heading1Color;
  private Color heading2Color;
  private Color heading3Color;
  private Color codeForeground;
  private Color codeBackground;
  private Color codeBlockBackground;
  private Color linkColor;

  public MarkdownStyledTextComp(Composite parent, int style) {
    super(parent, style);
    PropsUi.setLook(this);
    setLayout(new FormLayout());

    resources = GuiResource.getInstance();
    FixedFonts resolved = resolveFixedFonts(resources, getDisplay());
    fixedFont = resolved.fixedFont();
    boldFixedFont = resolved.boldFixedFont();
    disposeFixedFont = resolved.disposeFixedFont();
    disposeBoldFixedFont = resolved.disposeBoldFixedFont();
    refreshThemeColors();

    styledText =
        new StyledText(this, SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER);
    PropsUi.setLook(styledText);
    applyBaseFont();
    styledText.setMargins(4, 4, 4, 4);
    FormData fdText = new FormData();
    fdText.left = new FormAttachment(0, 0);
    fdText.right = new FormAttachment(100, 0);
    fdText.top = new FormAttachment(0, 0);
    fdText.bottom = new FormAttachment(100, 0);
    styledText.setLayoutData(fdText);

    addListener(
        SWT.Dispose,
        event -> {
          if (disposeFixedFont && fixedFont != null && !fixedFont.isDisposed()) {
            fixedFont.dispose();
          }
          if (disposeBoldFixedFont && boldFixedFont != null && !boldFixedFont.isDisposed()) {
            boldFixedFont.dispose();
          }
        });
  }

  public void setMarkdown(String markdown) {
    refreshThemeColors();
    RenderedMarkdown rendered = MarkdownStyleRenderer.render(markdown);
    String displayText = rendered.displayText();
    List<StyleRange> ranges = toStyleRanges(rendered.spans(), displayText.length());
    styledText.setRedraw(false);
    try {
      applyBaseFont();
      styledText.setText(displayText);
      applyStyleRangesSafely(ranges, displayText.length());
    } catch (RuntimeException ex) {
      clearStyleRanges();
    } finally {
      styledText.setRedraw(true);
    }
  }

  private void applyStyleRangesSafely(List<StyleRange> ranges, int textLength) {
    if (textLength <= 0) {
      clearStyleRanges();
      return;
    }
    clearStyleRanges();
    if (ranges == null || ranges.isEmpty()) {
      return;
    }
    for (StyleRange range : ranges) {
      StyleRange normalized = normalizeStyleRange(range, textLength);
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

  private void clearStyleRanges() {
    try {
      styledText.setStyleRanges(new StyleRange[0]);
    } catch (RuntimeException ignored) {
      // Widget may reject style reset; plain text is still usable.
    }
  }

  private StyleRange normalizeStyleRange(StyleRange range, int textLength) {
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

  private Font validFont(Font font) {
    return font != null && !font.isDisposed() ? font : null;
  }

  private void refreshThemeColors() {
    heading1Color = MarkdownStylePalette.heading1(resources);
    heading2Color = MarkdownStylePalette.heading2(resources);
    heading3Color = MarkdownStylePalette.heading3(resources);
    codeForeground = MarkdownStylePalette.codeForeground(resources);
    codeBackground = MarkdownStylePalette.codeBackground(resources);
    codeBlockBackground = MarkdownStylePalette.codeBlockBackground(resources);
    linkColor = MarkdownStylePalette.link(resources);
  }

  public int getPreferredHeight(int width) {
    if (width <= 0) {
      width = 400;
    }
    return Math.max(styledText.computeSize(width, SWT.DEFAULT).y + 8, styledText.getLineHeight());
  }

  private List<StyleRange> toStyleRanges(List<StyleSpan> spans, int textLength) {
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
      applyKind(range, span.kind());
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

  private void applyBaseFont() {
    if (styledText != null
        && !styledText.isDisposed()
        && fixedFont != null
        && !fixedFont.isDisposed()) {
      styledText.setFont(fixedFont);
    }
  }

  private void applyKind(StyleRange range, SpanKind kind) {
    range.font = validFont(fixedFont);
    range.fontStyle = SWT.NORMAL;
    Font headingFont = validFont(boldFixedFont);
    if (headingFont == null) {
      headingFont = validFont(fixedFont);
    }
    switch (kind) {
      case HEADING_1 -> {
        range.font = headingFont;
        applyForeground(range, heading1Color);
      }
      case HEADING_2 -> {
        range.font = headingFont;
        applyForeground(range, heading2Color);
      }
      case HEADING_3 -> {
        range.font = headingFont;
        applyForeground(range, heading3Color);
      }
      case BOLD -> range.font = headingFont;
      case ITALIC -> range.fontStyle = SWT.ITALIC;
      case CODE -> {
        applyForeground(range, codeForeground);
        applyBackground(range, codeBackground);
      }
      case CODE_BLOCK, TABLE_ROW -> {
        applyForeground(range, codeForeground);
        applyBackground(range, codeBlockBackground);
      }
      case LINK -> {
        applyForeground(range, linkColor);
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

  private record FixedFonts(
      Font fixedFont, Font boldFixedFont, boolean disposeFixedFont, boolean disposeBoldFixedFont) {}

  private static FixedFonts resolveFixedFonts(GuiResource resources, Display display) {
    Font fixed = resources.getFontFixed();
    if (fixed != null) {
      return new FixedFonts(fixed, deriveBoldFont(display, fixed), false, true);
    }
    Font created = new Font(display, new FontData("Monospace", 10, SWT.NORMAL));
    return new FixedFonts(created, deriveBoldFont(display, created), true, true);
  }

  private static Font deriveBoldFont(Display display, Font baseFont) {
    FontData[] fontData = baseFont.getFontData();
    if (fontData == null || fontData.length == 0) {
      return baseFont;
    }
    FontData boldData = new FontData(fontData[0]);
    boldData.setStyle(boldData.getStyle() | SWT.BOLD);
    return new Font(display, boldData);
  }

  public String getDisplayText() {
    return styledText.getText();
  }

  public void scrollToTop() {
    if (styledText != null && !styledText.isDisposed()) {
      styledText.setTopIndex(0);
    }
  }

  public void setPlainText(String text) {
    String value = text != null ? text : "";
    if (Utils.isEmpty(value)) {
      styledText.setText("");
      clearStyleRanges();
      return;
    }
    setMarkdown(value);
  }
}
