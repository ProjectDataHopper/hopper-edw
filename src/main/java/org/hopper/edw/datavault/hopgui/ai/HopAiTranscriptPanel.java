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
package org.hopper.edw.datavault.hopgui.ai;

import org.apache.hop.core.util.Utils;
import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.widget.MarkdownStyledTextComp;

/** Scrollable conversation transcript used by Hop AI advisory dialogs. */
public final class HopAiTranscriptPanel {

  private static final int USER_TEXT_MIN_HEIGHT = (int) (60 * PropsUi.getNativeZoomFactor());
  private static final int USER_TEXT_MAX_HEIGHT = (int) (100 * PropsUi.getNativeZoomFactor());
  private static final int ADVICE_TEXT_MIN_HEIGHT = (int) (140 * PropsUi.getNativeZoomFactor());
  private static final int ADVICE_TEXT_MAX_HEIGHT = (int) (420 * PropsUi.getNativeZoomFactor());
  private static final int TEXT_LINE_HEIGHT = (int) (16 * PropsUi.getNativeZoomFactor());

  private final Label conversationLabel;
  private final ScrolledComposite transcriptScroll;
  private final Composite transcriptContent;
  private Control lastControl;

  public HopAiTranscriptPanel(
      Composite parent,
      Control anchorTop,
      Control anchorBottom,
      int margin,
      String conversationLabelText) {
    conversationLabel = new Label(parent, SWT.LEFT);
    conversationLabel.setText(conversationLabelText);
    PropsUi.setLook(conversationLabel);
    FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.top = new FormAttachment(anchorTop, margin);
    conversationLabel.setLayoutData(fdLabel);

    transcriptScroll = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
    PropsUi.setLook(transcriptScroll);
    transcriptScroll.setExpandHorizontal(true);
    transcriptScroll.setExpandVertical(true);
    FormData fdScroll = new FormData();
    fdScroll.left = new FormAttachment(0, 0);
    fdScroll.right = new FormAttachment(100, 0);
    fdScroll.top = new FormAttachment(conversationLabel, margin);
    fdScroll.bottom = new FormAttachment(anchorBottom, -margin);
    transcriptScroll.setLayoutData(fdScroll);

    transcriptContent = new Composite(transcriptScroll, SWT.NONE);
    PropsUi.setLook(transcriptContent);
    transcriptContent.setLayout(new FormLayout());
    transcriptScroll.setContent(transcriptContent);
  }

  public Control appendHeading(String heading) {
    Label label = new Label(transcriptContent, SWT.LEFT);
    label.setText(heading);
    PropsUi.setLook(label);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, PropsUi.getMargin());
    fd.right = new FormAttachment(100, -PropsUi.getMargin());
    if (lastControl == null) {
      fd.top = new FormAttachment(0, PropsUi.getMargin());
    } else {
      fd.top = new FormAttachment(lastControl, PropsUi.getMargin());
    }
    label.setLayoutData(fd);
    lastControl = label;
    refreshScroll();
    return label;
  }

  public Button appendButton(String label) {
    Button button = new Button(transcriptContent, SWT.PUSH);
    button.setText(label);
    PropsUi.setLook(button);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, PropsUi.getMargin());
    fd.top = new FormAttachment(lastControl, PropsUi.getMargin());
    button.setLayoutData(fd);
    lastControl = button;
    refreshScroll();
    return button;
  }

  public Control appendSystemLine(String text) {
    Label label = new Label(transcriptContent, SWT.LEFT | SWT.WRAP);
    label.setText(text != null ? text : "");
    PropsUi.setLook(label);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, PropsUi.getMargin());
    fd.right = new FormAttachment(100, -PropsUi.getMargin());
    fd.top = new FormAttachment(lastControl, PropsUi.getMargin());
    label.setLayoutData(fd);
    lastControl = label;
    refreshScroll();
    return label;
  }

  public Control appendText(String text, boolean userMessage) {
    if (userMessage) {
      return appendPlainText(text, USER_TEXT_MIN_HEIGHT, USER_TEXT_MAX_HEIGHT);
    }
    return appendMarkdown(text);
  }

  public Control appendMarkdown(String markdown) {
    int width = Math.max(200, transcriptScroll.getClientArea().width - 2 * PropsUi.getMargin());
    MarkdownStyledTextComp markdownComp = new MarkdownStyledTextComp(transcriptContent, SWT.NONE);
    markdownComp.setMarkdown(markdown != null ? markdown : "");
    int height =
        Math.clamp(
            markdownComp.getPreferredHeight(width), ADVICE_TEXT_MIN_HEIGHT, ADVICE_TEXT_MAX_HEIGHT);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, PropsUi.getMargin());
    fd.right = new FormAttachment(100, -PropsUi.getMargin());
    fd.top = new FormAttachment(lastControl, PropsUi.getMargin());
    fd.height = height;
    markdownComp.setLayoutData(fd);
    lastControl = markdownComp;
    refreshScroll();
    return markdownComp;
  }

  private Control appendPlainText(String text, int minHeight, int maxHeight) {
    Text box =
        new Text(
            transcriptContent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.READ_ONLY | SWT.WRAP);
    box.setText(text != null ? text : "");
    PropsUi.setLook(box);
    int height = computeTextHeight(text, minHeight, maxHeight);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, PropsUi.getMargin());
    fd.right = new FormAttachment(100, -PropsUi.getMargin());
    fd.top = new FormAttachment(lastControl, PropsUi.getMargin());
    fd.height = height;
    box.setLayoutData(fd);
    lastControl = box;
    refreshScroll();
    return box;
  }

  public void clear() {
    for (Control child : transcriptContent.getChildren()) {
      child.dispose();
    }
    lastControl = null;
    refreshScroll();
  }

  public void refreshScroll() {
    if (transcriptScroll == null || transcriptScroll.isDisposed() || transcriptContent == null) {
      return;
    }
    transcriptContent.layout(true, true);
    int width = Math.max(200, transcriptScroll.getClientArea().width - 2);
    int contentHeight =
        Math.max(
            (int) (400 * PropsUi.getNativeZoomFactor()),
            transcriptContent.computeSize(width, SWT.DEFAULT).y);
    transcriptContent.setSize(width, contentHeight);
    transcriptScroll.setMinSize(width, contentHeight);
    transcriptScroll.layout(true, true);
    int viewportHeight = transcriptScroll.getClientArea().height;
    transcriptScroll.setOrigin(0, Math.max(0, contentHeight - viewportHeight));
  }

  private static int computeTextHeight(String text, int minHeight, int maxHeight) {
    if (Utils.isEmpty(text)) {
      return minHeight;
    }
    int lines = 0;
    for (String line : text.split("\n", -1)) {
      int wrapped = Math.max(1, (line.length() + 88) / 89);
      lines += wrapped;
    }
    int height = lines * TEXT_LINE_HEIGHT + 12;
    return Math.clamp(height, minHeight, maxHeight);
  }
}
