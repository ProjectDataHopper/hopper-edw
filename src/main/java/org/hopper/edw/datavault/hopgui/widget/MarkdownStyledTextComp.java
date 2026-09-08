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

import org.apache.hop.core.util.Utils;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

/**
 * Read-only markdown view. Desktop uses SWT {@code StyledText} style ranges; Hop Web uses a plain
 * {@code Text} widget because RAP does not ship {@code StyledText}. Font copies use {@code
 * FontData(String, int, int)} because RAP has no {@code FontData(FontData)} constructor.
 */
public class MarkdownStyledTextComp extends Composite {

  private final Control textControl;
  private final Text webText;
  private final Font fixedFont;
  private final Font boldFixedFont;
  private final boolean disposeFixedFont;
  private final boolean disposeBoldFixedFont;

  public MarkdownStyledTextComp(Composite parent, int style) {
    super(parent, style);
    PropsUi.setLook(this);
    setLayout(new FormLayout());

    GuiResource resources = GuiResource.getInstance();
    FixedFonts resolved = resolveFixedFonts(resources, getDisplay());
    fixedFont = resolved.fixedFont();
    boldFixedFont = resolved.boldFixedFont();
    disposeFixedFont = resolved.disposeFixedFont();
    disposeBoldFixedFont = resolved.disposeBoldFixedFont();

    if (EnvironmentUtils.getInstance().isWeb()) {
      webText = new Text(this, SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER);
      PropsUi.setLook(webText);
      applyWebFont();
      textControl = webText;
    } else {
      webText = null;
      textControl = MarkdownDesktopStyledText.createReadOnly(this);
      MarkdownDesktopStyledText.applyBaseFont(textControl, fixedFont);
    }

    FormData fdText = new FormData();
    fdText.left = new FormAttachment(0, 0);
    fdText.right = new FormAttachment(100, 0);
    fdText.top = new FormAttachment(0, 0);
    fdText.bottom = new FormAttachment(100, 0);
    textControl.setLayoutData(fdText);

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
    if (webText != null) {
      webText.setText(MarkdownStyleRenderer.render(markdown).displayText());
      return;
    }
    MarkdownDesktopStyledText.setMarkdown(textControl, markdown, fixedFont, boldFixedFont);
  }

  public int getPreferredHeight(int width) {
    if (width <= 0) {
      width = 400;
    }
    if (webText != null) {
      return Math.max(webText.computeSize(width, SWT.DEFAULT).y + 8, webText.getLineHeight());
    }
    return MarkdownDesktopStyledText.preferredHeight(textControl, width);
  }

  public String getDisplayText() {
    if (webText != null) {
      return webText.getText();
    }
    return MarkdownDesktopStyledText.getText(textControl);
  }

  public void scrollToTop() {
    if (webText != null) {
      if (!webText.isDisposed()) {
        webText.setTopIndex(0);
      }
      return;
    }
    MarkdownDesktopStyledText.scrollToTop(textControl);
  }

  public void setPlainText(String text) {
    String value = text != null ? text : "";
    if (webText != null) {
      if (Utils.isEmpty(value)) {
        webText.setText("");
        return;
      }
      setMarkdown(value);
      return;
    }
    MarkdownDesktopStyledText.setPlainText(textControl, value, fixedFont, boldFixedFont);
  }

  private void applyWebFont() {
    if (webText != null && !webText.isDisposed() && fixedFont != null && !fixedFont.isDisposed()) {
      webText.setFont(fixedFont);
    }
  }

  private record FixedFonts(
      Font fixedFont, Font boldFixedFont, boolean disposeFixedFont, boolean disposeBoldFixedFont) {}

  private static FixedFonts resolveFixedFonts(GuiResource resources, Display display) {
    Font fixed = resources.getFontFixed();
    boolean created = false;
    if (fixed == null) {
      fixed = new Font(display, new FontData("Monospace", 10, SWT.NORMAL));
      created = true;
    }
    // Hop Web never applies style ranges, so a second bold font is unused. Skip it so RAP does not
    // have to construct extra FontData at all.
    if (EnvironmentUtils.getInstance().isWeb()) {
      return new FixedFonts(fixed, null, created, false);
    }
    Font bold = deriveBoldFont(display, fixed);
    return new FixedFonts(fixed, bold, created, bold != fixed);
  }

  /**
   * RAP {@code FontData} has {@code FontData(String, int, int)} only. The SWT copy constructor
   * {@code FontData(FontData)} is a {@code NoSuchMethodError} on Hop Web.
   */
  private static Font deriveBoldFont(Display display, Font baseFont) {
    FontData[] fontData = baseFont.getFontData();
    if (fontData == null || fontData.length == 0) {
      return baseFont;
    }
    FontData source = fontData[0];
    return new Font(
        display, new FontData(source.getName(), source.getHeight(), source.getStyle() | SWT.BOLD));
  }
}
