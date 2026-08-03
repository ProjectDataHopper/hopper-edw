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
package org.apache.hop.datavault.metadata;

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorOptions;
import org.apache.hop.datavault.catalog.RecordSourceIndicatorSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Reusable SWT section for choosing static record source vs source column during import. */
public final class RecordSourceIndicatorImportSection {

  private static final Class<?> PKG = RecordSourceIndicatorImportSection.class;

  private final Composite parent;
  private final int middle;
  private final int margin;
  private final List<String> discoveredFieldNames;
  private final String defaultStaticValue;

  private Button wStaticMode;
  private Button wFieldMode;
  private Text wStaticValue;
  private Combo wSourceField;
  private Control lastControl;

  public RecordSourceIndicatorImportSection(
      Composite parent,
      int middle,
      int margin,
      List<String> discoveredFieldNames,
      String defaultStaticValue,
      Control attachAfter) {
    this.parent = parent;
    this.middle = middle;
    this.margin = margin;
    this.discoveredFieldNames = discoveredFieldNames;
    this.defaultStaticValue = defaultStaticValue;
    this.lastControl = attachAfter;
    build();
  }

  public Control getLastControl() {
    return lastControl;
  }

  public RecordSourceIndicatorOptions collectOptions(String fallbackStaticValue) {
    if (wFieldMode.getSelection()) {
      String fieldName = Const.NVL(wSourceField.getText(), "").trim();
      if (Utils.isEmpty(fieldName)) {
        return null;
      }
      return RecordSourceIndicatorOptions.fieldName(fieldName);
    }
    String staticValue = Const.NVL(wStaticValue.getText(), "").trim();
    if (Utils.isEmpty(staticValue)) {
      staticValue = fallbackStaticValue;
    }
    return RecordSourceIndicatorOptions.staticValue(staticValue);
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

    wFieldMode = new Button(modeComp, SWT.RADIO);
    PropsUi.setLook(wFieldMode);
    wFieldMode.setText(
        BaseMessages.getString(PKG, "RecordSourceIndicatorImportSection.Mode.Field"));
    lastControl = modeComp;

    Label wlStatic = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlStatic);
    wlStatic.setText(
        BaseMessages.getString(PKG, "RecordSourceIndicatorImportSection.StaticValue.Label"));
    FormData fdlStatic = new FormData();
    fdlStatic.left = new FormAttachment(0, 0);
    fdlStatic.right = new FormAttachment(middle, -margin);
    fdlStatic.top = new FormAttachment(lastControl, margin);
    wlStatic.setLayoutData(fdlStatic);

    wStaticValue = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wStaticValue);
    wStaticValue.setText(Const.NVL(defaultStaticValue, ""));
    FormData fdStatic = new FormData();
    fdStatic.left = new FormAttachment(middle, 0);
    fdStatic.right = new FormAttachment(100, 0);
    fdStatic.top = new FormAttachment(wlStatic, 0, SWT.CENTER);
    wStaticValue.setLayoutData(fdStatic);
    lastControl = wStaticValue;

    Label wlField = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlField);
    wlField.setText(
        BaseMessages.getString(PKG, "RecordSourceIndicatorImportSection.SourceField.Label"));
    FormData fdlField = new FormData();
    fdlField.left = new FormAttachment(0, 0);
    fdlField.right = new FormAttachment(middle, -margin);
    fdlField.top = new FormAttachment(lastControl, margin);
    wlField.setLayoutData(fdlField);

    wSourceField = new Combo(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wSourceField);
    if (discoveredFieldNames != null) {
      for (String fieldName : discoveredFieldNames) {
        if (!Utils.isEmpty(fieldName)) {
          wSourceField.add(fieldName);
        }
      }
    }
    String suggested = RecordSourceIndicatorSupport.suggestRecordSourceField(discoveredFieldNames);
    if (!Utils.isEmpty(suggested)) {
      wSourceField.setText(suggested);
    } else if (wSourceField.getItemCount() > 0) {
      wSourceField.select(0);
    }
    FormData fdField = new FormData();
    fdField.left = new FormAttachment(middle, 0);
    fdField.right = new FormAttachment(100, 0);
    fdField.top = new FormAttachment(wlField, 0, SWT.CENTER);
    wSourceField.setLayoutData(fdField);
    lastControl = wSourceField;

    SelectionAdapter modeListener =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            updateModeEnabled();
          }
        };
    wStaticMode.addSelectionListener(modeListener);
    wFieldMode.addSelectionListener(modeListener);
    updateModeEnabled();
  }

  private void updateModeEnabled() {
    boolean staticMode = wStaticMode.getSelection();
    wStaticValue.setEnabled(staticMode);
    wSourceField.setEnabled(!staticMode);
  }
}
