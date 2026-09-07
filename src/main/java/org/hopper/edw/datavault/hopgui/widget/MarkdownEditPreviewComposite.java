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
import org.apache.hop.core.Props;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

/** Markdown source editor with a rendered preview tab. */
public class MarkdownEditPreviewComposite extends Composite {

  private final Control editor;
  private final Text webEditor;
  private final MarkdownStyledTextComp preview;

  public MarkdownEditPreviewComposite(
      Composite parent, int style, String editTabLabel, String previewTabLabel) {
    super(parent, style);
    PropsUi.setLook(this);
    setLayout(new FillLayout());

    CTabFolder folder = new CTabFolder(this, SWT.BORDER);
    PropsUi.setLook(folder, Props.WIDGET_STYLE_TAB);

    CTabItem editItem = new CTabItem(folder, SWT.NONE);
    editItem.setText(Const.NVL(editTabLabel, "Edit"));
    Composite editComp = new Composite(folder, SWT.NONE);
    PropsUi.setLook(editComp);
    editComp.setLayout(new FillLayout());
    if (EnvironmentUtils.getInstance().isWeb()) {
      webEditor =
          new Text(editComp, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
      PropsUi.setLook(webEditor, Props.WIDGET_STYLE_FIXED);
      editor = webEditor;
    } else {
      webEditor = null;
      editor = MarkdownDesktopStyledText.createEditor(editComp);
    }
    editItem.setControl(editComp);

    CTabItem previewItem = new CTabItem(folder, SWT.NONE);
    previewItem.setText(Const.NVL(previewTabLabel, "Preview"));
    Composite previewComp = new Composite(folder, SWT.NONE);
    PropsUi.setLook(previewComp);
    previewComp.setLayout(new FormLayout());
    preview = new MarkdownStyledTextComp(previewComp, SWT.NONE);
    FormData fdPreview = new FormData();
    fdPreview.left = new FormAttachment(0, 0);
    fdPreview.right = new FormAttachment(100, 0);
    fdPreview.top = new FormAttachment(0, 0);
    fdPreview.bottom = new FormAttachment(100, 0);
    preview.setLayoutData(fdPreview);
    previewItem.setControl(previewComp);

    folder.setSelection(0);
    folder.addListener(
        SWT.Selection,
        event -> {
          if (folder.getSelection() == previewItem) {
            preview.setMarkdown(getText());
          }
        });
  }

  public String getText() {
    if (webEditor != null) {
      return webEditor.getText();
    }
    return MarkdownDesktopStyledText.getText(editor);
  }

  public void setText(String text) {
    String value = Const.NVL(text, "");
    if (webEditor != null) {
      webEditor.setText(value);
    } else {
      MarkdownDesktopStyledText.setText(editor, value);
    }
    preview.setMarkdown(value);
  }
}
