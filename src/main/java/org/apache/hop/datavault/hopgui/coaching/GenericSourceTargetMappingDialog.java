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

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceRef;
import org.apache.hop.datavault.metadata.coaching.ICoachingModelAdapter;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/** Applies coaching bindings then opens the modeler table editor for refinement. */
public class GenericSourceTargetMappingDialog {

  private final HopGui hopGui;
  private final ICoachingModelAdapter adapter;
  private final CoachingSourceRef sourceRef;
  private final IVariables variables;
  private final String targetTableName;

  public GenericSourceTargetMappingDialog(
      HopGui hopGui,
      ICoachingModelAdapter adapter,
      CoachingSourceRef sourceRef,
      IVariables variables,
      String targetTableName) {
    this.hopGui = hopGui;
    this.adapter = adapter;
    this.sourceRef = sourceRef;
    this.variables = variables;
    this.targetTableName = targetTableName;
  }

  public void open() {
    String tableName = targetTableName;
    if (Utils.isEmpty(tableName)) {
      tableName = promptForTableName(hopGui.getShell(), variables);
      if (Utils.isEmpty(tableName)) {
        return;
      }
    }
    try {
      CoachingMappingApplier.apply(
          adapter, sourceRef, tableName, variables, hopGui.getMetadataProvider());
      adapter.openTableEditor(tableName);
    } catch (Exception e) {
      new ErrorDialog(hopGui.getShell(), "Map coaching source", e.getMessage(), e);
    }
  }

  private static String promptForTableName(Shell parent, IVariables variables) {
    Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.SHEET);
    dialog.setText("Map coaching source to table");
    dialog.setLayout(new FormLayout());
    Label wl = new Label(dialog, SWT.RIGHT);
    wl.setText("Target table name");
    FormData fdWl = new FormData();
    fdWl.left = new FormAttachment(0, 0);
    fdWl.top = new FormAttachment(0, 10);
    wl.setLayoutData(fdWl);
    TextVar wName = new TextVar(variables, dialog, SWT.SINGLE | SWT.BORDER);
    FormData fdName = new FormData();
    fdName.left = new FormAttachment(wl, 10);
    fdName.top = new FormAttachment(0, 10);
    fdName.right = new FormAttachment(100, -10);
    wName.setLayoutData(fdName);
    final String[] result = new String[1];
    org.eclipse.swt.widgets.Button wOk = new org.eclipse.swt.widgets.Button(dialog, SWT.PUSH);
    wOk.setText("OK");
    org.eclipse.swt.widgets.Button wCancel = new org.eclipse.swt.widgets.Button(dialog, SWT.PUSH);
    wCancel.setText("Cancel");
    wOk.addListener(
        SWT.Selection,
        e -> {
          result[0] = wName.getText();
          dialog.dispose();
        });
    wCancel.addListener(SWT.Selection, e -> dialog.dispose());
    dialog.pack();
    dialog.open();
    while (!dialog.isDisposed()) {
      if (!parent.getDisplay().readAndDispatch()) {
        parent.getDisplay().sleep();
      }
    }
    return result[0];
  }
}
