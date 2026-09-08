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
package org.hopper.edw.datavault.hopgui.metrics;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.datavault.hopgui.dialog.ShowRowsDialog;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.hopgui.widget.MarkdownStyledTextComp;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewHistoryLoader;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewHistoryLoader.HistoryList;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewHistoryLoader.OverviewHistoryRow;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewReport;
import org.hopper.edw.datavault.metrics.WorkflowLoadOverviewReportFormatter;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveMonitor;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveRegistry;
import org.hopper.edw.datavault.metrics.live.UpdateRunLiveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveModelProgress;
import org.hopper.edw.datavault.metrics.live.UpdateRunWavePhase;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshot;
import org.hopper.edw.datavault.metrics.live.UpdateRunWaveSnapshotSupport;

/** Live wave progress plus OPS load-overview history for Update resource definition group. */
public final class ResourceGroupUpdateMetricsDialog {

  private static final Class<?> PKG = UpdateRunLiveAnalysisDialog.class;
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private final Shell parent;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final String waveId;
  private final String workflowFilename;
  private final String actionName;
  private final boolean historyPreferred;

  private Shell shell;
  private CTabFolder tabFolder;
  private Label thisRunHeader;
  private TableView thisRunTable;
  private MarkdownStyledTextComp thisRunDetails;
  private Label historyMessage;
  private TableView historyTable;
  private UpdateRunWaveSnapshot currentWave;
  private HistoryList historyList;
  private boolean autoRefreshScheduled;

  private ResourceGroupUpdateMetricsDialog(
      Shell parent,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String waveId,
      String workflowFilename,
      String actionName,
      boolean historyPreferred) {
    this.parent = parent;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.waveId = waveId;
    this.workflowFilename = workflowFilename;
    this.actionName = actionName;
    this.historyPreferred = historyPreferred;
  }

  public static void open(
      Shell parent,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      UpdateRunLiveAreaOwnerData data) {
    if (data == null) {
      return;
    }
    open(
        parent,
        variables,
        metadataProvider,
        data.getWaveId(),
        data.getWorkflowFilename(),
        data.getActionName(),
        data.isHistoryPreferred());
  }

  public static void open(
      Shell parent,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String waveId,
      String workflowFilename,
      String actionName,
      boolean historyPreferred) {
    if (parent == null || parent.isDisposed()) {
      return;
    }
    new ResourceGroupUpdateMetricsDialog(
            parent,
            variables,
            metadataProvider,
            waveId,
            workflowFilename,
            actionName,
            historyPreferred)
        .openDialog();
  }

  private void openDialog() {
    currentWave = resolveWave().orElse(null);

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    shell.setImage(GuiResource.getInstance().getImageHop());
    shell.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Title"));
    PropsUi.setLook(shell);
    shell.setLayout(new FormLayout());
    shell.addListener(SWT.Close, event -> close());

    int margin = PropsUi.getMargin();

    Button wRefresh = new Button(shell, SWT.PUSH);
    wRefresh.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Refresh"));
    PropsUi.setLook(wRefresh);
    wRefresh.addListener(SWT.Selection, event -> refreshAll());

    Button wCopy = new Button(shell, SWT.PUSH);
    wCopy.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.CopyDiagnostics"));
    PropsUi.setLook(wCopy);
    wCopy.addListener(SWT.Selection, event -> copyDiagnostics());

    Button wTransforms = new Button(shell, SWT.PUSH);
    wTransforms.setText(BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.ViewTransforms"));
    PropsUi.setLook(wTransforms);
    wTransforms.addListener(SWT.Selection, event -> showTransforms());

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    PropsUi.setLook(wClose);
    wClose.addListener(SWT.Selection, event -> close());

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wRefresh, wCopy, wTransforms, wClose}, margin, null);
    DialogHelpSupport.installLocalHelpButton(shell, HelpTopics.RESOURCE_GROUP_UPDATE_METRICS);

    tabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(tabFolder);
    FormData fdTabs = new FormData();
    fdTabs.left = new FormAttachment(0, margin);
    fdTabs.top = new FormAttachment(0, margin);
    fdTabs.right = new FormAttachment(100, -margin);
    fdTabs.bottom = new FormAttachment(wRefresh, -margin);
    tabFolder.setLayoutData(fdTabs);

