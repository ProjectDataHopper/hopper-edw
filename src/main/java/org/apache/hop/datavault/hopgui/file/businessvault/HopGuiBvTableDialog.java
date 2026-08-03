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
package org.apache.hop.datavault.hopgui.file.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.hopgui.lineage.LineageTabSupport;
import org.apache.hop.datavault.lineage.BvModelLineageCollector;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineage.TableLineage;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDerivativeSupport;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvDerivativeRef;
import org.apache.hop.datavault.metadata.businessvault.BvPitCadence;
import org.apache.hop.datavault.metadata.businessvault.BvPitRangeEnd;
import org.apache.hop.datavault.metadata.businessvault.BvPitRangeStart;
import org.apache.hop.datavault.metadata.businessvault.BvPitSnapshotAnchor;
import org.apache.hop.datavault.metadata.businessvault.BvPitSnapshotSchedule;
import org.apache.hop.datavault.metadata.businessvault.BvPitTable;
import org.apache.hop.datavault.metadata.businessvault.BvTableType;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Dialog to edit a Business Vault table on the canvas. */
public class HopGuiBvTableDialog {
  private static final Class<?> PKG = HopGuiBvTableDialog.class;

  private final Shell parent;
  private final IBvTable input;
  private final BusinessVaultModel businessVaultModel;
  private final DataVaultModel dataVaultModel;
  private final IVariables variables;
  private final int originalTableIndex;
  private Shell shell;

  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private Text wSnapshotDateField;
  private Label wlSnapshotDateField;
  private Combo wCadence;
  private Label wlCadence;
  private Combo wSnapshotAnchor;
  private Label wlSnapshotAnchor;
  private Text wHorizonDays;
  private Label wlHorizonDays;
  private Combo wRangeStart;
  private Label wlRangeStart;
  private Text wRangeStartFixed;
  private Label wlRangeStartFixed;
  private Combo wRangeEnd;
  private Label wlRangeEnd;
  private Text wRangeEndFixed;
  private Label wlRangeEndFixed;
  private Text wPointerSuffix;
  private Label wlPointerSuffix;
  private Label wlDerivatives;
  private TableView wDerivatives;
  private Button wAddDerivative;
  private Button wDeleteDerivative;

  private boolean ok;
  private boolean pit;

