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
package org.apache.hop.datavault.metadata;

import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Record source options for database table bulk import. */
public final class RecordSourceIndicatorDatabaseImportSection {

  private static final Class<?> PKG = RecordSourceIndicatorDatabaseImportSection.class;

  private final Composite parent;
  private final int middle;
  private final int margin;

  private Button wStaticMode;
  private Button wFieldOrStaticMode;
  private Text wStaticOverride;
  private Control lastControl;

  public RecordSourceIndicatorDatabaseImportSection(
      Composite parent, int middle, int margin, Control attachAfter) {
    this.parent = parent;
    this.middle = middle;
    this.margin = margin;
    this.lastControl = attachAfter;
    build();
  }

  public Control getLastControl() {
    return lastControl;
  }

  public RecordSourceIndicatorOptions collectOptions() {
    if (wFieldOrStaticMode.getSelection()) {
      String staticFallback = Const.NVL(wStaticOverride.getText(), "").trim();
      RecordSourceIndicatorOptions options = RecordSourceIndicatorOptions.fieldOrStatic(null);
      if (!Utils.isEmpty(staticFallback)) {
        options.setStaticValue(staticFallback);
      }
      return options;
    }
    String staticValue = Const.NVL(wStaticOverride.getText(), "").trim();
    RecordSourceIndicatorOptions options = RecordSourceIndicatorOptions.staticValue(null);
    if (!Utils.isEmpty(staticValue)) {
      options.setStaticValue(staticValue);
    }
    return options;
  }

  private void build() {
    Label wlMode = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlMode);
    wlMode.setText(BaseMessages.getString(PKG, "RecordSourceIndicatorImportSection.Mode.Label"));
    FormData fdlMode = new FormData();
    fdlMode.left = new FormAttachment(0, 0);
    fdlMode.right = new FormAttachment(middle, -margin);
    fdlMode.top = new FormAttachment(lastControl, margin);
    wlMode.setLayoutData(fdlMode);

    Composite modeComp = new Composite(parent, SWT.NONE);
    PropsUi.setLook(modeComp);
    FormData fdModeComp = new FormData();
    fdModeComp.left = new FormAttachment(middle, 0);
    fdModeComp.right = new FormAttachment(100, 0);
    fdModeComp.top = new FormAttachment(wlMode, 0, SWT.CENTER);
    modeComp.setLayoutData(fdModeComp);
    modeComp.setLayout(new org.eclipse.swt.layout.RowLayout(SWT.HORIZONTAL));

    wStaticMode = new Button(modeComp, SWT.RADIO);
    PropsUi.setLook(wStaticMode);
    wStaticMode.setText(
        BaseMessages.getString(PKG, "RecordSourceIndicatorImportSection.Mode.Static"));
    wStaticMode.setSelection(true);

    wFieldOrStaticMode = new Button(modeComp, SWT.RADIO);
    PropsUi.setLook(wFieldOrStaticMode);
    wFieldOrStaticMode.setText(
        BaseMessages.getString(PKG, "RecordSourceIndicatorImportSection.Mode.FieldOrStatic"));
    lastControl = modeComp;

    Label wlOverride = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlOverride);
    wlOverride.setText(
        BaseMessages.getString(PKG, "RecordSourceIndicatorImportSection.StaticOverride.Label"));
    FormData fdlOverride = new FormData();
    fdlOverride.left = new FormAttachment(0, 0);
    fdlOverride.right = new FormAttachment(middle, -margin);
    fdlOverride.top = new FormAttachment(lastControl, margin);
    wlOverride.setLayoutData(fdlOverride);

    wStaticOverride = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wStaticOverride);
    FormData fdOverride = new FormData();
    fdOverride.left = new FormAttachment(middle, 0);
    fdOverride.right = new FormAttachment(100, 0);
    fdOverride.top = new FormAttachment(wlOverride, 0, SWT.CENTER);
    wStaticOverride.setLayoutData(fdOverride);
    lastControl = wStaticOverride;

    SelectionAdapter modeListener =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            updateModeEnabled();
          }
        };
    wStaticMode.addSelectionListener(modeListener);
    wFieldOrStaticMode.addSelectionListener(modeListener);
    updateModeEnabled();
  }

  private void updateModeEnabled() {
    wStaticOverride.setEnabled(true);
  }
}
