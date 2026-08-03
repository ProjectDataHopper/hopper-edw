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
package org.apache.hop.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.profile.SourceRelationshipProfileOptions;
import org.apache.hop.datavault.metadata.sourcemodel.profile.SourceRelationshipProfileResult;
import org.apache.hop.datavault.metadata.sourcemodel.profile.SourceRelationshipProfileStrategy;
import org.apache.hop.datavault.metadata.sourcemodel.profile.SourceRelationshipProfiler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit a {@link SourceRelationship} (child→parent join definition). */
public class HopGuiSourceRelationshipDialog {

  private static final Class<?> PKG = HopGuiSourceRelationshipDialog.class;

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final SourceModel model;
  private final SourceRelationship input;
  private Shell shell;

  private Text wName;
  private Text wDescription;
  private Combo wChildTable;
  private Combo wParentTable;
  private Combo wJoinType;
  private Combo wChildMultiplicity;
  private Combo wParentMultiplicity;
  private TableView wJoinColumns;

  private boolean ok;

  public HopGuiSourceRelationshipDialog(
      Shell parent, SourceRelationship relationship, SourceModel model, IVariables variables) {
    this(parent, relationship, model, variables, null);
  }

  public HopGuiSourceRelationshipDialog(
      Shell parent,
      SourceRelationship relationship,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.input = relationship;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiSourceRelationshipDialog.Title", Const.NVL(input.getName(), "")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    Button wProfile = new Button(shell, SWT.PUSH);
    wProfile.setText(BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.Profile.Button"));
    wProfile.addListener(SWT.Selection, e -> profileRelationship());
    wProfile.setEnabled(metadataProvider != null);
    DialogHelpSupport.createHelpButton(shell, HelpTopics.IMPORT_DATABASE_TABLES_OPTIONS);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wCancel, wProfile}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());
    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    wName.setLayoutData(new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(
        BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());
    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    String[] tableNames = tableNames();

    Label wlChild = new Label(shell, SWT.RIGHT);
    wlChild.setText(BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.ChildTable.Label"));
    PropsUi.setLook(wlChild);
    wlChild.setLayoutData(
        new FormDataBuilder().left().top(wDescription, margin).right(middle, -margin).result());
    wChildTable = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wChildTable);
    wChildTable.setItems(tableNames);
    wChildTable.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wDescription, margin).right().result());

    Label wlParent = new Label(shell, SWT.RIGHT);
    wlParent.setText(
        BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.ParentTable.Label"));
    PropsUi.setLook(wlParent);
    wlParent.setLayoutData(
        new FormDataBuilder().left().top(wChildTable, margin).right(middle, -margin).result());
    wParentTable = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wParentTable);
    wParentTable.setItems(tableNames);
    wParentTable.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wChildTable, margin).right().result());

    Label wlJoinType = new Label(shell, SWT.RIGHT);
    wlJoinType.setText(
        BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.JoinType.Label"));
    PropsUi.setLook(wlJoinType);
    wlJoinType.setLayoutData(
        new FormDataBuilder().left().top(wParentTable, margin).right(middle, -margin).result());
    wJoinType = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wJoinType);
    wJoinType.setItems(SourceJoinType.getDescriptions());
    wJoinType.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wParentTable, margin).right().result());

    Label wlChildMult = new Label(shell, SWT.RIGHT);
    wlChildMult.setText(
        BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.ChildMultiplicity.Label"));
    PropsUi.setLook(wlChildMult);
    wlChildMult.setLayoutData(
        new FormDataBuilder().left().top(wJoinType, margin).right(middle, -margin).result());
    wChildMultiplicity = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wChildMultiplicity);
    wChildMultiplicity.setItems(SourceRelationshipMultiplicity.getDescriptions());
    wChildMultiplicity.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.ChildMultiplicity.ToolTip"));
    wChildMultiplicity.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wJoinType, margin).right().result());

    Label wlParentMult = new Label(shell, SWT.RIGHT);
    wlParentMult.setText(
        BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.ParentMultiplicity.Label"));
    PropsUi.setLook(wlParentMult);
    wlParentMult.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wChildMultiplicity, margin)
            .right(middle, -margin)
            .result());
    wParentMultiplicity = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wParentMultiplicity);
    wParentMultiplicity.setItems(SourceRelationshipMultiplicity.getDescriptions());
    wParentMultiplicity.setToolTipText(
        BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.ParentMultiplicity.ToolTip"));
    wParentMultiplicity.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wChildMultiplicity, margin).right().result());

    Label wlJoinColumns = new Label(shell, SWT.LEFT);
    wlJoinColumns.setText(
        BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.JoinColumns.Label"));
    PropsUi.setLook(wlJoinColumns);
    wlJoinColumns.setLayoutData(
        new FormDataBuilder().left().top(wParentMultiplicity, margin * 2).right().result());

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.JoinColumns.Child"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.JoinColumns.Parent"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    int rowCount =
        Math.max(input.getChildColumns().size(), Math.max(input.getParentColumns().size(), 1));
    wJoinColumns =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            rowCount,
            null,
            PropsUi.getInstance());
    wJoinColumns.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wlJoinColumns, margin)
            .right()
            .bottom(new FormAttachment(wOk, -margin))
            .result());

    getData();
    BaseTransformDialog.setSize(shell, 640, 480);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private String[] tableNames() {
    if (model == null) {
      return new String[0];
    }
    List<String> names = new ArrayList<>();
    for (SourceTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        names.add(table.getName());
      }
    }
    return names.toArray(new String[0]);
  }

  private void getData() {
    wName.setText(Const.NVL(input.getName(), ""));
    wDescription.setText(Const.NVL(input.getDescription(), ""));
    wChildTable.setText(Const.NVL(input.getChildTableName(), ""));
    wParentTable.setText(Const.NVL(input.getParentTableName(), ""));
    EnumDialogSupport.selectCombo(wJoinType, input.resolveDefaultJoinType());
    EnumDialogSupport.selectCombo(wChildMultiplicity, input.resolveChildMultiplicity());
    EnumDialogSupport.selectCombo(wParentMultiplicity, input.resolveParentMultiplicity());

    wJoinColumns.clearAll(false);
    int pairs = Math.max(input.getChildColumns().size(), input.getParentColumns().size());
    for (int i = 0; i < pairs; i++) {
      TableItem item = new TableItem(wJoinColumns.table, SWT.NONE);
      if (i < input.getChildColumns().size()) {
        item.setText(1, Const.NVL(input.getChildColumns().get(i), ""));
      }
      if (i < input.getParentColumns().size()) {
        item.setText(2, Const.NVL(input.getParentColumns().get(i), ""));
      }
    }
    if (pairs == 0) {
      new TableItem(wJoinColumns.table, SWT.NONE);
    }
    wJoinColumns.optimizeTableView();
  }

  private void ok() {
    String name = wName.getText().trim();
    if (Utils.isEmpty(name)
        || Utils.isEmpty(wChildTable.getText())
        || Utils.isEmpty(wParentTable.getText())) {
      return;
    }
    SourceRelationship existing = model != null ? model.findRelationship(name) : null;
    if (existing != null && existing != input) {
      return;
    }

    input.setName(name);
    input.setDescription(wDescription.getText());
    input.setChildTableName(wChildTable.getText());
    input.setParentTableName(wParentTable.getText());
    input.setDefaultJoinType(SourceJoinType.lookupDescription(wJoinType.getText()));
    input.setChildMultiplicity(
        SourceRelationshipMultiplicity.lookupDescription(wChildMultiplicity.getText()));
    input.setParentMultiplicity(
        SourceRelationshipMultiplicity.lookupDescription(wParentMultiplicity.getText()));
    // Keep legacy field in sync for older tools reading free-text cardinality.
    input.setCardinality(
        input.resolveChildMultiplicity().compactLabel()
            + ":"
            + input.resolveParentMultiplicity().compactLabel());

    List<String> childCols = new ArrayList<>();
    List<String> parentCols = new ArrayList<>();
    int rows = wJoinColumns.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wJoinColumns.getNonEmpty(i);
      String child = item.getText(1);
      String parentCol = item.getText(2);
      if (Utils.isEmpty(child) && Utils.isEmpty(parentCol)) {
        continue;
      }
      childCols.add(Const.NVL(child, "").trim());
      parentCols.add(Const.NVL(parentCol, "").trim());
    }
    input.setChildColumns(childCols);
    input.setParentColumns(parentCols);
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void profileRelationship() {
    if (metadataProvider == null || model == null) {
      return;
    }
    // Apply current dialog fields so profiling uses latest join columns.
    input.setChildTableName(wChildTable.getText());
    input.setParentTableName(wParentTable.getText());
    List<String> childCols = new ArrayList<>();
    List<String> parentCols = new ArrayList<>();
    int rows = wJoinColumns.nrNonEmpty();
    for (int i = 0; i < rows; i++) {
      TableItem item = wJoinColumns.getNonEmpty(i);
      String child = item.getText(1);
      String parentCol = item.getText(2);
      if (Utils.isEmpty(child) && Utils.isEmpty(parentCol)) {
        continue;
      }
      childCols.add(Const.NVL(child, "").trim());
      parentCols.add(Const.NVL(parentCol, "").trim());
    }
    input.setChildColumns(childCols);
    input.setParentColumns(parentCols);
    if (!input.isValid()) {
      MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
      box.setText(
          BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.Profile.Invalid.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.Profile.Invalid.Message"));
      box.open();
      return;
    }
    try {
      SourceRelationshipProfileOptions options = SourceRelationshipProfileOptions.defaults();
      // Strategy is auto-recommended inside profiler; force auto by leaving EXACT_KEY default
      // and letting size gates downgrade.
      options.setStrategy(SourceRelationshipProfileStrategy.EXACT_KEY);
      SourceRelationshipProfileResult result =
          SourceRelationshipProfiler.profile(model, input, variables, metadataProvider, options);
      EnumDialogSupport.selectCombo(wChildMultiplicity, result.getChildMultiplicity());
      EnumDialogSupport.selectCombo(wParentMultiplicity, result.getParentMultiplicity());
      StringBuilder msg = new StringBuilder();
      msg.append(
          BaseMessages.getString(
              PKG,
              "HopGuiSourceRelationshipDialog.Profile.Result.Summary",
              result.getChildMultiplicity().getDescription(),
              result.getParentMultiplicity().getDescription(),
              result.getConfidence().name()));
      for (String line : result.getMessages()) {
        msg.append(Const.CR).append(line);
      }
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(
          BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.Profile.Result.Title"));
      box.setMessage(msg.toString());
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.Profile.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceRelationshipDialog.Profile.Error.Message"),
          e);
    }
  }

  private void dispose() {
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
    shell.dispose();
  }
}
