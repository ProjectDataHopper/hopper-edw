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
package org.hopper.edw.datavault.hopgui.file.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceCalendar;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceCalendarEntry;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceCalendarMode;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.hopper.edw.datavault.naming.EdwNamingWidgetSupport;

/** Dialog for a Business Vault source calendar. The window grid is the body; buttons stay below it. */
public class HopGuiBvSourceCalendarDialog {

  private static final Class<?> PKG = HopGuiBvSourceCalendarDialog.class;

  private static final String[] MODE_CODES = new String[] {"", "full", "delta", "seed"};

  private final Shell parent;
  private final BvSourceCalendar input;
  private final BusinessVaultModel businessVaultModel;
  private final DataVaultModel dataVaultModel;
  private final IVariables variables;
  private final int originalTableIndex;

  private Shell shell;
  private Text wName;
  private Text wDescription;
  private TableView wEntries;
  private boolean ok;

  public HopGuiBvSourceCalendarDialog(
      Shell parent,
      BvSourceCalendar calendar,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    this.parent = parent;
    this.input = calendar;
    this.businessVaultModel = businessVaultModel;
    this.dataVaultModel = dataVaultModel;
    this.variables = variables;
    this.originalTableIndex =
        businessVaultModel != null ? businessVaultModel.getTables().indexOf(calendar) : -1;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiBvSourceCalendarDialog.Title", Const.NVL(input.getName(), "")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(
            ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> validate());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.BV_SOURCE_CALENDAR);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Name.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    EdwNamingWidgetSupport.enableAndLayout(
        wName,
        variables,
        EdwNamingSchemeTypes.BV_SOURCE_CALENDAR,
        new FormDataBuilder().left(middle, 0).top(0, margin).right().result());

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(
        BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wName, margin).right(middle, -margin).result());

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wName, margin).right().result());

    Label wlIntro = new Label(shell, SWT.LEFT | SWT.WRAP);
    wlIntro.setText(BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Intro"));
    PropsUi.setLook(wlIntro);
    wlIntro.setLayoutData(new FormDataBuilder().left().top(wDescription, margin).right().result());

    Button wAdd = new Button(shell, SWT.PUSH);
    wAdd.setText(BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Entries.Add"));
    PropsUi.setLook(wAdd);
    wAdd.setLayoutData(new FormDataBuilder().left().top(wlIntro, margin).result());
    wAdd.addListener(SWT.Selection, e -> addEntryRow());

    Button wDelete = new Button(shell, SWT.PUSH);
    wDelete.setText(BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Entries.Delete"));
    PropsUi.setLook(wDelete);
    wDelete.setLayoutData(new FormDataBuilder().left(wAdd, margin).top(wlIntro, margin).result());
    wDelete.addListener(SWT.Selection, e -> removeEntryRows());

    ColumnInfo sourceIdColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Column.SourceId"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    sourceIdColumn.setToolTip(
        BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Column.SourceId.Tooltip"));
    ColumnInfo effectiveFromColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Column.EffectiveFrom"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    effectiveFromColumn.setToolTip(
        BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Column.EffectiveFrom.Tooltip"));
    ColumnInfo effectiveToColumn =
        new ColumnInfo(
            BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Column.EffectiveTo"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    effectiveToColumn.setToolTip(
        BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Column.EffectiveTo.Tooltip"));
    ColumnInfo[] columns =
        new ColumnInfo[] {
          sourceIdColumn,
          effectiveFromColumn,
          effectiveToColumn,
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Column.Priority"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.Column.Mode"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              MODE_CODES,
              false),
        };
    wEntries =
        new TableView(
            variables, shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI, columns, 1, null, PropsUi.getInstance());
    wEntries.setLayoutData(
        new FormDataBuilder().left().top(wAdd, margin).right().bottom(wOk, -margin).result());

    getData();
    BaseTransformDialog.setSize(shell, 760, 560);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void addEntryRow() {
    new TableItem(wEntries.table, SWT.NONE);
    wEntries.optimizeTableView();
  }

  private void removeEntryRows() {
    int index = wEntries.getSelectionIndex();
    if (index >= 0) {
      wEntries.table.remove(index);
      wEntries.optimizeTableView();
    }
  }

  private void getData() {
    if (!Utils.isEmpty(input.getName())) {
      wName.setText(input.getName());
    }
    if (!Utils.isEmpty(input.getDescription())) {
      wDescription.setText(input.getDescription());
    }
    wEntries.clearAll();
    for (BvSourceCalendarEntry entry : input.getEntries()) {
      if (entry == null) {
        continue;
      }
      TableItem item = new TableItem(wEntries.table, SWT.NONE);
      item.setText(1, Const.NVL(entry.getSourceId(), ""));
      item.setText(2, Const.NVL(entry.getEffectiveFrom(), ""));
      item.setText(3, Const.NVL(entry.getEffectiveTo(), ""));
      item.setText(4, Const.NVL(entry.getPriority(), ""));
      item.setText(5, entry.getMode() == null ? "" : entry.getMode().getCode());
    }
    wEntries.optimizeTableView();
  }

  private void ok() {
    String newName = Const.trim(wName.getText());
    if (Utils.isEmpty(newName)) {
      showWarning(
          "HopGuiBvSourceCalendarDialog.NameRequired.Title",
          BaseMessages.getString(PKG, "HopGuiBvSourceCalendarDialog.NameRequired.Message"));
      return;
    }
    if (businessVaultModel != null && businessVaultModel.hasOtherTableNamed(input, newName)) {
      showWarning(
          "HopGuiBvSourceCalendarDialog.DuplicateName.Title",
          BaseMessages.getString(
              PKG, "HopGuiBvSourceCalendarDialog.DuplicateName.Message", newName));
      return;
    }
    applyWidgets(input);
    ok = true;
    dispose();
  }

  private void validate() {
    try {
      List<ICheckResult> remarks =
          ModelDialogValidationSupport.runChecksWithBusyCursor(
              shell,
              () -> {
                BusinessVaultModel draft =
                    ModelDialogValidationSupport.cloneBusinessVaultModel(
                        businessVaultModel, HopGui.getInstance().getMetadataProvider());
                IBvTable draftTable = locateDraftTable(draft);
                applyWidgets(draftTable);
                List<ICheckResult> tableRemarks = new ArrayList<>();
                draftTable.check(
                    tableRemarks,
                    HopGui.getInstance().getMetadataProvider(),
                    variables,
                    draft,
                    dataVaultModel);
                return tableRemarks;
              });
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception ex) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"),
          BaseMessages.getString(
              ModelDialogValidationSupport.class,
              "ModelTableDialog.Validate.Error",
              ex.getMessage()),
          ex);
    }
  }

  private IBvTable locateDraftTable(BusinessVaultModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    return draft.getTables().get(originalTableIndex);
  }

  private void applyWidgets(IBvTable target) {
    target.setName(wName.getText());
    target.setDescription(wDescription.getText());
    if (!(target instanceof BvSourceCalendar calendar)) {
      return;
    }
    calendar.getEntries().clear();
    for (TableItem item : wEntries.getNonEmptyItems()) {
      String sourceId = item.getText(1);
      String effectiveFrom = item.getText(2);
      String effectiveTo = item.getText(3);
      String priority = item.getText(4);
      String mode = item.getText(5);
      if (Utils.isEmpty(sourceId)
          && Utils.isEmpty(effectiveFrom)
          && Utils.isEmpty(effectiveTo)
          && Utils.isEmpty(priority)
          && Utils.isEmpty(mode)) {
        continue;
      }
      BvSourceCalendarEntry entry = new BvSourceCalendarEntry();
      entry.setSourceId(Const.trim(sourceId));
      entry.setEffectiveFrom(Const.trim(effectiveFrom));
      entry.setEffectiveTo(Const.trim(effectiveTo));
      entry.setPriority(Const.trim(priority));
      entry.setMode(BvSourceCalendarMode.lookupCode(mode));
      calendar.getEntries().add(entry);
    }
  }

  private void showWarning(String titleKey, String message) {
    MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
    box.setText(BaseMessages.getString(PKG, titleKey));
    box.setMessage(Const.NVL(message, ""));
    box.open();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    shell.dispose();
  }
}