  public HopGuiBvTableDialog(
      Shell parent,
      IBvTable table,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    this.parent = parent;
    this.input = table;
    this.businessVaultModel = businessVaultModel;
    this.dataVaultModel = dataVaultModel;
    this.variables = variables;
    this.pit = table.getTableType() == BvTableType.PIT;
    this.originalTableIndex =
        businessVaultModel != null ? businessVaultModel.getTables().indexOf(table) : -1;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            "HopGuiBvTableDialog.Title",
            Const.NVL(input.getName(), input.getTableType().name())));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = 30;

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Name.Label"));
    PropsUi.setLook(wlName);
    FormData fdlName = new FormData();
    fdlName.left = new FormAttachment(0, 0);
    fdlName.top = new FormAttachment(0, margin);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.left = new FormAttachment(middle, 0);
    fdName.top = new FormAttachment(0, margin);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    Label wlTableName = new Label(shell, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    FormData fdlTableName = new FormData();
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.top = new FormAttachment(wName, margin);
    fdlTableName.right = new FormAttachment(middle, -margin);
    wlTableName.setLayoutData(fdlTableName);

    wTableName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTableName);
    FormData fdTableName = new FormData();
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.top = new FormAttachment(wName, margin);
    fdTableName.right = new FormAttachment(100, 0);
    wTableName.setLayoutData(fdTableName);

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    FormData fdlDescription = new FormData();
    fdlDescription.left = new FormAttachment(0, 0);
    fdlDescription.top = new FormAttachment(wTableName, margin);
    fdlDescription.right = new FormAttachment(middle, -margin);
    wlDescription.setLayoutData(fdlDescription);

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    FormData fdDescription = new FormData();
    fdDescription.left = new FormAttachment(middle, 0);
    fdDescription.top = new FormAttachment(wTableName, margin);
    fdDescription.right = new FormAttachment(100, 0);
    wDescription.setLayoutData(fdDescription);

    wlSnapshotDateField = new Label(shell, SWT.RIGHT);
    wlSnapshotDateField.setText(
        BaseMessages.getString(PKG, "HopGuiBvTableDialog.SnapshotDateField.Label"));
    PropsUi.setLook(wlSnapshotDateField);
    FormData fdlSnapshotDateField = new FormData();
    fdlSnapshotDateField.left = new FormAttachment(0, 0);
    fdlSnapshotDateField.top = new FormAttachment(wDescription, margin);
    fdlSnapshotDateField.right = new FormAttachment(middle, -margin);
    wlSnapshotDateField.setLayoutData(fdlSnapshotDateField);

    wSnapshotDateField = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSnapshotDateField);
    FormData fdSnapshotDateField = new FormData();
    fdSnapshotDateField.left = new FormAttachment(middle, 0);
    fdSnapshotDateField.top = new FormAttachment(wDescription, margin);
    fdSnapshotDateField.right = new FormAttachment(100, 0);
    wSnapshotDateField.setLayoutData(fdSnapshotDateField);

    org.eclipse.swt.widgets.Control derivativeTop = wDescription;

    if (pit) {
      wlCadence =
          addLabel(shell, "HopGuiBvTableDialog.Cadence.Label", wSnapshotDateField, middle, margin);
      wCadence = addEnumCombo(shell, BvPitCadence.class, wlCadence, middle);
      wlSnapshotAnchor =
          addLabel(shell, "HopGuiBvTableDialog.SnapshotAnchor.Label", wCadence, middle, margin);
      wSnapshotAnchor = addEnumCombo(shell, BvPitSnapshotAnchor.class, wlSnapshotAnchor, middle);
      wlHorizonDays =
          addLabel(shell, "HopGuiBvTableDialog.HorizonDays.Label", wSnapshotAnchor, middle, margin);
      wHorizonDays = addText(shell, wlHorizonDays, middle);
      wlRangeStart =
          addLabel(shell, "HopGuiBvTableDialog.RangeStart.Label", wHorizonDays, middle, margin);
      wRangeStart = addEnumCombo(shell, BvPitRangeStart.class, wlRangeStart, middle);
      wlRangeStartFixed =
          addLabel(shell, "HopGuiBvTableDialog.RangeStartFixed.Label", wRangeStart, middle, margin);
      wRangeStartFixed = addText(shell, wlRangeStartFixed, middle);
      wlRangeEnd =
          addLabel(shell, "HopGuiBvTableDialog.RangeEnd.Label", wRangeStartFixed, middle, margin);
      wRangeEnd = addEnumCombo(shell, BvPitRangeEnd.class, wlRangeEnd, middle);
      wlRangeEndFixed =
          addLabel(shell, "HopGuiBvTableDialog.RangeEndFixed.Label", wRangeEnd, middle, margin);
      wRangeEndFixed = addText(shell, wlRangeEndFixed, middle);
      wlPointerSuffix =
          addLabel(
              shell, "HopGuiBvTableDialog.PointerSuffix.Label", wRangeEndFixed, middle, margin);
      wPointerSuffix = addText(shell, wlPointerSuffix, middle);

      wRangeStart.addListener(SWT.Selection, e -> refreshFixedDateVisibility());
      wRangeEnd.addListener(SWT.Selection, e -> refreshFixedDateVisibility());
      derivativeTop = wPointerSuffix;
    }

    wlSnapshotDateField.setVisible(pit);
    wSnapshotDateField.setVisible(pit);

    wlDerivatives = new Label(shell, SWT.LEFT);
    wlDerivatives.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Label"));
    PropsUi.setLook(wlDerivatives);
    FormData fdlDerivatives = new FormData();
    fdlDerivatives.left = new FormAttachment(0, 0);
    fdlDerivatives.top = new FormAttachment(derivativeTop, 2 * margin);
    fdlDerivatives.right = new FormAttachment(100, 0);
    wlDerivatives.setLayoutData(fdlDerivatives);

    wAddDerivative = new Button(shell, SWT.PUSH);
    wAddDerivative.setText(BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Add"));
    PropsUi.setLook(wAddDerivative);
    FormData fdAddDerivative = new FormData();
    fdAddDerivative.left = new FormAttachment(0, 0);
    fdAddDerivative.top = new FormAttachment(wlDerivatives, margin);
    wAddDerivative.setLayoutData(fdAddDerivative);
    wAddDerivative.addListener(SWT.Selection, e -> addDerivativeRow());

    wDeleteDerivative = new Button(shell, SWT.PUSH);
    wDeleteDerivative.setText(
        BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Delete"));
    PropsUi.setLook(wDeleteDerivative);
    FormData fdDeleteDerivative = new FormData();
    fdDeleteDerivative.left = new FormAttachment(wAddDerivative, margin);
    fdDeleteDerivative.top = new FormAttachment(wlDerivatives, margin);
    wDeleteDerivative.setLayoutData(fdDeleteDerivative);
    wDeleteDerivative.addListener(SWT.Selection, e -> removeDerivativeRows());

    ColumnInfo[] derivativeCols =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Column.Name"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              getEligibleDvTableNames(),
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.Column.Type"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    derivativeCols[1].setReadOnly(true);

    wDerivatives =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            derivativeCols,
            1,
            null,
            PropsUi.getInstance());

    FormData fdDerivatives = new FormData();
    fdDerivatives.left = new FormAttachment(0, 0);
    fdDerivatives.top = new FormAttachment(wAddDerivative, margin);
    fdDerivatives.right = new FormAttachment(100, 0);
    fdDerivatives.bottom = new FormAttachment(100, -50);
    wDerivatives.setLayoutData(fdDerivatives);

    boolean dvAvailable = dataVaultModel != null && !dataVaultModel.getTables().isEmpty();
    wAddDerivative.setEnabled(dvAvailable);
    if (!dvAvailable) {
      wlDerivatives.setText(
          BaseMessages.getString(PKG, "HopGuiBvTableDialog.Derivatives.MissingDvModel"));
    }

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(
            ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> validate());
    Button wLineage = new Button(shell, SWT.PUSH);
    wLineage.setText(BaseMessages.getString(LineageTabSupport.class, "LineageTab.ShowButton"));
    wLineage.setToolTipText(
        BaseMessages.getString(LineageTabSupport.class, "LineageTab.ShowButton.ToolTip"));
    wLineage.addListener(SWT.Selection, e -> showLineage());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.BV_TABLE);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wLineage, wCancel}, margin, null);

    getData();
    if (pit) {
      refreshFixedDateVisibility();
    }
    BaseTransformDialog.setSize(shell, 560, pit ? 720 : 520);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private void showLineage() {
    TableLineage tableLineage = null;
    try {
      if (businessVaultModel != null) {
        LineageSnapshot snapshot = BvModelLineageCollector.collect(businessVaultModel, variables);
        tableLineage = LineageTabSupport.findTable(snapshot, input.getName());
      }
    } catch (Exception e) {
      new ErrorDialog(shell, "Error", "Error building lineage for table", e);
      return;
    }
    LineageTabSupport.openViewerDialog(shell, variables, tableLineage);
  }

  private Label addLabel(
      Shell parentShell,
      String messageKey,
      org.eclipse.swt.widgets.Control top,
      int middle,
      int margin) {
    Label label = new Label(parentShell, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, messageKey));
    PropsUi.setLook(label);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(top, margin);
    fd.right = new FormAttachment(middle, -margin);
    label.setLayoutData(fd);
    return label;
  }

  private Text addText(Shell parentShell, Label label, int middle) {
    Text text = new Text(parentShell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.top = new FormAttachment(label, 0, SWT.TOP);
    fd.right = new FormAttachment(100, 0);
    text.setLayoutData(fd);
    return text;
  }

  private <E extends Enum<E> & IEnumHasCodeAndDescription> Combo addEnumCombo(
      Shell parentShell, Class<E> enumType, Label label, int middle) {
    Combo combo = new Combo(parentShell, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(combo);
    EnumDialogSupport.populateCombo(combo, enumType);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.top = new FormAttachment(label, 0, SWT.TOP);
    fd.right = new FormAttachment(100, 0);
    combo.setLayoutData(fd);
    return combo;
  }

  private void refreshFixedDateVisibility() {
    BvPitRangeStart rangeStart =
        EnumDialogSupport.lookupText(wRangeStart.getText(), BvPitRangeStart.class, null);
    BvPitRangeEnd rangeEnd =
        EnumDialogSupport.lookupText(wRangeEnd.getText(), BvPitRangeEnd.class, null);
    boolean showStartFixed = rangeStart == BvPitRangeStart.FIXED_DATE;
    boolean showEndFixed = rangeEnd == BvPitRangeEnd.FIXED_DATE;
    wlRangeStartFixed.setVisible(showStartFixed);
    wRangeStartFixed.setVisible(showStartFixed);
    wlRangeEndFixed.setVisible(showEndFixed);
    wRangeEndFixed.setVisible(showEndFixed);
  }

  private String[] getEligibleDvTableNames() {
    List<String> names = new ArrayList<>();
    if (dataVaultModel == null) {
      return names.toArray(new String[0]);
    }
    for (IDvTable table : dataVaultModel.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || !BusinessVaultDerivativeSupport.isValidDerivativePair(
              input.getTableType(), table.getTableType())) {
        continue;
      }
      names.add(table.getName());
    }
    return names.toArray(new String[0]);
  }

  private void addDerivativeRow() {
    new TableItem(wDerivatives.table, SWT.NONE);
    wDerivatives.optimizeTableView();
  }

  private void removeDerivativeRows() {
    int idx = wDerivatives.getSelectionIndex();
    if (idx >= 0) {
      wDerivatives.table.remove(idx);
      wDerivatives.optimizeTableView();
    }
  }

  private void getData() {
    if (!Utils.isEmpty(input.getName())) {
      wName.setText(input.getName());
    }
    if (!Utils.isEmpty(input.getTableName())) {
      wTableName.setText(input.getTableName());
    }
    if (!Utils.isEmpty(input.getDescription())) {
      wDescription.setText(input.getDescription());
    }
    if (input instanceof BvPitTable pit) {
      if (!Utils.isEmpty(pit.getSnapshotDateField())) {
        wSnapshotDateField.setText(pit.getSnapshotDateField());
      }
      BvPitSnapshotSchedule schedule = pit.getSnapshotScheduleOrDefault();
      EnumDialogSupport.selectCombo(wCadence, schedule.getCadence());
      EnumDialogSupport.selectCombo(wSnapshotAnchor, schedule.getSnapshotAnchor());
      wHorizonDays.setText(Integer.toString(schedule.getHorizonDays()));
      EnumDialogSupport.selectCombo(wRangeStart, schedule.getRangeStart());
      if (!Utils.isEmpty(schedule.getRangeStartFixed())) {
        wRangeStartFixed.setText(schedule.getRangeStartFixed());
      }
      EnumDialogSupport.selectCombo(wRangeEnd, schedule.getRangeEnd());
      if (!Utils.isEmpty(schedule.getRangeEndFixed())) {
        wRangeEndFixed.setText(schedule.getRangeEndFixed());
      }
      if (!Utils.isEmpty(schedule.getSatellitePointerSuffix())) {
        wPointerSuffix.setText(schedule.getSatellitePointerSuffix());
      }
    }

    wDerivatives.clearAll();
    for (BvDerivativeRef derivative : input.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      TableItem item = new TableItem(wDerivatives.table, SWT.NONE);
      item.setText(1, derivative.getDvTableName());
      if (derivative.getDvTableType() != null) {
        item.setText(2, derivative.getDvTableType().getDescription());
      }
    }
    wDerivatives.optimizeTableView();
  }

  private void ok() {
    applyWidgetsToTable(input);
    ok = true;
    dispose();
  }

  private void validate() {
    try {
      BusinessVaultModel draft =
          ModelDialogValidationSupport.cloneBusinessVaultModel(
              businessVaultModel, HopGui.getInstance().getMetadataProvider());
      IBvTable draftTable = locateDraftTable(draft);
      applyWidgetsToTable(draftTable);
      List<ICheckResult> remarks =
          draft.check(HopGui.getInstance().getMetadataProvider(), variables);
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

  private void applyWidgetsToTable(IBvTable target) {
    target.setName(wName.getText());
    target.setTableName(wTableName.getText());
    target.setDescription(wDescription.getText());
    if (target instanceof BvPitTable pit) {
      pit.setSnapshotDateField(wSnapshotDateField.getText());
      BvPitSnapshotSchedule schedule = pit.getSnapshotScheduleOrDefault();
      schedule.setCadence(
          EnumDialogSupport.readCombo(wCadence, BvPitCadence.class, BvPitCadence.DAILY));
      schedule.setSnapshotAnchor(
          EnumDialogSupport.readCombo(
              wSnapshotAnchor, BvPitSnapshotAnchor.class, BvPitSnapshotAnchor.END_OF_PERIOD));
      schedule.setHorizonDays(parseHorizonDays(wHorizonDays.getText()));
      schedule.setRangeStart(
          EnumDialogSupport.readCombo(
              wRangeStart,
              BvPitRangeStart.class,
              BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD));
      schedule.setRangeStartFixed(wRangeStartFixed.getText());
      schedule.setRangeEnd(
          EnumDialogSupport.readCombo(
              wRangeEnd, BvPitRangeEnd.class, BvPitRangeEnd.NOW_MINUS_HORIZON));
      schedule.setRangeEndFixed(wRangeEndFixed.getText());
      schedule.setSatellitePointerSuffix(wPointerSuffix.getText());
    }

    target.getDerivatives().clear();
    for (TableItem item : wDerivatives.getNonEmptyItems()) {
      String dvName = item.getText(1);
      if (Utils.isEmpty(dvName)) {
        continue;
      }
      DvTableType dvType = null;
      if (dataVaultModel != null) {
        IDvTable dvTable = dataVaultModel.findTable(dvName);
        if (dvTable != null) {
          dvType = dvTable.getTableType();
        }
      }
      if (dvType == null && !Utils.isEmpty(item.getText(2))) {
        dvType = DvTableType.lookupDescription(item.getText(2));
        if (dvType == null) {
          dvType = DvTableType.lookupCode(item.getText(2));
        }
      }
      if (dvType != null
          && BusinessVaultDerivativeSupport.isValidDerivativePair(target.getTableType(), dvType)
          && !BusinessVaultDerivativeSupport.hasDerivative(target, dvName)) {
        target.getDerivatives().add(new BvDerivativeRef(dvName, dvType));
      }
    }
  }

  private static int parseHorizonDays(String text) {
    if (Utils.isEmpty(text)) {
      return BvPitSnapshotSchedule.DEFAULT_HORIZON_DAYS;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      return BvPitSnapshotSchedule.DEFAULT_HORIZON_DAYS;
    }
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      WindowProperty winProp = new WindowProperty(shell);
      PropsUi.getInstance().setSessionScreen(winProp);
      shell.dispose();
    }
  }
}