    createThisRunTab();
    createHistoryTab();

    boolean showHistory =
        historyPreferred
            || currentWave == null
            || currentWave.getPhase() == UpdateRunWavePhase.FINISHED;
    tabFolder.setSelection(showHistory ? 1 : 0);

    populateThisRun();
    populateHistory();
    scheduleAutoRefresh();

    BaseTransformDialog.setSize(shell, 900, 640);
    shell.open();
    Display display = parent.getDisplay();
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

  private void createThisRunTab() {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Tab.ThisRun"));

    Composite composite = new Composite(tabFolder, SWT.NONE);
    composite.setLayout(new FormLayout());
    PropsUi.setLook(composite);
    tab.setControl(composite);

    int margin = PropsUi.getMargin();
    thisRunHeader = new Label(composite, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(thisRunHeader);
    FormData fdHeader = new FormData();
    fdHeader.left = new FormAttachment(0, margin);
    fdHeader.top = new FormAttachment(0, margin);
    fdHeader.right = new FormAttachment(100, -margin);
    thisRunHeader.setLayoutData(fdHeader);

    SashForm sash = new SashForm(composite, SWT.VERTICAL);
    FormData fdSash = new FormData();
    fdSash.left = new FormAttachment(0, margin);
    fdSash.top = new FormAttachment(thisRunHeader, margin);
    fdSash.right = new FormAttachment(100, -margin);
    fdSash.bottom = new FormAttachment(100, -margin);
    sash.setLayoutData(fdSash);

    thisRunTable = buildThisRunTable(sash);
    thisRunTable.addListener(SWT.Selection, event -> updateThisRunDetails());
    thisRunTable.table.addListener(SWT.Selection, event -> updateThisRunDetails());

    thisRunDetails = new MarkdownStyledTextComp(sash, SWT.NONE);
    sash.setWeights(new int[] {40, 60});
  }

  private TableView buildThisRunTable(Composite parent) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          textColumn("ResourceGroupUpdateMetrics.Column.Layer"),
          textColumn("ResourceGroupUpdateMetrics.Column.Model"),
          textColumn("ResourceGroupUpdateMetrics.Column.State"),
          textColumn("ResourceGroupUpdateMetrics.Column.Table"),
          textColumn("ResourceGroupUpdateMetrics.Column.Elapsed"),
          textColumn("ResourceGroupUpdateMetrics.Column.RowsIn", true),
          textColumn("ResourceGroupUpdateMetrics.Column.RowsOut", true),
          textColumn("ResourceGroupUpdateMetrics.Column.Errors", true),
        };
    TableView view =
        new TableView(
            variables,
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE,
            columns,
            1,
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    view.setSortable(true);
    return view;
  }

