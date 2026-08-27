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
package org.hopper.edw.catalog.hopgui.perspective;

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.quality.history.DataQualityHistoryReader.FindingEntry;

/** Read-only TableView of findings for one quality run. */
public final class QualityHistoryFindingsDialog {

  private static final Class<?> PKG = RecordDefinitionDetailsPanel.class;

  private final Shell parentShell;
  private final IVariables variables;
  private final String qualityRunId;
  private final List<FindingEntry> findings;

  private Shell shell;

  public QualityHistoryFindingsDialog(
      Shell parent, IVariables variables, String qualityRunId, List<FindingEntry> findings) {
    this.parentShell = parent;
    this.variables = variables;
    this.qualityRunId = qualityRunId;
    this.findings = findings != null ? findings : List.of();
  }

  public void open() {
    if (parentShell == null || parentShell.isDisposed()) {
      return;
    }

    shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    shell.setImage(GuiResource.getInstance().getImageHopUi());
    shell.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Findings.Title"));
    shell.setLayout(new FormLayout());
    shell.addListener(SWT.Close, event -> saveWindowProperty());

    int margin = PropsUi.getMargin();

    Label messageLabel = new Label(shell, SWT.LEFT | SWT.WRAP);
    messageLabel.setText(
        BaseMessages.getString(
            PKG,
            "RecordDefinitionDetailsPanel.Quality.History.Findings.Message",
            Const.NVL(qualityRunId, "")));
    PropsUi.setLook(messageLabel);
    FormData fdMessage = new FormData();
    fdMessage.left = new FormAttachment(0, 0);
    fdMessage.right = new FormAttachment(100, 0);
    fdMessage.top = new FormAttachment(0, margin);
    messageLabel.setLayoutData(fdMessage);

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(
        BaseMessages.getString(PKG, "RecordDefinitionDetailsPanel.Quality.History.Close"));
    PropsUi.setLook(wClose);
    wClose.addListener(SWT.Selection, e -> close());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wClose}, margin, null);

    TableView tableView = buildTableView(margin, messageLabel, wClose);
    populateRows(tableView);

    BaseTransformDialog.setSize(shell);
    shell.open();

    Display display = parentShell.getDisplay();
    while (shell != null && !shell.isDisposed()) {
      if (display == null || display.isDisposed()) {
        break;
      }
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

  private TableView buildTableView(int margin, Label messageLabel, Button wClose) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          col("RecordDefinitionDetailsPanel.Quality.History.Findings.Column.Seq", true),
          col("RecordDefinitionDetailsPanel.Quality.History.Findings.Column.Severity", false),
          col("RecordDefinitionDetailsPanel.Quality.History.Findings.Column.Type", false),
          col("RecordDefinitionDetailsPanel.Quality.History.Findings.Column.Field", false),
          col("RecordDefinitionDetailsPanel.Quality.History.Findings.Column.Rule", false),
          col("RecordDefinitionDetailsPanel.Quality.History.Findings.Column.Message", false),
          col("RecordDefinitionDetailsPanel.Quality.History.Findings.Column.Actual", false),
          col("RecordDefinitionDetailsPanel.Quality.History.Findings.Column.Expected", false),
        };

    TableView view =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            Math.max(1, findings.size()),
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    view.setSortable(true);

    FormData fdTable = new FormData();
    fdTable.left = new FormAttachment(0, 0);
    fdTable.top = new FormAttachment(messageLabel, margin);
    fdTable.right = new FormAttachment(100, 0);
    fdTable.bottom = new FormAttachment(wClose, -margin);
    view.setLayoutData(fdTable);
    return view;
  }

  private static ColumnInfo col(String key, boolean numeric) {
    ColumnInfo column =
        new ColumnInfo(BaseMessages.getString(PKG, key), ColumnInfo.COLUMN_TYPE_TEXT, numeric);
    column.setReadOnly(true);
    return column;
  }

  private void populateRows(TableView tableView) {
    tableView.clearAll(false);
    for (int i = 0; i < findings.size(); i++) {
      FindingEntry finding = findings.get(i);
      TableItem item;
      if (i == 0 && tableView.table.getItemCount() > 0) {
        item = tableView.table.getItem(0);
      } else {
        item = new TableItem(tableView.table, SWT.NONE);
      }
      item.setText(1, Long.toString(finding.findingSeq()));
      item.setText(2, Const.NVL(finding.severity(), ""));
      item.setText(3, Const.NVL(finding.ruleType(), ""));
      item.setText(4, Const.NVL(finding.fieldName(), ""));
      item.setText(5, Const.NVL(finding.ruleName(), Const.NVL(finding.ruleId(), "")));
      item.setText(6, Const.NVL(finding.message(), ""));
      item.setText(7, Const.NVL(finding.actualSummary(), ""));
      item.setText(8, Const.NVL(finding.expectedSummary(), ""));
    }
    tableView.optimizeTableView();
  }

  private void close() {
    saveWindowProperty();
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }

  private void saveWindowProperty() {
    if (shell == null || shell.isDisposed()) {
      return;
    }
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
  }
}
