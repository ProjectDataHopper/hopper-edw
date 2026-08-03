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
package org.apache.hop.datavault.hopgui.coaching;

import java.util.Arrays;
import java.util.List;
import org.apache.hop.datavault.metadata.coaching.BvCoachingModelAdapter;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceRef;
import org.apache.hop.datavault.metadata.coaching.DmCoachingModelAdapter;
import org.apache.hop.datavault.metadata.coaching.DvCoachingModelAdapter;
import org.apache.hop.datavault.metadata.coaching.ICoachingModelAdapter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class CoachingTableTypeDialog {

  private final Shell parent;
  private final ICoachingModelAdapter adapter;
  private final CoachingSourceRef sourceRef;

  private Shell shell;
  private Combo wTableType;
  private Text wTableName;
  private boolean cancelled = true;
  private String selectedTableType;
  private String selectedTableName;

  public CoachingTableTypeDialog(
      Shell parent, ICoachingModelAdapter adapter, CoachingSourceRef sourceRef) {
    this.parent = parent;
    this.adapter = adapter;
    this.sourceRef = sourceRef;
  }

  public boolean open() {
    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.SHEET);
    shell.setText("Create table from coaching source");
    shell.setLayout(new FormLayout());
    PropsUi.setLook(shell);

    Label wlType = new Label(shell, SWT.RIGHT);
    wlType.setText("Table type");
    PropsUi.setLook(wlType);
    FormData fdWlType = new FormData();
    fdWlType.left = new FormAttachment(0, 0);
    fdWlType.top = new FormAttachment(0, PropsUi.getMargin());
    wlType.setLayoutData(fdWlType);

    wTableType = new Combo(shell, SWT.READ_ONLY);
    PropsUi.setLook(wTableType);
    for (String type : tableTypesForAdapter()) {
      wTableType.add(type);
    }
    if (wTableType.getItemCount() > 0) {
      wTableType.select(0);
    }
    FormData fdType = new FormData();
    fdType.left = new FormAttachment(wlType, PropsUi.getMargin());
    fdType.top = new FormAttachment(0, PropsUi.getMargin());
    fdType.right = new FormAttachment(100, -PropsUi.getMargin());
    wTableType.setLayoutData(fdType);

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText("Table name");
    PropsUi.setLook(wlName);
    FormData fdWlName = new FormData();
    fdWlName.left = new FormAttachment(0, 0);
    fdWlName.top = new FormAttachment(wTableType, PropsUi.getMargin());
    wlName.setLayoutData(fdWlName);

    wTableName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    wTableName.setText(suggestTableName());
    FormData fdName = new FormData();
    fdName.left = new FormAttachment(wlName, PropsUi.getMargin());
    fdName.top = new FormAttachment(wTableType, PropsUi.getMargin());
    fdName.right = new FormAttachment(100, -PropsUi.getMargin());
    wTableName.setLayoutData(fdName);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(
        BaseMessages.getString(org.apache.hop.ui.core.dialog.BaseDialog.class, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(
        BaseMessages.getString(
            org.apache.hop.ui.core.dialog.BaseDialog.class, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wCancel}, PropsUi.getMargin(), wTableName);
    BaseTransformDialog.setSize(shell, 420, 180);
    shell.addListener(
        SWT.Traverse,
        e -> {
          if (e.detail == SWT.TRAVERSE_ESCAPE) {
            e.doit = false;
            cancel();
          }
        });
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return !cancelled;
  }

  public String getSelectedTableType() {
    return selectedTableType;
  }

  public String getSelectedTableName() {
    return selectedTableName;
  }

  private List<String> tableTypesForAdapter() {
    if (adapter instanceof DvCoachingModelAdapter) {
      return Arrays.asList("HUB", "LINK", "SATELLITE");
    }
    if (adapter instanceof BvCoachingModelAdapter) {
      return Arrays.asList("SCD2", "PIT");
    }
    if (adapter instanceof DmCoachingModelAdapter) {
      return Arrays.asList("DIMENSION", "FACT", "BRIDGE", "JUNK_DIMENSION");
    }
    return List.of();
  }

  private String suggestTableName() {
    if (sourceRef == null) {
      return "new_table";
    }
    String label = sourceRef.resolvedDisplayLabel();
    return label == null ? "new_table" : label.replace(' ', '_').toLowerCase();
  }

  private void ok() {
    int typeIndex = wTableType.getSelectionIndex();
    selectedTableType =
        typeIndex >= 0 ? wTableType.getItem(typeIndex).trim() : wTableType.getText().trim();
    selectedTableName = wTableName.getText().trim();
    cancelled = false;
    shell.dispose();
  }

  private void cancel() {
    cancelled = true;
    shell.dispose();
  }
}
