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
package org.hopper.edw.datavault.hopgui.busmatrix;

import java.text.SimpleDateFormat;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.datavault.hopgui.file.metrics.LoadRunDurationTableSupport;
import org.hopper.edw.datavault.metrics.LoadRunDurationMetricsLoader;
import org.hopper.edw.datavault.metrics.LoadRunDurationRun;
import org.hopper.edw.datavault.metrics.LoadRunDurationSnapshot;

/** Displays operational execution load run metrics for a bus matrix fact or dimension table. */
public class BusMatrixOperationalReportDialog {

  private static final Class<?> PKG = BusMatrixLaunchSupport.class;
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private final Shell parent;
  private final String tableName;
  private final String modelName;
  private final String modelFilename;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  public BusMatrixOperationalReportDialog(
      Shell parent,
      String tableName,
      String modelName,
      String modelFilename,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.tableName = Const.NVL(tableName, "");
    this.modelName = Const.NVL(modelName, "");
    this.modelFilename = Const.NVL(modelFilename, "");
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public void open() {
    Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    shell.setImage(GuiResource.getInstance().getImageHopUi());
    shell.setText(
        BaseMessages.getString(
            PKG, "BusMatrixOperationalReportDialog.Title", tableName, modelName));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wClose}, margin, null);

    Label wlHeader = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlHeader);
    wlHeader.setText(
        BaseMessages.getString(
            PKG, "BusMatrixOperationalReportDialog.Header", tableName, modelName));
    FormData fdHeader = new FormData();
    fdHeader.left = new FormAttachment(0, margin);
    fdHeader.top = new FormAttachment(0, margin);
    fdHeader.right = new FormAttachment(100, -margin);
    wlHeader.setLayoutData(fdHeader);

    Label wlSummary = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlSummary);
    FormData fdSummary = new FormData();
    fdSummary.left = new FormAttachment(0, margin);
    fdSummary.top = new FormAttachment(wlHeader, margin / 2);
    fdSummary.right = new FormAttachment(100, -margin);
    wlSummary.setLayoutData(fdSummary);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "BusMatrixOperationalReportDialog.Column.RunId"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "BusMatrixOperationalReportDialog.Column.StartedAt"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "BusMatrixOperationalReportDialog.Column.FinishedAt"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "BusMatrixOperationalReportDialog.Column.Duration"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "BusMatrixOperationalReportDialog.Column.Status"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true)
        };

    TableView tableView =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            0,
            null,
            PropsUi.getInstance());
    PropsUi.setLook(tableView);
    FormData fdTable = new FormData();
    fdTable.left = new FormAttachment(0, margin);
    fdTable.top = new FormAttachment(wlSummary, margin);
    fdTable.right = new FormAttachment(100, -margin);
    fdTable.bottom = new FormAttachment(wClose, -margin);
    tableView.setLayoutData(fdTable);

    // Load data
    LoadRunDurationSnapshot snapshot =
        LoadRunDurationMetricsLoader.load(
            modelName, "dm", List.of(tableName), metadataProvider, variables);

    LoadRunDurationTableSupport.Model model = LoadRunDurationTableSupport.build(snapshot);
    if (snapshot == null || snapshot.getStatus() != LoadRunDurationSnapshot.Status.LOADED) {
      String msg = model.statusMessage();
      wlSummary.setText(
          BaseMessages.getString(PKG, "BusMatrixOperationalReportDialog.NoData", msg));
    } else {
      List<LoadRunDurationRun> runs = snapshot.getRuns();
      long totalDuration = 0;
      int runCount = runs.size();
      for (int i = 0; i < runCount; i++) {
        LoadRunDurationRun run = runs.get(i);
        long durationMs = snapshot.durationMs(tableName, i);
        totalDuration += durationMs;

        TableItem item = new TableItem(tableView.table, SWT.NONE);
        item.setText(1, Const.NVL(run.getRunId(), ""));
        item.setText(2, run.getStartedAt() != null ? DATE_FORMAT.format(run.getStartedAt()) : "");
        item.setText(3, run.getFinishedAt() != null ? DATE_FORMAT.format(run.getFinishedAt()) : "");
        item.setText(4, formatDuration(durationMs));
        item.setText(5, run.isSuccess() ? "SUCCESS" : "FAILED");
      }
      tableView.optimizeTableView();

      long avgMs = runCount > 0 ? totalDuration / runCount : 0;
      String latestStatus = runCount > 0 && runs.get(0).isSuccess() ? "SUCCESS" : "FAILED";
      wlSummary.setText(
          BaseMessages.getString(
              PKG,
              "BusMatrixOperationalReportDialog.Summary",
              Integer.toString(runCount),
              formatDuration(avgMs),
              latestStatus));
    }

    shell.setSize(750, 480);
    BaseDialog.defaultShellHandling(shell, c -> shell.dispose(), c -> shell.dispose());
  }

  private static String formatDuration(long durationMs) {
    if (durationMs < 1000) {
      return durationMs + " ms";
    }
    return String.format("%.2f s", durationMs / 1000.0);
  }
}
