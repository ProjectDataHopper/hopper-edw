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

import org.apache.hop.core.Const;
import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;

/** Markdown source editor with a rendered preview tab. */
public class MarkdownEditPreviewComposite extends Composite {

  private final StyledText editor;
  private final MarkdownStyledTextComp preview;

  public MarkdownEditPreviewComposite(
      Composite parent, int style, String editTabLabel, String previewTabLabel) {
    super(parent, style);
    PropsUi.setLook(this);
    setLayout(new FillLayout());

    CTabFolder folder = new CTabFolder(this, SWT.BORDER);
    PropsUi.setLook(folder, org.apache.hop.core.Props.WIDGET_STYLE_TAB);

    CTabItem editItem = new CTabItem(folder, SWT.NONE);
    editItem.setText(Const.NVL(editTabLabel, "Edit"));
    Composite editComp = new Composite(folder, SWT.NONE);
    PropsUi.setLook(editComp);
    editComp.setLayout(new FillLayout());
    editor =
        new StyledText(editComp, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
    PropsUi.setLook(editor, org.apache.hop.core.Props.WIDGET_STYLE_FIXED);
    editor.setMargins(4, 4, 4, 4);
    editItem.setControl(editComp);

    CTabItem previewItem = new CTabItem(folder, SWT.NONE);
    previewItem.setText(Const.NVL(previewTabLabel, "Preview"));
    Composite previewComp = new Composite(folder, SWT.NONE);
    PropsUi.setLook(previewComp);
    previewComp.setLayout(new FormLayout());
    preview = new MarkdownStyledTextComp(previewComp, SWT.NONE);
    org.eclipse.swt.layout.FormData fdPreview = new org.eclipse.swt.layout.FormData();
    fdPreview.left = new org.eclipse.swt.layout.FormAttachment(0, 0);
    fdPreview.right = new org.eclipse.swt.layout.FormAttachment(100, 0);
    fdPreview.top = new org.eclipse.swt.layout.FormAttachment(0, 0);
    fdPreview.bottom = new org.eclipse.swt.layout.FormAttachment(100, 0);
    preview.setLayoutData(fdPreview);
    previewItem.setControl(previewComp);

    folder.setSelection(0);
    folder.addListener(
        SWT.Selection,
        event -> {
          if (folder.getSelection() == previewItem) {
            preview.setMarkdown(editor.getText());
          }
        });
  }

  public String getText() {
    return editor.getText();
  }

  public void setText(String text) {
    editor.setText(Const.NVL(text, ""));
    preview.setMarkdown(editor.getText());
  }
}