  private void createHistoryTab() {
    CTabItem tab = new CTabItem(tabFolder, SWT.NONE);
    tab.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Tab.History"));

    Composite composite = new Composite(tabFolder, SWT.NONE);
    composite.setLayout(new FormLayout());
    PropsUi.setLook(composite);
    tab.setControl(composite);

    int margin = PropsUi.getMargin();
    historyMessage = new Label(composite, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(historyMessage);
    FormData fdMessage = new FormData();
    fdMessage.left = new FormAttachment(0, margin);
    fdMessage.top = new FormAttachment(0, margin);
    fdMessage.right = new FormAttachment(100, -margin);
    historyMessage.setLayoutData(fdMessage);

    historyTable = buildHistoryTable(composite, historyMessage);
    historyTable.addListener(SWT.DefaultSelection, event -> openHistoryDetail());
    historyTable.table.addListener(SWT.MouseDoubleClick, event -> openHistoryDetail());
  }

  private TableView buildHistoryTable(Composite parent, Label top) {
    ColumnInfo[] columns =
        new ColumnInfo[] {
          textColumn("ResourceGroupUpdateMetrics.History.Column.Finished"),
          textColumn("ResourceGroupUpdateMetrics.History.Column.Duration"),
          textColumn("ResourceGroupUpdateMetrics.History.Column.Models", true),
          textColumn("ResourceGroupUpdateMetrics.History.Column.RowsIn", true),
          textColumn("ResourceGroupUpdateMetrics.History.Column.RowsOut", true),
          textColumn("ResourceGroupUpdateMetrics.History.Column.Errors", true),
          textColumn("ResourceGroupUpdateMetrics.History.Column.Success"),
          textColumn("ResourceGroupUpdateMetrics.History.Column.Workflow"),
        };
    TableView view =
        new TableView(
            variables,
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE,
            columns,
            1,
            null,
            PropsUi.getInstance());
    view.setReadonly(true);
    view.setSortable(true);
    FormData fdTable = new FormData();
    fdTable.left = new FormAttachment(0, PropsUi.getMargin());
    fdTable.top = new FormAttachment(top, PropsUi.getMargin());
    fdTable.right = new FormAttachment(100, -PropsUi.getMargin());
    fdTable.bottom = new FormAttachment(100, -PropsUi.getMargin());
    view.setLayoutData(fdTable);
    return view;
  }

  private ColumnInfo textColumn(String key) {
    return textColumn(key, false);
  }

  private ColumnInfo textColumn(String key, boolean numeric) {
    ColumnInfo column =
        new ColumnInfo(BaseMessages.getString(PKG, key), ColumnInfo.COLUMN_TYPE_TEXT, numeric);
    column.setReadOnly(true);
    return column;
  }

  private void refreshAll() {
    currentWave = resolveWave().orElse(null);
    populateThisRun();
    populateHistory();
    scheduleAutoRefresh();
  }

  private void populateThisRun() {
    if (thisRunHeader == null || thisRunHeader.isDisposed()) {
      return;
    }
    if (currentWave == null) {
      thisRunHeader.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.ThisRun.None"));
      thisRunTable.clearAll(false);
      thisRunTable.add(new String[] {"", "", "", "", "", "", "", ""});
      thisRunTable.removeEmptyRows();
      thisRunTable.setRowNums();
      thisRunDetails.setMarkdown("");
      return;
    }
    thisRunHeader.setText(
        BaseMessages.getString(
            PKG,
            "ResourceGroupUpdateMetrics.ThisRun.Header",
            Const.NVL(currentWave.getResourceGroupName(), ""),
            currentWave.getOverallState() != null ? currentWave.getOverallState().name() : "",
            currentWave.getModelsCompleted() + "/" + currentWave.getModelsTotal(),
            UpdateRunWaveSnapshotSupport.formatDuration(currentWave)));
    thisRunTable.clearAll(false);
    List<UpdateRunWaveModelProgress> models = currentWave.getModels();
    if (models != null) {
      for (UpdateRunWaveModelProgress model : models) {
        if (model == null) {
          continue;
        }
        TableItem item = new TableItem(thisRunTable.table, SWT.NONE);
        item.setText(1, UpdateRunWaveDiagnosticsFormatter.formatLayer(model.getLayer()));
        item.setText(2, UpdateRunWaveSnapshotSupport.displayName(model));
        item.setText(3, model.getState() != null ? model.getState().name() : "");
        item.setText(4, Const.NVL(model.getCurrentElementName(), ""));
        item.setText(5, UpdateRunWaveDiagnosticsFormatter.formatElapsed(model));
        item.setText(6, Long.toString(model.getSourceRowsRead()));
        item.setText(7, Long.toString(model.getTargetRowsInserted()));
        item.setText(8, Long.toString(model.getErrors()));
      }
    }
    thisRunTable.removeEmptyRows();
    thisRunTable.setRowNums();
    thisRunTable.optWidth(true);
    selectCurrentModelRow();
    updateThisRunDetails();
  }

  private void selectCurrentModelRow() {
    if (currentWave == null || currentWave.getModels() == null) {
      return;
    }
    UpdateRunWaveModelProgress current =
        UpdateRunWaveSnapshotSupport.currentModel(currentWave.getModels());
    if (current == null) {
      return;
    }
    int index = currentWave.getModels().indexOf(current);
    if (index >= 0 && index < thisRunTable.nrNonEmpty()) {
      thisRunTable.setSelection(new int[] {index});
    }
  }

  private void updateThisRunDetails() {
    if (thisRunDetails == null || thisRunDetails.isDisposed()) {
      return;
    }
    if (currentWave == null) {
      thisRunDetails.setMarkdown("");
      return;
    }
    int selected = thisRunTable.getSelectionIndex();
    UpdateRunWaveModelProgress model = modelAt(selected);
    UpdateRunWaveModelProgress current =
        UpdateRunWaveSnapshotSupport.currentModel(currentWave.getModels());
    if (model != null && (current == null || model != current)) {
      thisRunDetails.setMarkdown(UpdateRunWaveDiagnosticsFormatter.formatModelMarkdown(model));
    } else {
      thisRunDetails.setMarkdown(UpdateRunWaveDiagnosticsFormatter.formatMarkdown(currentWave));
    }
  }

  private UpdateRunWaveModelProgress modelAt(int tableIndex) {
    if (currentWave == null || currentWave.getModels() == null || tableIndex < 0) {
      return null;
    }
    int modelIndex = tableIndex;
    if (modelIndex >= 0 && modelIndex < currentWave.getModels().size()) {
      return currentWave.getModels().get(modelIndex);
    }
    return null;
  }

  private void populateHistory() {
    if (historyTable == null || historyTable.isDisposed()) {
      return;
    }
    String workflowName = resolveWorkflowName();
    historyList =
        WorkflowLoadOverviewHistoryLoader.listRecent(metadataProvider, variables, workflowName);
    if (!Utils.isEmpty(historyList.emptyReason())) {
      historyMessage.setText(historyEmptyMessage(historyList.emptyReason()));
    } else if (historyList.workflowFilterMissed()) {
      historyMessage.setText(
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.Unfiltered"));
    } else {
      historyMessage.setText(
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.Message"));
    }
    historyTable.clearAll(false);
    for (OverviewHistoryRow row : historyList.rows()) {
      TableItem item = new TableItem(historyTable.table, SWT.NONE);
      item.setText(1, formatDate(row.finishedAt()));
      item.setText(2, WorkflowLoadOverviewReportFormatter.formatDuration(nvl(row.durationMs())));
      item.setText(3, longText(row.modelCount()));
      item.setText(4, longText(row.totalSourceRowsRead()));
      item.setText(5, longText(row.totalTargetRowsInserted()));
      item.setText(6, longText(row.totalErrors()));
      item.setText(
          7,
          Boolean.FALSE.equals(row.success())
              ? BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.Failed")
              : BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.Success"));
      item.setText(8, Const.NVL(row.rootWorkflowName(), ""));
    }
    historyTable.removeEmptyRows();
    historyTable.setRowNums();
    historyTable.optWidth(true);
  }

  private void openHistoryDetail() {
    int index = historyTable.getSelectionIndex();
    if (historyList == null || index < 0 || index >= historyList.rows().size()) {
      return;
    }
    OverviewHistoryRow row = historyList.rows().get(index);
    WorkflowLoadOverviewReport report =
        WorkflowLoadOverviewHistoryLoader.loadDetail(metadataProvider, variables, row);
    if (report == null) {
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.DetailMissing"));
      box.open();
      return;
    }
    String markdown = WorkflowLoadOverviewReportFormatter.formatMarkdown(report, true, true);
    Shell detail = new Shell(shell, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    detail.setImage(GuiResource.getInstance().getImageHop());
    detail.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.DetailTitle"));
    PropsUi.setLook(detail);
    detail.setLayout(new FormLayout());
    int margin = PropsUi.getMargin();
    Button wClose = new Button(detail, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    PropsUi.setLook(wClose);
    wClose.addListener(SWT.Selection, event -> detail.dispose());
    BaseTransformDialog.positionBottomButtons(detail, new Button[] {wClose}, margin, null);
    MarkdownStyledTextComp viewer = new MarkdownStyledTextComp(detail, SWT.NONE);
    viewer.setMarkdown(markdown);
    FormData fdViewer = new FormData();
    fdViewer.left = new FormAttachment(0, margin);
    fdViewer.top = new FormAttachment(0, margin);
    fdViewer.right = new FormAttachment(100, -margin);
    fdViewer.bottom = new FormAttachment(wClose, -margin);
    viewer.setLayoutData(fdViewer);
    BaseTransformDialog.setSize(detail, 800, 560);
    detail.open();
  }

  private void copyDiagnostics() {
    String text =
        currentWave != null ? UpdateRunWaveDiagnosticsFormatter.formatMarkdown(currentWave) : "";
    if (Utils.isEmpty(text)) {
      return;
    }
    Clipboard clipboard = new Clipboard(shell.getDisplay());
    clipboard.setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
    clipboard.dispose();
  }

  private void showTransforms() {
    UpdateRunLiveSnapshot live = currentWave != null ? currentWave.getCurrentLiveSnapshot() : null;
    if (live == null) {
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Title"));
      box.setMessage(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Transforms.None"));
      box.open();
      return;
    }
    try {
      IRowMeta rowMeta = UpdateRunLiveAnalysisDialog.buildTransformRowMeta();
      List<Object[]> rows = UpdateRunLiveAnalysisDialog.buildTransformRows(live, rowMeta);
      new ShowRowsDialog(
              shell,
              variables,
              BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Transforms.Title"),
              BaseMessages.getString(PKG, "UpdateRunLiveAnalysisDialog.Transforms.Message"),
              rowMeta,
              rows)
          .open();
    } catch (HopException e) {
      MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.Title"));
      box.setMessage(Const.getStackTracker(e));
      box.open();
    }
  }

  private void scheduleAutoRefresh() {
    if (autoRefreshScheduled || shell == null || shell.isDisposed()) {
      return;
    }
    if (currentWave == null || currentWave.getPhase() == UpdateRunWavePhase.FINISHED) {
      return;
    }
    autoRefreshScheduled = true;
    Display display = shell.getDisplay();
    display.timerExec(
        (int) Math.max(1_000L, UpdateRunLiveMonitor.pollIntervalMs()),
        () -> {
          autoRefreshScheduled = false;
          if (shell == null || shell.isDisposed()) {
            return;
          }
          currentWave = resolveWave().orElse(null);
          populateThisRun();
          scheduleAutoRefresh();
        });
  }

  private Optional<UpdateRunWaveSnapshot> resolveWave() {
    if (!Utils.isEmpty(waveId)) {
      Optional<UpdateRunWaveSnapshot> byId = UpdateRunLiveRegistry.findWaveById(waveId);
      if (byId.isPresent()) {
        return byId;
      }
    }
    Optional<UpdateRunWaveSnapshot> byFile =
        UpdateRunLiveRegistry.findWaveByWorkflowAction(workflowFilename, actionName);
    if (byFile.isPresent()) {
      return byFile;
    }
    return UpdateRunLiveRegistry.findWaveByWorkflowAction(resolveWorkflowName(), actionName);
  }

  private String resolveWorkflowName() {
    if (currentWave != null && !Utils.isEmpty(currentWave.getWorkflowName())) {
      return currentWave.getWorkflowName();
    }
    return workflowFilename;
  }

  private String historyEmptyMessage(String reason) {
    return switch (Const.NVL(reason, "")) {
      case "not-configured" ->
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.NotConfigured");
      case "tables-missing" ->
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.TablesMissing");
      case "load-failed" ->
          BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.LoadFailed");
      default -> BaseMessages.getString(PKG, "ResourceGroupUpdateMetrics.History.Empty");
    };
  }

  private static String formatDate(java.util.Date date) {
    return date != null ? DATE_FORMAT.format(date) : "";
  }

  private static String longText(Long value) {
    return value != null ? Long.toString(value) : "";
  }

  private static long nvl(Long value) {
    return value != null ? value : 0L;
  }

  private void close() {
    if (shell == null || shell.isDisposed()) {
      return;
    }
    PropsUi.getInstance().setScreen(new WindowProperty(shell));
    shell.dispose();
  }
}
